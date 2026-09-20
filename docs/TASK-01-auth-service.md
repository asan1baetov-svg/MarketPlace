# Задание №1 — auth-service + валидация JWT в api-gateway

> **Статус: выполнено (2026-09-05).** Доменный/сервисный слой и веб-слой (контроллеры, security,
> JWKS, проверка JWT в api-gateway) реализованы и вручную провалидированы end-to-end (регистрация →
> OTP → логин → ротация/повторное использование refresh → logout, SSO из mock-mlm + защита от
> replay, internal-эндпоинты, gateway 401/permitAll). Юнит- и Testcontainers-тесты добавлены.
> Подробности — `services/auth-service/README.md`, `docs/http/auth.http`. Документ ниже остаётся
> как справочная спецификация контракта.

**Кому:** backend-коллеге
**Ветка:** `feature/auth-service`
**Оценка:** ~5–7 рабочих дней
**Предусловие:** скелет монорепозитория уже в `main` (`./mvnw compile` — зелёный),
модуль `services/auth-service` создан и пустой (только `AuthApplication`, `application.yml`,
`V001__baseline.sql`).

Общий контекст — `docs/ARCHITECTURE.md` (разделы 2.1, 3.1, 5). Этот документ — детализация
до уровня «сел и сделал». Если по ходу видишь, что контракт лучше поменять — пиши в комментарии
к MR, не молчи.

---

## 1. Что делаем и зачем

`auth-service` — единственный источник истины по учётным записям, ролям и токенам. Все
остальные сервисы ему доверяют и сами пользователей не заводят. В этом задании — три блока:

1. **Локальная регистрация и вход** внешнего клиента (email/телефон + пароль, OTP-подтверждение).
2. **Выпуск токенов** через Spring Authorization Server: access-JWT (RS256) + refresh с ротацией,
   эндпоинт JWKS для остальных сервисов.
3. **SSO-вход из внешней MLM-системы**: принять подписанный токен от MLM-бэка, создать/найти
   пользователя, выдать наш JWT. Реальный контракт с MLM ещё не согласован — делаем через
   антикоррупционный интерфейс и `mock-mlm` из `docker-compose.infra.yml`.

Плюс — включить проверку JWT в `api-gateway` и проброс identity вниз.

**Не в этом задании** (не трогай): реферальное дерево и статусы активации MLM (это `mlm-service`),
модерация магазинов/курьеров (catalog/courier дёрнут наш internal API позже), соц-логин,
Telegram-вход, реальная отправка SMS (заглушка + лог).

---

## 2. Схема БД (Flyway `V002__auth.sql`)

`V001__baseline.sql` уже создаёт `processed_events` и `outbox` — их не трогай. Добавь миграцию
`src/main/resources/db/migration/V002__auth.sql`:

```sql
create table users (
    id            uuid primary key default gen_random_uuid(),
    email         varchar(320) unique,
    phone         varchar(32)  unique,
    password_hash varchar(200),
    status        varchar(20)  not null default 'pending',   -- pending | active | blocked
    client_type   varchar(20)  not null default 'external',  -- external | internal_mlm
    locale        varchar(8)   not null default 'ru',
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint users_contact_present check (email is not null or phone is not null)
);

create table roles (
    id   smallint primary key,
    code varchar(32) unique not null
);
insert into roles (id, code) values
    (1,'CLIENT_EXTERNAL'), (2,'CLIENT_MLM'), (3,'SHOP'),
    (4,'COURIER'), (5,'ADMIN'), (6,'SUPER_ADMIN');

create table user_roles (
    user_id uuid     not null references users(id) on delete cascade,
    role_id smallint not null references roles(id),
    primary key (user_id, role_id)
);

create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    token_hash  varchar(64) not null unique,     -- sha-256 от самого токена
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked     boolean not null default false,
    replaced_by uuid references refresh_tokens(id)
);
create index idx_refresh_user_active on refresh_tokens(user_id) where revoked = false;

create table otp_codes (
    id         uuid primary key default gen_random_uuid(),
    target     varchar(320) not null,            -- email или телефон
    channel    varchar(10)  not null,            -- email | sms
    code_hash  varchar(64)  not null,
    purpose    varchar(30)  not null,            -- registration | login | reset
    attempts   int          not null default 0,
    expires_at timestamptz  not null,
    consumed_at timestamptz,
    created_at timestamptz  not null default now()
);
create index idx_otp_target on otp_codes(target, purpose);

create table mlm_sso_identities (
    user_id             uuid primary key references users(id) on delete cascade,
    mlm_user_id         varchar(100) unique not null,
    referral_code       varchar(64),
    upline_mlm_user_id  varchar(100),
    last_access_status  varchar(30),
    linked_at           timestamptz not null default now(),
    last_login_at       timestamptz
);

create table mlm_sso_token_log (   -- replay-защита: каждый jti одноразовый
    jti         varchar(64) primary key,
    mlm_user_id varchar(100) not null,
    issued_at   timestamptz not null,
    consumed_at timestamptz not null default now()
);
```

