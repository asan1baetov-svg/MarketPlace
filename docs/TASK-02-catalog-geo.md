# Задание №2 — catalog-service: гео-справочники (countries/cities/delivery_zones)

> **Статус: выполнено (2026-09-08).** Реализовано напрямую (без передачи — см.
> [решение работать в одиночку]). Сущности/репозитории/`GeoService`/контроллеры на месте, юнит- и
> Testcontainers-тесты добавлены, полный CRUD-флоу (страна → город → зона, включая отказ удаления
> при наличии зависимых записей) проверен вручную через реальный Postgres. Документ ниже остаётся
> как справочная спецификация контракта.

**Кому:** backend-коллега (историческая формулировка — по факту сделано автором проекта)
**Ветка:** `feature/catalog-geo`
**Оценка:** ~2 рабочих дня
**Предусловие:** `services/catalog-service` — пустой скелет (только `CatalogApplication`,
`application.yml`, `V001__baseline.sql` с `processed_events`/`outbox`). `spring-boot-starter-flyway`
в его `pom.xml` уже добавлен — не убирай, без него Flyway в Spring Boot 4 молча не выполняется.

Общий контекст — `docs/ARCHITECTURE.md` §2.2, §3.2 (там же полная схема catalog, но в этом
задании — **только гео-часть**, остальное не трогай). Как выглядит готовый REST-слой на похожей
задаче — смотри `services/auth-service` (`web/`, `config/SecurityConfig.java`, `repo/`): это
свежий, полностью рабочий пример структуры пакетов/слоёв, ориентируйся на него.

---

## 1. Что делаем и зачем

Гео-справочники — страны, города, зоны доставки — нужны каталогу для фильтрации витрины по
городу клиента (раздел 2.2 архитектуры) и магазину при регистрации. Это самая простая,
самодостаточная часть catalog-service: чистый CRUD без Kafka-событий, без ценообразования, без
зависимостей от других таблиц каталога.

**Не в этом задании** (не трогай): `shops`, `products`, `categories`, `markup_rules`, витрина,
Redis-кэш, Kafka-события каталога, JWT/ролевая проверка (админ-эндпоинты пока оставляем
открытыми — `permitAll`, ровно как было в auth-service на первом шаге, см. `AuthErrors`-аналог
ниже). Эти куски — отдельные следующие задания.

---

## 2. Схема БД (Flyway `V002__geo.sql`)

`V001__baseline.sql` не трогай. Добавь `src/main/resources/db/migration/V002__geo.sql`:

```sql
create table countries (
    id         uuid primary key default gen_random_uuid(),
    name       varchar(200) not null,
    iso_code   varchar(2)   not null unique,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table cities (
    id         uuid primary key default gen_random_uuid(),
    country_id uuid         not null references countries(id),
    name       varchar(200) not null,
    lat        numeric(9,6),
    lon        numeric(9,6),
    timezone   varchar(64)  not null default 'Asia/Bishkek',
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index idx_cities_country on cities (country_id);

create table delivery_zones (
    id         uuid primary key default gen_random_uuid(),
    city_id    uuid         not null references cities(id),
    name       varchar(200) not null,
    geo        jsonb,
    radius_m   integer,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index idx_zones_city on delivery_zones (city_id);
```

`spring.jpa.hibernate.ddl-auto` уже `validate` в `application.yml` — сущности должны точно
соответствовать схеме (как в auth-service).

## 3. Слои (по образцу auth-service)

- `domain/` — JPA-сущности `Country`, `City`, `DeliveryZone`. Простые POJO с приватным
  no-args конструктором + публичным конструктором для создания, геттеры, `created_at`/`updated_at`
  — `@CreationTimestamp`/`@UpdateTimestamp` (см. `OtpCode`/`RefreshToken` в auth-service как пример).
- `repo/` — `CountryRepository`, `CityRepository`, `DeliveryZoneRepository` (`JpaRepository`).
  Добавь `existsByIsoCode`, `findByCountryId`, `findByCityId` — понадобятся для валидации и списков.