`spring.jpa.hibernate.ddl-auto` оставь `validate` — маппинг сущностей должен совпадать со схемой.

---

## 3. REST API

Базовый префикс — без `/api` (его добавит gateway). Формат ошибок — `ProblemDetail` из
`common-web` (`@Import(RestExceptionHandler.class)` уже стоит в `AuthApplication`).

### 3.1 Регистрация и вход

| Метод | Путь | Тело / параметры | Ответ | Примечания |
|---|---|---|---|---|
| POST | `/auth/register` | `{ email?, phone?, password, locale? }` | `202 Accepted` | создаёт `users.status=pending`, роль `CLIENT_EXTERNAL`, шлёт OTP `purpose=registration` |
| POST | `/auth/otp/request` | `{ target, purpose }` | `204` | rate-limit: не чаще 1/60 сек на target; не раскрывать, существует ли аккаунт |
| POST | `/auth/otp/verify` | `{ target, purpose, code }` | `200 { accessToken, refreshToken, expiresIn }` | при `registration` переводит `status=active`; 5 неверных попыток → код сгорает |
| POST | `/auth/login` | `{ login, password }` | `200 { accessToken, refreshToken, expiresIn }` | `login` = email или телефон; `blocked` → 403; неверные creds → 401 без деталей |
| POST | `/auth/token/refresh` | `{ refreshToken }` | `200 { accessToken, refreshToken, expiresIn }` | **ротация**: старый помечается `revoked`, `replaced_by`; переиспользование отозванного → 401 + отзыв всей цепочки |
| POST | `/auth/logout` | `{ refreshToken }` | `204` | ревок текущего refresh |
| GET | `/auth/me` | — (Bearer) | `200 { userId, email, phone, roles[], clientType, mlmUserId? }` | из JWT + БД |

### 3.2 SSO из MLM

| Метод | Путь | Тело | Ответ | Примечания |
|---|---|---|---|---|
| POST | `/auth/sso/mlm` | `{ token }` | `200 { accessToken, refreshToken, expiresIn, created: bool }` | см. раздел 4 |

### 3.3 Внутренний API (только из сети кластера, не через gateway)

| Метод | Путь | Тело | Ответ | Кто зовёт |
|---|---|---|---|---|
| POST | `/internal/auth/users` | `{ email?, phone?, role, status? }` | `201 { userId }` | catalog при регистрации магазина, courier при регистрации курьера |
| POST | `/internal/auth/users/{id}/roles` | `{ role }` | `204` | catalog после одобрения магазина (`SHOP`), courier (`COURIER`) |
| GET | `/internal/auth/users/{id}` | — | `200 { userId, roles[], status, clientType }` | любой сервис |

Разделение internal/публичного — по порту или по префиксу + отдельный `SecurityFilterChain`,
который принимает только служебный статический токен (`AUTH_INTERNAL_TOKEN` из env). Для MVP
достаточно префикса `/internal/**` + проверка заголовка `X-Internal-Token`.

---

## 4. SSO из внешней MLM-системы

### 4.1 Антикоррупционный слой

Создай интерфейс и держи всю специфику протокола за ним:

```java
package greenecomall.auth.mlm;

public interface MlmIdentityProvider {
    /** Проверяет подпись, iss/aud/exp/nbf, одноразовость jti. Кидает при невалидности. */
    MlmIdentity verifyAndExtract(String rawToken);
}

public record MlmIdentity(
        String mlmUserId,        // обязателен
        String referralCode,     // nullable
        String uplineMlmUserId,  // nullable
        String accessStatus,     // none | access_paid | active | expired (как отдал MLM)
        String email, String phone, String fullName,
        String locale, String countryCode, String city) {}
```

Реализация на сейчас: `MockMlmIdentityProvider` — HS256, общий секрет `MLM_SSO_SHARED_SECRET`
(тот же, что в `infra/mock-mlm/app.py` — `local-dev-mlm-shared-secret-change-me`), проверка
`iss=mock-mlm`, `aud=green-eco-mall`, `exp`, допуск часов 60 сек, `jti` не в `mlm_sso_token_log`.
Реальный контракт (OIDC / RS256+JWKS) — новая реализация того же интерфейса, остальной код не
меняется (см. `docs/ARCHITECTURE.md` §5).

### 4.2 Логика `/auth/sso/mlm`

1. `identity = mlmIdentityProvider.verifyAndExtract(token)`.
2. Записать `jti` в `mlm_sso_token_log` (если уже есть → 401 `sso.token_replayed`).
3. Найти `mlm_sso_identities` по `mlm_user_id`:
   - **нет** → создать `users` (`client_type=internal_mlm`, `status=active`, email/phone/locale
     из identity если есть), роль `CLIENT_MLM`, строку `mlm_sso_identities`
     (`referral_code`, `upline_mlm_user_id`, `last_access_status`). `created=true`.
     Опубликовать `auth.MlmUserLinked` (payload: `userId`, `mlmUserId`, `referralCode`,
     `uplineMlmUserId`) — это событие потом слушает `mlm-service`.
   - **есть** → обновить `last_access_status`, `last_login_at`. `created=false`.
4. Выдать наш access/refresh JWT так же, как в обычном логине.

`auth.MlmUserLinked` пишем в `outbox` в **той же транзакции**, что и создание пользователя.
Публикатор outbox → Kafka можно сделать простым `@Scheduled`-поллером (раз в 1–2 сек:
`select ... where sent_at is null order by created_at limit 100`, отправить, проставить
`sent_at`). Это общий механизм — вынеси в `common-events` или в пакет `auth.outbox`, обсудим на ревью.

---

## 5. Токены (Spring Authorization Server)

- Зависимость: `org.springframework.security:spring-security-oauth2-authorization-server`
  (проверь точное имя артефакта под Spring Boot 4.1.1 в BOM; если под Boot 4 плагина ещё нет —
  сообщи, обсудим ручной вариант на `nimbus-jose-jwt`).
- **access-JWT**: RS256, TTL 15 минут. Claims: `sub` (userId), `roles` (массив кодов из
  `user_roles`), `client_type`, `mlm_user_id` (если есть), стандартные `iss/aud/exp/iat/jti`.
- **refresh-токен**: непрозрачная строка (32+ байта энтропии), TTL 30 дней, хранится в БД
  **только как sha-256** (`refresh_tokens.token_hash`). Ротация при каждом refresh.
  Обнаружение переиспользования отозванного токена → отозвать всю цепочку (`replaced_by`) и
  вернуть 401.
- **JWKS**: публичный эндпоинт `GET /oauth2/jwks` (его читают `api-gateway` и остальные сервисы).
  Ключ RSA 2048 — для dev сгенерировать при старте и держать в памяти; TODO-комментарий, что в
  проде ключ берётся из секрета/волта и ротируется.
- Пароли: `PasswordEncoder` = bcrypt (strength 12) или Argon2.

---

## 6. api-gateway — включить проверку JWT

В `services/api-gateway` (webflux):