- `geo/GeoService.java` — один сервис на все три сущности достаточно (не разбивай на три ради
  разбивки): create/update/delete/list для каждой, с проверками:
  - `iso_code` страны — 2 буквы, уникален (иначе `DomainException` с новым кодом
    `catalog.country_code_taken`);
  - создание города — страна должна существовать (`catalog.country_not_found`);
  - создание зоны — город должен существовать (`catalog.city_not_found`);
  - удаление страны/города с зависимыми городами/зонами — запрети явной проверкой
    (`catalog.geo_has_dependents`), не полагайся на FK-исключение из БД.
- `web/` — контроллеры и DTO (records), `@RestControllerAdvice` по образцу
  `AuthExceptionHandler` — сопоставь новые коды ошибок с HTTP-статусами (404 — not found,
  409 — code taken / has dependents).

## 4. REST API

| Метод | Путь | Тело | Ответ | Примечания |
|---|---|---|---|---|
| GET | `/catalog/geo/countries` | — | `200 [{id,name,isoCode}]` | публичный, для селектора страны/города на клиенте |
| GET | `/catalog/geo/cities?countryId=` | — | `200 [{id,name,countryId}]` | публичный |
| POST | `/admin/geo/countries` | `{name, isoCode}` | `201 {id}` | |
| PUT | `/admin/geo/countries/{id}` | `{name, isoCode}` | `200` | |
| DELETE | `/admin/geo/countries/{id}` | — | `204` | 409, если есть города |
| POST | `/admin/geo/cities` | `{countryId, name, lat?, lon?, timezone?}` | `201 {id}` | |
| PUT | `/admin/geo/cities/{id}` | `{name, lat?, lon?, timezone?}` | `200` | страну не меняем |
| DELETE | `/admin/geo/cities/{id}` | — | `204` | 409, если есть зоны |
| POST | `/admin/geo/zones` | `{cityId, name, radiusM?}` | `201 {id}` | `geo` (JSONB-полигон) — пропусти в этом задании, оставь `null` |
| PUT | `/admin/geo/zones/{id}` | `{name, radiusM?}` | `200` | |
| DELETE | `/admin/geo/zones/{id}` | — | `204` | |

Все `/admin/**` в этом задании без проверки роли (permitAll — см. §5). JWT-проверку и
роль `ADMIN` подключим отдельным заданием, когда у catalog-service появится связь с JWKS
auth-service (по аналогии с тем, как это сделано в `api-gateway`).

## 5. Security

Скопируй временную заглушку из истории auth-service: один `SecurityFilterChain`,
`anyRequest().permitAll()`, `csrf().disable()`, `sessionManagement` — `STATELESS`. Ничего сложнее
не нужно.

## 6. Тесты (обязательно)

- Unit: `GeoService` — валидации (дубликат `isoCode`, город на несуществующую страну, удаление
  страны с городами). Мокай репозитории, как в `TokenServiceTest`/`OtpServiceTest` из auth-service.
- Integration (Testcontainers Postgres, зависимости в pom уже есть): подними контекст, прогони
  через реальные контроллеры: создать страну → город → зону → получить списки → удалить зону →
  удалить город → удалить страну. Проверь, что удаление страны с существующим городом даёт 409.

  Если Testcontainers у тебя на машине не может найти Docker при абсолютно рабочем `docker ps` —
  это известная нестыковка версии docker-java в testcontainers 1.20.4 с новыми Docker Desktop,
  а не баг в тесте; напиши мне, замерим версию у тебя и решим, поднимать ли testcontainers в BOM.

## 7. Definition of Done

- [ ] `./mvnw -pl services/catalog-service -am verify` — зелёный.
- [ ] `docker compose -f infra/docker-compose.infra.yml up -d postgres` (или свой Postgres),
      сервис стартует из IDE, `GET /actuator/health` → `UP`, миграция `V002__geo.sql` применилась
      (видно в логе Flyway при старте — `Migrating schema "public" to version "002 - geo"`).
- [ ] Ручной прогон curl/Postman: создать страну → город → зону → получить списки → проверить
      409 при попытке удалить страну с городом.
- [ ] MR в `main`, описание со ссылкой на это задание.

## 8. Договорённости по процессу

- Коммиты — Conventional Commits (`feat(catalog): ...`, `test(catalog): ...`).
- Не расширяй скоуп на `shops`/`products`/`categories` — это следующие задания.
- Если контракт (пути, поля) неудобен на практике — пиши в комментарии к MR, обсудим, но
  не меняй молча.