- Настроить resource-server: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` =
  `${GATEWAY_JWT_JWK_SET_URI}` (по умолчанию `http://localhost:8081/oauth2/jwks`, в
  `application.yml` уже есть плейсхолдер).
- Публичные пути без токена: `/`, `/actuator/**`, `POST /api/auth/register`,
  `/api/auth/otp/**`, `/api/auth/login`, `/api/auth/token/refresh`, `/api/auth/sso/mlm`,
  `GET /api/catalog/**` (витрина для гостя). Остальное — требует валидный JWT.
- После валидации — глобальный фильтр проставляет вниз заголовки (константы в
  `common-security` → `GatewayHeaders`): `X-User-Id`, `X-User-Roles` (csv), `X-Client-Type`,
  `X-Trace-Id`. Эти же заголовки **вырезать из входящего запроса снаружи**, чтобы их нельзя
  было подделать.
- Роутинг на сервисы пока не делаем (Spring Cloud Gateway ждёт релиза под Boot 4.1.1 — см.
  TODO в `api-gateway/pom.xml`). Достаточно security-фильтра и фильтра заголовков; проверяется
  юнит-тестом с `WebTestClient`.

---

## 7. Тесты (обязательно)

- **Unit**: ротация refresh (happy path + переиспользование отозванного), проверка OTP
  (истечение, лимит попыток), `MockMlmIdentityProvider` (плохая подпись / чужой `aud` /
  просроченный / повторный `jti`).
- **Integration** (Testcontainers Postgres, зависимости уже в pom): миграции применяются;
  `register → otp/verify → login → refresh → logout`; `sso/mlm` создаёт пользователя и пишет
  `auth.MlmUserLinked` в `outbox`; повторный вызова `sso/mlm` с тем же токеном → 401.
- Для событий Kafka в интеграционных тестах достаточно проверять запись в `outbox`
  (публикатор можно тестировать отдельно с embedded Kafka).

---

## 8. Критерии приёмки (Definition of Done)

- [ ] `./mvnw -pl services/auth-service,services/api-gateway -am verify` — зелёный.
- [ ] Поднят `docker compose -f infra/docker-compose.infra.yml up -d`; `auth-service`
      стартует из IDE, `GET /actuator/health` → `UP`.
- [ ] Полный путь руками (curl/Postman-коллекция в `docs/http/auth.http`):
      регистрация → OTP из лога MailHog → verify → `/auth/me` с полученным access-токеном.
- [ ] `GET http://localhost:9100/sso/issue-token?mlm_user_id=U1&upline_id=U0` из `mock-mlm`
      → полученный токен в `POST /auth/sso/mlm` → выдан наш JWT, в БД есть `users` +
      `mlm_sso_identities`, в `outbox` — `auth.MlmUserLinked`.
- [ ] `GET /oauth2/jwks` отдаёт ключ; в `api-gateway` запрос без токена на защищённый путь →
      401, с валидным → проходит и вниз уходят заголовки `X-User-*`.
- [ ] Refresh-токены в БД только в виде хэша; переиспользование отозванного → 401 + цепочка отозвана.
- [ ] README модуля (`services/auth-service/README.md`): как запустить, список эндпоинтов,
      env-переменные (`MLM_SSO_SHARED_SECRET`, `AUTH_INTERNAL_TOKEN`, `GATEWAY_JWT_JWK_SET_URI`).
- [ ] MR в `main`, описание со ссылкой на это задание, отмечены отклонения от контракта (если были).

## 9. Договорённости по процессу

- Коммиты — Conventional Commits (`feat(auth): ...`, `test(auth): ...`).
- Не расширять скоуп: реф-дерево, активации MLM, промокоды, реальная SMS — не здесь.
- Спорные места по контракту событий/`common-*` — комментарий в MR, меняем согласованно
  (эти модули общие для всех сервисов).
- Вопросы к контракту MLM (способ подписи, состав claim'ов, эндпоинты обратной синхронизации)
  копи в `docs/ARCHITECTURE.md` §11 / открытые вопросы 9–11 — их выясняет владелец продукта
  с командой MLM-бэка, тебе достаточно `MockMlmIdentityProvider`.
