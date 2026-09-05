# Архитектура «Грин Эко Молл»

> Архитектура для согласования. Скелет монорепозитория собран (`./mvnw compile` — зелёный),
> бизнес-логику сервисов пишем поэтапно (раздел 9).
>
> **Стек зафиксирован:** Java 21, Spring Boot 4.1.1, Maven multi-module. Auth — свой
> (Spring Authorization Server внутри `auth-service`), без Keycloak.

## Оглавление

1. [Общая схема](#1-общая-схема)
2. [Декомпозиция на сервисы](#2-декомпозиция-на-сервисы)
3. [Схема БД по сервисам](#3-схема-бд-по-сервисам)
4. [Kafka: топики и события](#4-kafka-топики-и-события)
5. [Контракт SSO-интеграции с MLM-бэком](#5-контракт-sso-интеграции-с-mlm-бэком)
6. [Monorepo vs multi-repo](#6-monorepo-vs-multi-repo)
7. [Docker Compose и GitLab CI/CD](#7-docker-compose-и-gitlab-cicd)
8. [Риски и открытые вопросы](#8-риски-и-открытые-вопросы)
9. [Порядок реализации](#9-порядок-реализации)

---

## 1. Общая схема

### 1.1 Компромисс для одного разработчика

Ты один backend-разработчик среднего уровня. 11 микросервисов из ТЗ — это 11 деплоев, 11 БД,
распределённые транзакции и отладка по трейсам с первого дня. Поэтому:

- **один монорепозиторий**, Maven multi-module (собран, см. раздел 6);
- **7 сервисов вместо 11** (обоснование — 1.3);
- **каждый сервис — самостоятельное Spring Boot приложение** со своей БД-схемой и портом.
  Локально запускаешь из IDE только нужные сейчас; всё вместе — через `docker-compose`;
- границы уже сейчас «микросервисные»: своя БД (никаких общих таблиц и FK между схемами),
  общение через **Kafka** и узкие REST-контракты, никаких `@Transactional` через границу
  сервиса. Это позволяет масштабировать и делить владение без переписывания логики.

Если позже понадобится один процесс на dev-машине — добавляется агрегирующий модуль `app`,
импортирующий домены как `@Configuration`; на архитектуру это не влияет, начинать с него не нужно.

Ниже — карта из 7 сервисов; для каждого указано, что он вобрал из черновика ТЗ.

### 1.2 Целевая карта сервисов

```
                        ┌─────────────────┐
   Web / Mobile / Bot ──►   api-gateway    │  Spring Cloud Gateway (по TODO; сейчас webflux)
                        │  (BFF + auth)    │  - маршрутизация
                        └───────┬─────────┘  - проверка JWT, проброс ролей
                                │            - агрегация для админки
        ┌───────────────┬───────┼────────────┬───────────────┬───────────────┐
        ▼               ▼       ▼            ▼               ▼               ▼
 ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
 │   auth     │ │  catalog   │ │  order   │ │  finance   │ │  courier   │ │    mlm     │
 │  service   │ │  service   │ │ service  │ │  service   │ │  service   │ │  service   │
 │            │ │            │ │          │ │ payment +  │ │            │ │ mlm-core + │
 │ users,     │ │ shops,     │ │ cart,    │ │ wallet     │ │ couriers,  │ │ integration│
 │ roles, JWT │ │ products,  │ │ orders,  │ │ ledger,    │ │ assignment │ │ (SSO/sync) │
 │ OTP, SSO   │ │ categories,│ │ suborders│ │ payouts    │ │            │ │            │
 │ вход MLM   │ │ geo, наце- │ │ статусы  │ │            │ │            │ │            │
 │            │ │ нка, кэш   │ │          │ │            │ │            │ │            │
 └─────┬──────┘ └─────┬──────┘ └────┬─────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘
       │              │             │             │              │              │
       └──────────────┴─────────────┴──────┬──────┴──────────────┴──────────────┘
                                           ▼
                                     ┌───────────┐         ┌──────────────────┐
                                     │  Kafka    │◄────────┤ notification-svc  │
                                     │ (KRaft)   │         │ push/sms/telegram │
                                     └───────────┘         └──────────────────┘
                                           ▲
                     ┌─────────────────────┴──────────────────────┐
                     │ Postgres (схема на сервис) │ Redis (кэш)   │
                     └────────────────────────────────────────────┘

Внешние системы:  Эквайринг (webhook) ──► finance-service
                  Внешний MLM-бэк  ◄──► mlm-service (SSO вход + обратная синхронизация)
```

### 1.3 Изменения относительно черновой декомпозиции ТЗ

| Черновик ТЗ | Решение | Почему |
|---|---|---|
| `auth-service` | Оставляем | — |
| `catalog-service` | Оставляем, **включает гео-справочники** (countries/cities/zones) | Гео нужно каталогу для фильтрации на каждом запросе; отдельный geo-service — лишний сетевой хоп |
| `order-service` | Оставляем, **включает корзину** | Корзина — это черновик заказа, отдельный cart-service не нужен |
| `payment-service` + `wallet-service` | **Сливаем в `finance-service`** (два модуля внутри) | Один разработчик; расчёт распределения средств по `OrderPaid` — это одна транзакционная история. Разъединить всегда успеем |
| `courier-service` | Оставляем | — |
| `mlm-service` + `mlm-integration-service` | **Сливаем в `mlm-service`** (модуль `core` + модуль `integration` с антикоррупционным слоем) | Интеграция без ядра бессмысленна; ACL-слой изолирует внешний контракт внутри одного сервиса |
| `notification-service` | Оставляем | Чисто consumer + шлюзы, легко отделяется |
| `admin-gateway / BFF` | **Роль отдаём `api-gateway`** | Отдельный BFF оправдан при нескольких фронтах; сейчас — эндпоинты `/admin/**` и агрегация в гейтвее |
| `search-service` | **Откладываем** | Postgres + индекс `(city_id, category_id, status)` держит MVP. Elasticsearch — когда упрёмся |

Итого: **7 сервисов** (auth, catalog, order, finance, courier, mlm, notification) + `api-gateway`.
На старте — один процесс, 8 модулей.

---

## 2. Декомпозиция на сервисы

Для каждого: назначение, сущности, ключевой REST API (верхний уровень), Kafka in/out.

### 2.1 auth-service

**Назначение.** Аутентификация, роли (RBAC), выдача и ротация JWT, OTP по email/телефону,
точка входа SSO из внешнего MLM-бэка (валидация внешнего токена → создание/поиск аккаунта →
выдача внутреннего JWT).

**Сущности.** `users`, `roles`, `user_roles`, `refresh_tokens`, `otp_codes`,
`mlm_sso_identities` (связь `user` ↔ `mlm_user_id`).

**REST API.**
- `POST /auth/register` — регистрация клиента (email/телефон + пароль)
- `POST /auth/otp/request`, `POST /auth/otp/verify`
- `POST /auth/login`, `POST /auth/token/refresh`, `POST /auth/logout`
- `POST /auth/sso/mlm` — обмен внешнего MLM-токена на внутренний JWT (см. раздел 5)
- `GET /auth/me` — профиль + роли + `client_type`
- `POST /internal/auth/users` — сервисное создание пользователя (для регистрации магазина/курьера)

**Kafka.**
- out: `auth.UserRegistered`, `auth.MlmUserLinked`
- in: —

**Заметки.** Пароли — Argon2/BCrypt. Access-JWT 15 мин, refresh 30 дней с ротацией и
revoke-списком. Роль магазина/курьера выдаётся только после модерации (событие из catalog/courier).

### 2.2 catalog-service

**Назначение.** Магазины-партнёры (регистрация + модерация), товары (загрузка, модерация,
публикация), иерархия категорий, гео-справочники, правила наценки, гео-фильтрованная витрина,
кэш популярного в Redis.

**Сущности.** `countries`, `cities`, `delivery_zones`, `shops`, `shop_delivery_zones`,
`categories`, `products`, `product_images`, `product_stock`, `markup_rules`,
`product_reviews`, `shop_reviews`.

**REST API.**
- Витрина (клиент/гость): `GET /catalog/products?cityId=&categoryId=&minPrice=&maxPrice=&rating=&shopId=&sort=`,
  `GET /catalog/products/{id}`, `GET /catalog/categories`, `GET /catalog/shops/{id}`
- Магазин: `POST /shops` (заявка), `POST /shops/{id}/products`, `PUT /products/{id}`,
  `POST /products/{id}/submit`, `PATCH /products/{id}/stock`, `POST /products/import` (CSV/Excel)
- Админ: `POST /admin/shops/{id}/approve|reject`, `POST /admin/products/{id}/publish|reject`,
  `PUT /admin/markup-rules`, CRUD `/admin/geo/countries|cities|zones`
- Внутренний: `GET /internal/catalog/price?productId=&cityId=` (резолв наценки),
  `POST /internal/catalog/stock/reserve`, `POST /internal/catalog/stock/release`,
  `POST /internal/catalog/stock/commit`

**Kafka.**
- out: `catalog.ShopApproved`, `catalog.ShopSuspended`, `catalog.ProductPublished`,
  `catalog.ProductRejected`, `catalog.ProductArchived`, `catalog.StockChanged`
- in: `payments.OrderPaid` (списание остатков), `orders.OrderCancelled` (возврат резерва)

**Ценообразование.** `sale_price = cost_price * (1 + markup_percent/100)`. `markup_percent`
резолвится из `markup_rules` по приоритету: `PRODUCT` > `SHOP` > `CATEGORY+REGION` > `CATEGORY`
> `REGION` > `GLOBAL`. Резолв кэшируется в Redis по ключу `price:{productId}:{cityId}`,
инвалидация по `ProductPublished` / изменению правил.

**Гео-фильтрация.** По умолчанию `WHERE shop.city_id = :cityId AND product.status='published'`.
Если у магазина заданы `delivery_zones`, включающие зону клиента — товар тоже виден
(join через `shop_delivery_zones`). Индексы: `products(city_id, category_id, status)`,
`products(city_id, status, rating)`.

### 2.3 order-service

**Назначение.** Мультивендорная корзина (товары из разных магазинов **одного города**),
оформление заказа, разбиение на suborders (по одному на магазин), конечный автомат статусов,
агрегация статуса `order` из статусов suborders, промокоды.

**Сущности.** `carts`, `cart_items`, `orders`, `suborders`, `suborder_items`,
`order_status_history`, `promocodes`, `order_promocodes`.

**REST API.**
- Клиент: `GET/POST/DELETE /cart/items`, `POST /cart/checkout` (адрес, промокод → создаёт
  `order` + `suborders`, возвращает `paymentId` от finance), `GET /orders`, `GET /orders/{id}`
- Магазин: `GET /shop/suborders?status=`, `POST /shop/suborders/{id}/accept|assemble`
  (магазин видит `cost_price`, **не видит наценку/`sale_price`**)
- Админ: `GET /admin/orders?status=&cityId=&shopId=&date=&clientId=`, `POST /admin/orders/{id}/cancel`,
  `POST /admin/orders/{id}/refund`
- Внутренний: `GET /internal/orders/{id}`, `POST /internal/suborders/{id}/status`

**Kafka.**
- out: `orders.OrderCreated`, `orders.OrderCancelled`, `orders.SuborderStatusChanged`,
  `orders.OrderCompleted`
- in: `payments.OrderPaid` / `payments.PaymentFailed` (→ статус `paid` / `payment_failed`),
  `couriers.DeliveryPickedUp|InTransit|Completed|Failed` (→ статусы suborder),
  `catalog.StockChanged` (валидация доступности при чекауте — синхронно через Feign/RestClient,
  событие для актуализации локального read-кэша)

**Статусы suborder.** `created → paid → accepted → assembled → handed_to_courier → in_transit
→ delivered → completed` (+ `cancelled`, `refunded`). `order.status` = агрегат
(например, `partially_delivered`, если часть suborders доставлена).

**Резерв остатков.** При `checkout` — синхронный вызов `catalog.stock/reserve` (условный
`UPDATE ... WHERE quantity - reserved >= :qty`). Резерв держится до оплаты; по таймауту
(например, 15 мин без оплаты) — `OrderCancelled` → release. Финальное списание — по `OrderPaid`.

### 2.4 finance-service (payment + wallet)

**Назначение.** Модуль **payment**: приём онлайн-оплаты через эквайринг (карты + локальные ПС),
обработка webhook, отдельный тип платежа «оплата доступа MLM», возвраты. Модуль **wallet**:
внутренние кошельки (магазин / курьер / платформа), ledger двойной записи, заявки на вывод и
их подтверждение админом.

**Сущности.**
- payment: `payments`, `payment_events` (лог webhook + идемпотентность), `refunds`
- wallet: `wallets`, `wallet_transactions` (append-only ledger), `payout_requests`

**REST API.**
- Клиент: `POST /payments` (создать платёж по заказу — обычно вызывается order-service),
  `GET /payments/{id}`, `POST /payments/mlm-access` (оплата входного билета)
- Webhook: `POST /webhooks/acquiring/{provider}` (проверка подписи, идемпотентность по
  `provider_event_id`)
- Магазин/курьер: `GET /wallet` (баланс + история), `POST /wallet/payouts` (заявка на вывод)
- Админ: `GET /admin/finance/settlements`, `POST /admin/payouts/{id}/approve|reject`,
  `GET /admin/finance/reconciliation?date=` (сверка с эквайрингом)

**Kafka.**
- out: `payments.PaymentInitiated`, `payments.OrderPaid`, `payments.PaymentFailed`,
  `payments.PaymentRefunded`, `payments.MlmAccessPaid`,
  `wallet.WalletCredited`, `wallet.WalletDebited`, `wallet.PayoutRequested`,
  `wallet.PayoutApproved`, `wallet.PayoutPaid`
- in: `orders.OrderCreated` (подготовка платёжного намерения),
  `orders.OrderCancelled` / возвраты (компенсация по кошелькам),
  `couriers.DeliveryCompleted` (начисление вознаграждения курьеру, если сдельно от платформы)

**Распределение по `OrderPaid`.** Для каждого suborder:
- `goods_amount` = Σ `sale_price` позиций; `cost_amount` = Σ `cost_price`;
- `platform_commission` = `goods_amount − cost_amount` (± корректировка на промокод, см. R7);
- проводки ledger (двойная запись):
  `platform.incoming` → `shop.wallet` на `cost_amount`;
  `platform.incoming` → `platform.commission` на `platform_commission`.
- Кошелёк — **виртуальный баланс**, реального перевода нет. Реальный перевод — по
  `payout_request` после подтверждения админом (R8: для MVP вручную).

**Идемпотентность.** Каждая проводка имеет `idempotency_key` (`{eventId}:{walletId}:{type}`),
повторный `OrderPaid` не задваивает начисления.

### 2.5 courier-service

**Назначение.** Регистрация курьеров с модерацией, привязка к городу/зонам, подбор курьера
под suborder, конечный автомат доставки, заработок за период.

**Сущности.** `couriers`, `courier_delivery_zones`, `courier_assignments`,
`assignment_offers` (для балансировки/раунд-робина), `courier_earnings`.

**REST API.**
- Курьер: `POST /couriers` (заявка), `GET /couriers/me/assignments?status=`,
  `POST /assignments/{id}/accept|reject`, `POST /assignments/{id}/pickup|in-transit|deliver|fail`
  (`fail` — с причиной)
- Админ: `POST /admin/couriers/{id}/approve|reject`, `GET /admin/couriers?cityId=&status=`,
  `POST /admin/assignments` (ручное назначение), мониторинг статусов
- Внутренний: `GET /internal/couriers/available?cityId=&zoneId=`

**Kafka.**
- out: `couriers.CourierRegistered`, `couriers.CourierAssigned`, `couriers.CourierAccepted`,
  `couriers.DeliveryPickedUp`, `couriers.DeliveryInTransit`, `couriers.DeliveryCompleted`,
  `couriers.DeliveryFailed`
- in: `payments.OrderPaid` (suborder оплачен → запустить подбор курьера),
  `orders.SuborderStatusChanged` (магазин собрал → курьер может забирать)

**Подбор.** Кандидаты: `courier.city_id = shop.city_id` (или пересечение зон), `status=active`.
Балансировка: минимальная текущая загрузка, затем рейтинг. Оффер с TTL; при отказе/таймауте —
следующий кандидат. Нет курьеров в городе → `couriers.NoCourierAvailable` → уведомление админу
(и, по решению, блок оформления — см. R1).
**MVP-решение по мультимагазинному заказу:** один курьер на **один suborder** (проще подбор и
расчёт). Маршрутизацию «один курьер собирает все suborders города» добавим позже.

### 2.6 mlm-service (core + integration)

**Назначение.** Модуль **core**: MLM-аккаунты, жизненный цикл активации (оплатил доступ →
обязан купить на сумму X в срок Y → выполнил → активен / просрочено), реферальное дерево,
начисление бонусов, тарифы и проценты. Модуль **integration**: SSO-вход из внешнего MLM-бэка
(валидация токена делегируется, маппинг `mlm_user_id` ↔ `user`), обратная синхронизация
(webhook в MLM-бэк о выполнении условия и о покупках) через транзакционный outbox +
антикоррупционный слой.

**Сущности.**
- core: `mlm_accounts` (`client_type`, `mlm_user_id`, `access_status`, `activation_deadline`,
  `required_purchase_amount`, `achieved_purchase_amount`, `bonus_balance`), `mlm_tariffs`,
  `mlm_referral_rates`, `mlm_referral_tree` (closure table: `ancestor_id`, `descendant_id`,
  `depth`), `mlm_bonus_transactions`
- integration: `mlm_sso_token_log` (`jti`, replay-защита), `mlm_sync_outbox`

**REST API.**
- Клиент MLM: `GET /mlm/me` (статус активации, таймер до дедлайна, бонусный баланс,
  история начислений), `GET /mlm/me/referrals` (дерево)
- Внутренний: `POST /internal/mlm/sso/resolve` (по данным из внешнего токена вернуть/создать
  `mlm_account`; вызывается auth-service), `GET /internal/mlm/accounts/{mlmUserId}`
- Админ: CRUD `/admin/mlm/tariffs`, `/admin/mlm/referral-rates`, `GET /admin/mlm/tree`,
  `GET /admin/mlm/reports/revenue`

**Kafka.**
- out: `mlm.MlmUserLoggedIn`, `mlm.MlmAccessActivated` (стартовал срок), `mlm.MlmPurchaseCounted`,
  `mlm.MlmConditionMet`, `mlm.MlmActivated`, `mlm.MlmExpired`, `mlm.MlmReferralBonusAccrued`
- in: `payments.MlmAccessPaid` (старт окна активации: `access_status=must_purchase`,
  `activation_deadline=now+Y`), `payments.OrderPaid` (если плательщик — MLM-клиент:
  прибавить к `achieved_purchase_amount`; при достижении X → `MlmConditionMet` →
  `access_status=active`, запись в `mlm_sync_outbox`)

**Дедлайн.** Планировщик (Spring `@Scheduled` / отдельный quartz) раз в час помечает
просроченные: `must_purchase` + `deadline < now` → `access_status=expired` → `MlmExpired`.
Напоминания за 3 дня и за 1 день — команда в `notification-service`.

**Источник истины по активации (R9):** маркетплейс считает условие «покупка на X в срок Y»
(он владеет данными заказов) и **пушит статус** в MLM-бэк. MLM-бэк владеет оплатой доступа и
реферальными выплатами. Требует подтверждения командой MLM.

### 2.7 notification-service

**Назначение.** Единая точка отправки уведомлений: push, SMS, Telegram-бот. Шаблоны,
мультиязычность, пользовательские настройки каналов.

**Сущности.** `notification_templates` (по `code` + `channel` + `locale`), `notifications`
(журнал отправок + статус), `user_notification_prefs`, `device_tokens`.

**REST API.**
- `POST /internal/notifications` — команда «отправить» (шаблон + получатель + payload)
- Клиент: `GET/PUT /notifications/preferences`, `POST /notifications/devices` (регистрация push-токена)

**Kafka.**
- in: подписка на **всё существенное** — `orders.SuborderStatusChanged`,
  `couriers.CourierAssigned`, `couriers.Delivery*`, `wallet.WalletCredited`,
  `wallet.PayoutApproved`, `mlm.MlmExpired`, `mlm.MlmActivated`, `catalog.ProductPublished/Rejected`,
  `catalog.ShopApproved`, плюс командный топик `notifications.commands`
- out: `notifications.NotificationSent` (для аналитики, опционально)

### 2.8 api-gateway

**Назначение.** Spring Cloud Gateway: маршрутизация на сервисы, проверка подписи JWT и проброс
`X-User-Id` / `X-Roles` вниз, rate limiting (Redis), CORS, агрегация ответов для админ-панели
(несколько upstream-вызовов в один ответ), единая OpenAPI-точка.

**Без своей БД.** Секрет/JWKS для проверки JWT берёт у auth-service.

---

## 3. Схема БД по сервисам

Принцип: **БД (схема) на сервис**, без межсхемных FK. Идентификаторы — `UUID` (`gen_random_uuid()`).
Деньги — целые в минорных единицах (`amount_minor BIGINT` + `currency CHAR(3)`), **никаких `float`/`double`**.
Все таблицы: `created_at`, `updated_at`. Миграции — Flyway (`V001__*.sql`).

### 3.1 auth

```
users(id PK, email UNIQUE NULL, phone UNIQUE NULL, password_hash NULL,
      status ENUM[pending,active,blocked], client_type ENUM[external,internal_mlm] DEFAULT external,
      locale, created_at, updated_at)
roles(id PK, code UNIQUE)   -- CLIENT_EXTERNAL, CLIENT_MLM, SHOP, COURIER, ADMIN, SUPER_ADMIN
user_roles(user_id FK→users, role_id FK→roles, PK(user_id,role_id))
refresh_tokens(id PK, user_id FK→users, token_hash, issued_at, expires_at, revoked BOOL,
      replaced_by NULL)
otp_codes(id PK, target, channel ENUM[email,sms], code_hash, purpose, attempts INT,
      expires_at, consumed_at NULL)
mlm_sso_identities(user_id PK FK→users, mlm_user_id UNIQUE, referral_code NULL,
      upline_mlm_user_id NULL, last_access_status, linked_at, last_login_at)
```
Индексы: `users(email)`, `users(phone)`, `mlm_sso_identities(mlm_user_id)`,
`refresh_tokens(user_id, revoked)`.

### 3.2 catalog

```
countries(id PK, name, iso_code UNIQUE)
cities(id PK, country_id FK→countries, name, lat NULL, lon NULL, timezone)
delivery_zones(id PK, city_id FK→cities, name, geo JSONB NULL, radius_m INT NULL)

shops(id PK, owner_user_id, name, legal_info JSONB, country_id FK, city_id FK,
      status ENUM[draft,moderation,active,rejected,suspended], rejection_reason NULL,
      rating NUMERIC(2,1) DEFAULT 0, reviews_count INT DEFAULT 0, created_at, updated_at)
shop_delivery_zones(shop_id FK→shops, delivery_zone_id FK→delivery_zones, PK(shop_id,delivery_zone_id))

categories(id PK, parent_id NULL FK→categories, name, slug, path LTREE|VARCHAR, sort INT)

products(id PK, shop_id FK→shops, category_id FK→categories, name, description,
      unit ENUM[pcs,kg,l,...], cost_price_minor BIGINT, currency CHAR(3),
      status ENUM[draft,moderation,published,archived,rejected], rejection_reason NULL,
      -- денормализация гео для фильтра:
      city_id, country_id,
      rating NUMERIC(2,1) DEFAULT 0, reviews_count INT DEFAULT 0, created_at, updated_at)
product_images(id PK, product_id FK→products, url, sort INT)
product_stock(product_id PK FK→products, quantity INT, reserved_quantity INT DEFAULT 0)

markup_rules(id PK, scope ENUM[GLOBAL,CATEGORY,SHOP,PRODUCT,REGION,CATEGORY_REGION],
      category_id NULL, shop_id NULL, product_id NULL, country_id NULL, city_id NULL,
      markup_percent NUMERIC(6,2), priority INT, active BOOL, created_at)

product_reviews(id PK, product_id FK, order_id, client_user_id, rating INT CHECK 1..5,
      text NULL, created_at)
shop_reviews(id PK, shop_id FK, order_id, client_user_id, rating INT CHECK 1..5, text NULL, created_at)
```
Индексы: `products(city_id, category_id, status)`, `products(city_id, status, rating DESC)`,
`products(shop_id, status)`, `categories(parent_id)`, `markup_rules(scope, active)`.

### 3.3 order

```
carts(id PK, client_user_id UNIQUE, city_id, created_at, updated_at)
cart_items(id PK, cart_id FK→carts, product_id, shop_id, qty INT,
      unit_sale_price_minor BIGINT, currency, added_at)

orders(id PK, client_user_id, city_id, delivery_address JSONB,
      status ENUM[created,paid,partially_*,completed,cancelled,payment_failed],
      items_amount_minor BIGINT, discount_amount_minor BIGINT DEFAULT 0,
      total_amount_minor BIGINT, currency, payment_id NULL, created_at, updated_at)

suborders(id PK, order_id FK→orders, shop_id,
      status ENUM[created,paid,accepted,assembled,handed_to_courier,in_transit,delivered,completed,cancelled,refunded],
      goods_amount_minor BIGINT,        -- Σ sale_price
      cost_amount_minor BIGINT,         -- Σ cost_price (для выплаты магазину)
      platform_commission_minor BIGINT, -- расчётно
      courier_id NULL, created_at, updated_at)
suborder_items(id PK, suborder_id FK→suborders, product_id, name_snapshot,
      qty INT, cost_price_minor BIGINT, sale_price_minor BIGINT, markup_percent_snapshot NUMERIC(6,2))

order_status_history(id PK, order_id NULL, suborder_id NULL, from_status, to_status,
      actor, reason NULL, created_at)

promocodes(id PK, code UNIQUE, type ENUM[percent,fixed], value NUMERIC,
      funded_by ENUM[PLATFORM,SHOP], constraints JSONB, valid_from, valid_to,
      usage_limit INT NULL, used_count INT DEFAULT 0, active BOOL)
order_promocodes(order_id FK→orders, promocode_id FK→promocodes, discount_amount_minor BIGINT,
      funded_by, PK(order_id,promocode_id))
```
Все цены в suborder_items — **снимок на момент оформления** (наценка/себестоимость могут потом
измениться). Индексы: `suborders(shop_id, status)`, `orders(client_user_id, created_at DESC)`,
`orders(city_id, status)`.

### 3.4 finance

```
-- payment
payments(id PK, type ENUM[ORDER,MLM_ACCESS], order_id NULL, mlm_account_id NULL,
      client_user_id, amount_minor BIGINT, currency, provider,
      provider_payment_id NULL, status ENUM[pending,succeeded,failed,refunded],
      created_at, updated_at)
payment_events(id PK, payment_id FK→payments, provider_event_id UNIQUE, raw_payload JSONB,
      signature_valid BOOL, received_at)          -- идемпотентность webhook
refunds(id PK, payment_id FK→payments, amount_minor BIGINT, reason,
      status ENUM[requested,succeeded,failed], provider_refund_id NULL, created_at)

-- wallet
wallets(id PK, owner_type ENUM[SHOP,COURIER,PLATFORM], owner_ref, currency,
      balance_minor BIGINT DEFAULT 0, held_minor BIGINT DEFAULT 0, updated_at,
      UNIQUE(owner_type, owner_ref, currency))
wallet_transactions(id PK, wallet_id FK→wallets, direction ENUM[CREDIT,DEBIT],
      amount_minor BIGINT, currency,
      type ENUM[ORDER_SETTLEMENT,COMMISSION,PAYOUT,REFUND,ADJUSTMENT,MLM_BONUS,COURIER_FEE],
      reference_type, reference_id, balance_after_minor BIGINT,
      idempotency_key UNIQUE, created_at)          -- append-only, не UPDATE/DELETE
payout_requests(id PK, wallet_id FK→wallets, amount_minor BIGINT, currency,
      status ENUM[requested,approved,rejected,paid], requested_by, approved_by NULL,
      bank_details JSONB, provider_payout_id NULL, created_at, processed_at NULL)
```
`balance_minor` — денормализованный итог, пересчитывается из ledger; ledger — источник истины.

### 3.5 courier

```
couriers(id PK, user_id UNIQUE, country_id, city_id, status ENUM[active,busy,offline],
      moderation_status ENUM[pending,approved,rejected], rating NUMERIC(2,1) DEFAULT 0,
      vehicle JSONB, created_at, updated_at)
courier_delivery_zones(courier_id FK→couriers, delivery_zone_id, PK(courier_id,delivery_zone_id))

courier_assignments(id PK, suborder_id UNIQUE, courier_id FK→couriers,
      status ENUM[offered,accepted,rejected,picked_up,in_transit,delivered,failed],
      offered_at, accepted_at NULL, delivered_at NULL, failure_reason NULL)
assignment_offers(id PK, suborder_id, courier_id, status ENUM[pending,accepted,rejected,expired],
      offered_at, expires_at)
courier_earnings(id PK, courier_id FK→couriers, assignment_id FK→courier_assignments,
      amount_minor BIGINT, currency, status ENUM[accrued,paid], period, created_at)
```
Индексы: `couriers(city_id, status)`, `courier_assignments(courier_id, status)`,
`assignment_offers(suborder_id, status)`.

### 3.6 mlm

```
mlm_tariffs(id PK, country_id NULL, access_price_minor BIGINT, currency,
      required_purchase_amount_minor BIGINT, purchase_window_days INT, active BOOL)
mlm_referral_rates(id PK, tariff_id FK→mlm_tariffs, level INT, percent NUMERIC(5,2),
      UNIQUE(tariff_id, level))

mlm_accounts(id PK, user_id UNIQUE, client_type DEFAULT 'internal_mlm',
      mlm_user_id UNIQUE, tariff_id FK→mlm_tariffs,
      access_status ENUM[none,access_paid,must_purchase,condition_met,active,expired],
      access_paid_at NULL, activation_deadline NULL,
      required_purchase_amount_minor BIGINT, achieved_purchase_amount_minor BIGINT DEFAULT 0,
      activated_at NULL, bonus_balance_minor BIGINT DEFAULT 0, currency, created_at, updated_at)

mlm_referral_tree(ancestor_id FK→mlm_accounts, descendant_id FK→mlm_accounts, depth INT,
      PK(ancestor_id, descendant_id))            -- closure table, произвольная глубина
mlm_bonus_transactions(id PK, mlm_account_id FK→mlm_accounts,
      source_type ENUM[REFERRAL_PURCHASE,REFERRAL_ACTIVATION], source_ref,
      from_account_id, level INT, percent NUMERIC(5,2), amount_minor BIGINT, currency,
      status ENUM[accrued,reversed], created_at)

mlm_sso_token_log(jti PK, mlm_user_id, issued_at, consumed_at)      -- replay-защита
mlm_sync_outbox(id PK, event_type ENUM[ACTIVATION_STATUS,PURCHASE_REPORTED],
      payload JSONB, status ENUM[pending,sent,failed], attempts INT DEFAULT 0,
      last_attempt_at NULL, created_at)
```

### 3.7 notification

```
notification_templates(id PK, code, channel ENUM[push,sms,telegram], locale,
      subject NULL, body, UNIQUE(code,channel,locale))
notifications(id PK, user_id, channel, template_code, payload JSONB,
      status ENUM[queued,sent,failed], provider_ref NULL, error NULL, created_at, sent_at NULL)
user_notification_prefs(user_id, channel, enabled BOOL, PK(user_id,channel))
device_tokens(id PK, user_id, platform ENUM[ios,android,web], token, created_at)
```

---

## 4. Kafka: топики и события

### 4.1 Соглашения

- **Топик = агрегат-домен**, не событие: `orders`, `payments`, `catalog`, `couriers`,
  `wallet`, `mlm`, `notifications.commands`. Тип события — в конверте.
- **Конверт** (общий, в `common-events`):
  ```json
  {
    "eventId": "uuid",
    "eventType": "orders.OrderCreated",
    "version": 1,
    "occurredAt": "2026-09-03T10:15:30Z",
    "producer": "order-service",
    "traceId": "uuid",
    "payload": { ... }
  }
  ```
- **Ключ партиции** — id корневого агрегата (`orderId`, `shopId`, `mlmUserId`), чтобы события
  одной сущности шли по порядку.
- **Идемпотентность консюмера** — таблица `processed_events(event_id PK, consumer, processed_at)`
  в каждой БД-консюмере; повторный `eventId` игнорируется.
- **Transactional outbox** у продюсера: запись бизнес-данных и события в одну транзакцию в
  таблицу `outbox`, отдельный публикатор (polling или Debezium) шлёт в Kafka. Избегаем
  dual-write.
- **DLT** на каждый consumer group: `<topic>.<group>.DLT`, ретраи с экспонентой, алерт админу.
- Формат — JSON + версия. Schema Registry (Avro/JSON Schema) — опционально, когда контрактов
  станет много.

### 4.2 Каталог событий

| Событие | Топик | Producer | Consumers | Payload (ключевое) |
|---|---|---|---|---|
| `UserRegistered` | `auth` | auth | notification | userId, roles, locale |
| `MlmUserLinked` | `auth` | auth | mlm | userId, mlmUserId, referralCode, uplineMlmUserId |
| `ShopApproved` | `catalog` | catalog | auth (выдать роль SHOP), finance (создать wallet), notification | shopId, ownerUserId, cityId |
| `ShopSuspended` | `catalog` | catalog | order, notification | shopId, reason |
| `ProductPublished` | `catalog` | catalog | search(future), notification, order (read-cache) | productId, shopId, cityId, categoryId, salePriceMinor, currency |
| `ProductRejected` | `catalog` | catalog | notification | productId, shopId, reason |
| `StockChanged` | `catalog` | catalog | order (read-cache) | productId, quantity, reserved |
| `OrderCreated` | `orders` | order | finance (платёжное намерение), catalog (резерв уже сделан синхронно — событие для аудита) | orderId, clientUserId, cityId, suborders[{suborderId, shopId, goodsAmountMinor, costAmountMinor}], totalAmountMinor, currency |
| `OrderCancelled` | `orders` | order | catalog (release stock), finance (компенсация/возврат), courier (снять назначение), notification | orderId, reason, suborderIds[] |
| `SuborderStatusChanged` | `orders` | order | courier, notification, mlm (нет), admin-bff | suborderId, orderId, shopId, fromStatus, toStatus |
| `OrderCompleted` | `orders` | order | mlm (зачесть покупку MLM-клиента), notification | orderId, clientUserId, totalAmountMinor |
| `PaymentInitiated` | `payments` | finance | notification | paymentId, type, orderId/mlmAccountId, amountMinor |
| `OrderPaid` | `payments` | finance | order (→paid), wallet-модуль (расчёт), catalog (списание остатков), courier (запуск подбора), mlm (если плательщик MLM — зачесть), notification | paymentId, orderId, amountMinor, currency, paidAt |
| `PaymentFailed` | `payments` | finance | order (→payment_failed), catalog (release), notification | paymentId, orderId, reason |
| `PaymentRefunded` | `payments` | finance | wallet, order, notification | paymentId, orderId, amountMinor |
| `MlmAccessPaid` | `payments` | finance | mlm (старт окна активации), notification | paymentId, mlmUserId, tariffId, amountMinor, paidAt |
| `WalletCredited` | `wallet` | finance | notification, admin-bff | walletId, ownerType, ownerRef, amountMinor, type, referenceId |
| `WalletDebited` | `wallet` | finance | notification, admin-bff | walletId, amountMinor, type, referenceId |
| `PayoutRequested` | `wallet` | finance | notification (админу) | payoutId, walletId, amountMinor |
| `PayoutApproved` / `PayoutPaid` | `wallet` | finance | notification | payoutId, amountMinor, processedAt |
| `CourierRegistered` | `couriers` | courier | notification (админу) | courierId, cityId |
| `CourierAssigned` | `couriers` | courier | order (проставить courier_id), notification | suborderId, courierId |
| `CourierAccepted` | `couriers` | courier | order, notification | suborderId, courierId |
| `DeliveryPickedUp` / `DeliveryInTransit` / `DeliveryCompleted` / `DeliveryFailed` | `couriers` | courier | order (статусы suborder), notification, finance (на Completed — начисление курьеру) | suborderId, courierId, at, failureReason? |
| `NoCourierAvailable` | `couriers` | courier | notification (админу), order (по решению R1 — блок) | suborderId, cityId |
| `MlmAccessActivated` | `mlm` | mlm | notification | mlmUserId, deadline, requiredAmountMinor |
| `MlmPurchaseCounted` | `mlm` | mlm | notification | mlmUserId, orderId, achievedAmountMinor, requiredAmountMinor |
| `MlmConditionMet` | `mlm` | mlm | integration-модуль (outbox→внешний MLM), notification | mlmUserId, achievedAmountMinor, orderIds[] |
| `MlmActivated` | `mlm` | mlm | notification | mlmUserId, activatedAt |
| `MlmExpired` | `mlm` | mlm | integration-модуль, notification | mlmUserId, deadline |
| `MlmReferralBonusAccrued` | `mlm` | mlm | notification | mlmAccountId, fromAccountId, level, amountMinor |
| `SendNotification` (команда) | `notifications.commands` | любой | notification | userId, templateCode, channel?, payload |

---

## 5. Контракт SSO-интеграции с MLM-бэком

### 5.1 Принцип: антикоррупционный слой

Внутри `mlm-service` — модуль `integration` с интерфейсами, **не зависящими** от реального
протокола MLM-бэка:

```java
interface MlmIdentityProvider {           // разбор входящего токена
    MlmIdentity verifyAndExtract(String rawToken);   // подпись, exp, aud, jti
}
interface MlmSyncClient {                  // обратная синхронизация
    void reportActivationStatus(MlmActivationStatus status);
    void reportPurchase(MlmPurchase purchase);
}
record MlmIdentity(String mlmUserId, String referralCode, String uplineMlmUserId,
                   String accessStatus, String email, String phone, String fullName,
                   String locale, String countryCode, String city) {}
```

Реализации: `JwtHmacMlmIdentityProvider`, `JwtRsaMlmIdentityProvider`, `OidcMlmIdentityProvider`.
Пока контракт с командой MLM не согласован — работаем против `mock-mlm` (см. раздел 7) по
описанному ниже дефолту; смена протокола = новая реализация интерфейса, остальные сервисы не
трогаем.

### 5.2 Дефолтный контракт (до согласования)

**Рекомендация по способу:** если MLM-бэк может дать OIDC — берём OIDC (`id_token`, discovery,
JWKS). Если нет — **подписанный JWT RS256** (асимметрия: MLM-бэк подписывает приватным ключом,
маркетплейс проверяет по JWKS/публичному ключу; HMAC-общий-секрет — только как крайний
вариант).

**Входящий токен (SSO).** Пользователь из MLM-системы переходит на маркетплейс со
`?sso_token=<JWT>` (или заголовком). Токен **короткоживущий (60–120 с), одноразовый**, меняется
на внутреннюю сессию.

- Заголовок: `alg=RS256`, `kid`.
- Claims:
  | claim | обяз. | описание |
  |---|---|---|
  | `iss` | да | идентификатор MLM-бэка |
  | `aud` | да | `"green-eco-mall"` |
  | `sub` / `mlm_user_id` | да | стабильный внешний ID пользователя |
  | `jti` | да | одноразовость (лог в `mlm_sso_token_log`) |
  | `iat`, `exp` | да | срок ≤ 120 с, допуск по часам ≤ 60 с |
  | `referral_code` | нет | личный реф-код пользователя |
  | `upline_id` / `upline_mlm_user_id` | нет | реферер (для построения дерева) |
  | `access_status` | нет | `none / access_paid / active / expired` на стороне MLM |
  | `email`, `phone`, `full_name` | нет | префилл профиля |
  | `locale`, `country_code`, `city` | нет | префилл гео |

- Валидация: подпись по JWKS (`GET {mlm_issuer}/.well-known/jwks.json`, кэш с TTL),
  `iss`/`aud`/`exp`/`nbf`, `jti` не встречался.
- Успех → `POST /internal/mlm/sso/resolve` (auth → mlm): найти `mlm_account` по `mlm_user_id`,
  иначе создать `user` (роль `CLIENT_MLM`, `client_type=internal_mlm`) + `mlm_account`
  (+ ветка дерева по `upline_id`). auth выдаёт внутренний access/refresh JWT.

**Обратная синхронизация (маркетплейс → MLM-бэк).** Через `mlm_sync_outbox`, ретраи с
экспонентой, HMAC-подпись тела:

- `POST {mlm_base}/api/marketplace/activation`
  ```json
  { "mlmUserId": "...", "status": "CONDITION_MET|ACTIVATED|EXPIRED",
    "achievedAmountMinor": 1500000, "currency": "KGS",
    "occurredAt": "...", "marketplaceOrderIds": ["..."] }
  ```
- `POST {mlm_base}/api/marketplace/purchases`
  ```json
  { "mlmUserId": "...", "orderId": "...", "amountMinor": 250000, "currency": "KGS",
    "occurredAt": "...", "itemsSummary": [{ "category": "...", "amountMinor": 250000 }] }
  ```
- Заголовки: `X-Signature: hmac-sha256(secret, timestamp + "." + body)`, `X-Timestamp`,
  `X-Idempotency-Key: {outboxId}`.

**Источник истины (R9):** маркетплейс считает «покупка на X за Y» и шлёт `activation`;
реферальные бонусы — на стороне MLM-бэка (тогда нужны `purchases`), либо на нашей (тогда
`purchases` не обязателен). **Согласовать с командой MLM.**

**Открытые вопросы к команде MLM (R10, R11):** OIDC или подписанный JWT; RS256/JWKS или
HMAC-секрет; точный список claim'ов; эндпоинты и аутентификация обратных вызовов; кто считает
активацию; нужны ли им наши `purchases`.

---

## 6. Monorepo vs multi-repo

**Рекомендация: monorepo, Maven multi-module, один Git-репозиторий, один CI-пайплайн.**

Причины для одного разработчика: атомарные рефакторинги контрактов событий (меняешь DTO
события — компилятор сразу показывает всех консюмеров), единый BOM версий, один `.gitlab-ci.yml`,
одна настройка окружения. Минусы multi-repo (независимые релизы, отдельные владельцы) на этом
масштабе не окупаются.

Фактическая структура (собрано, `./mvnw compile` — зелёный):

```
green-eco-mall/
├── pom.xml                      # parent: packaging=pom, java 21, BOM наших модулей + testcontainers
├── common/
│   ├── common-domain/           # Money, GeoRef, DomainException
│   ├── common-events/           # EventEnvelope, Topics, EventTypes, DomainEventPublisher, payload/*
│   ├── common-security/         # Roles, GatewayHeaders, AuthPrincipal
│   └── common-web/              # PageResponse, RestExceptionHandler (ProblemDetail / RFC 7807)
├── services/
│   ├── auth-service/            # порт 8081, БД auth
│   ├── catalog-service/         # 8082, БД catalog
│   ├── order-service/           # 8083, БД orders
│   ├── finance-service/         # 8084, БД finance
│   ├── courier-service/         # 8085, БД courier
│   ├── mlm-service/             # 8086, БД mlm
│   ├── notification-service/    # 8087, БД notification
│   └── api-gateway/             # 8080, webflux (Spring Cloud Gateway — по TODO в pom)
├── infra/
│   ├── docker-compose.infra.yml # postgres/kafka(KRaft)/kafka-ui/redis/mailhog/mock-mlm
│   ├── docker-compose.yml       # инфра + все сервисы
│   ├── postgres/init-databases.sh
│   └── mock-mlm/                # заглушка внешнего MLM-бэка (SSO-токен + приём обратных вызовов)
├── docs/ARCHITECTURE.md
└── .gitlab-ci.yml
```

Каждый `*-service` — самостоятельное Spring Boot приложение (свой `@SpringBootApplication`,
`application.yml`, Flyway-миграции в `src/main/resources/db/migration`, в каждой БД — таблицы
`processed_events` и `outbox` из `V001__baseline.sql`). Контракты между сервисами — события в
`common-events` и REST через `RestClient`-интерфейсы.

Переход к multi-repo — только когда появится вторая команда/подрядчик на конкретный сервис.

---

## 7. Docker Compose и GitLab CI/CD

### 7.1 docker-compose.infra.yml (для повседневной разработки)

Поднимаешь инфраструктуру в Docker, сервисы запускаешь из IDE.

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: gem
      POSTGRES_PASSWORD: gem
    ports: ["5432:5432"]
    volumes:
      - ./postgres/init-databases.sh:/docker-entrypoint-initdb.d/init.sh
      - pgdata:/var/lib/postgresql/data
    # init.sh создаёт БД: auth, catalog, order, finance, courier, mlm, notification

  kafka:
    image: bitnami/kafka:3.7          # KRaft, без Zookeeper
    environment:
      KAFKA_CFG_NODE_ID: "1"
      KAFKA_CFG_PROCESS_ROLES: "controller,broker"
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: "1@kafka:9093"
      KAFKA_CFG_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_CFG_ADVERTISED_LISTENERS: "PLAINTEXT://localhost:9092"
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"
    ports: ["9092:9092"]

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
    ports: ["8085:8080"]
    depends_on: [kafka]

  redis:
    image: redis:7
    ports: ["6379:6379"]

  mailhog:                            # ловим email/OTP локально
    image: mailhog/mailhog
    ports: ["8025:8025", "1025:1025"]

  mock-mlm:                           # заглушка внешнего MLM-бэка
    build: ./mock-mlm
    ports: ["9100:9100"]

volumes:
  pgdata:
```

### 7.2 docker-compose.yml (полный прогон)

`extends` от `infra` + все сервисы (образы собираются через **Jib** или Spring Boot buildpacks,
без Dockerfile), каждому — свой `SPRING_DATASOURCE_URL` на свою БД, `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092`,
`depends_on` с `condition: service_healthy` (Actuator `/actuator/health`).

Порты: gateway `8080`, auth `8081`, catalog `8082`, order `8083`, finance `8084`,
courier `8085`, mlm `8086`, notification `8087`.

### 7.3 .gitlab-ci.yml (скелет)

```yaml
stages: [build, test, package, deploy]

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"

cache:
  key: "$CI_COMMIT_REF_SLUG"
  paths: [".m2/repository"]

build:
  stage: build
  image: eclipse-temurin:21-jdk
  script: ["./mvnw -B -T1C clean compile"]

test:
  stage: test
  image: eclipse-temurin:21-jdk
  services: ["docker:dind"]           # Testcontainers: postgres/kafka/redis
  variables:
    DOCKER_HOST: "tcp://docker:2375"
    TESTCONTAINERS_RYUK_DISABLED: "true"
  script: ["./mvnw -B verify"]
  artifacts:
    reports:
      junit: "**/target/surefire-reports/TEST-*.xml"

package:
  stage: package
  image: eclipse-temurin:21-jdk
  script:
    # Jib собирает и пушит образы только изменившихся модулей
    - ./mvnw -B -pl services/auth-service,services/catalog-service,... -am
        com.google.cloud.tools:jib-maven-plugin:build
        -Dimage=$CI_REGISTRY_IMAGE/$MODULE:$CI_COMMIT_SHORT_SHA
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'

deploy:staging:
  stage: deploy
  script: ["./deploy.sh staging $CI_COMMIT_SHORT_SHA"]
  environment: { name: staging }
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'

deploy:prod:
  stage: deploy
  script: ["./deploy.sh prod $CI_COMMIT_SHORT_SHA"]
  environment: { name: production }
  when: manual
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
```

Позже: `rules:changes` на пути модулей, чтобы пересобирать только затронутые сервисы; `sonar`
в стадии `test`; сканирование образов (Trivy).

---

## 8. Риски и открытые вопросы

### 8.1 Технические риски

| # | Риск | Смягчение |
|---|---|---|
| R0 | ~~Расхождение стека~~ **закрыто**: Java 21 + Spring Boot 4.1.1, `java.version=21` в parent pom | Остаётся один хвост: Spring Cloud Gateway пока без релиза под Boot 4.1.1 — api-gateway собран на webflux-заглушке, маршрутизацию подключаем по TODO в его `pom.xml` (либо gateway отдельным модулем на Boot 3.3.x + Spring Cloud 2024.x) |
| R1 | **Распределённые транзакции** заказ→оплата→остатки→кошельки | Сага-хореография через события + transactional outbox + идемпотентные консюмеры + компенсации (отмена suborder, release остатков, refund). Никаких 2PC |
| R2 | **Пересортировка/перепродажа остатков** при гонках | Резерв условным `UPDATE ... WHERE quantity - reserved >= :qty`; блокировка строки; таймаут неоплаченного резерва |
| R3 | **Безопасность и идемпотентность webhook** (эквайринг, MLM) | Проверка подписи; дедуп по `provider_event_id`; хранить сырой payload в `payment_events` |
| R4 | **Деньги** | Целые минорные единицы + `currency` везде; ledger двойной записи append-only; запрет `float`; аудит всех финопераций |
| R5 | **PCI DSS** | Не хранить данные карт. Только hosted payment page / токенизация провайдера |
| R6 | **Мультиязычность / мультивалютность** позже | Ключи локализации с 1-го дня; `currency` в каждой денежной таблице даже при одной валюте в MVP |
| R7 | **Kafka: порядок и дубликаты** | Ключ партиции = id агрегата; консюмеры идемпотентны; DLT + алерты |
| R8 | **Наблюдаемость** | `traceId` в конверте события с 1-го дня; Actuator + Micrometer/Prometheus; централизованные логи (ELK/Loki) до первого прод-инцидента |
| R9 | ~~Auth: своё vs Keycloak~~ **решено: своё** | Spring Authorization Server внутри `auth-service` — стандартные OAuth2/OIDC-эндпоинты и JWKS как библиотека, без отдельного компонента; регистрация/OTP/модерация/MLM-provisioning — свой код. Выпуск токенов спрятать за интерфейсом на случай будущей миграции на Keycloak |
| R10 | **Единая точка отказа — api-gateway** | Stateless, несколько реплик за LB; таймауты и circuit breaker (Resilience4j) на upstream |
| R11 | **Юридический риск MLM**: «входной билет» + обязательная покупка + реф-бонусы может трактоваться как финансовая пирамида | Вынести на юр-проверку по каждой стране подключения до запуска MLM-ветки. На архитектуру не влияет, на запуск — критично |
| R12 | **Мок MLM-бэка ≠ реальный контракт** | Антикоррупционный слой (раздел 5.1); реальный протокол = новая реализация интерфейса |

### 8.2 Открытые вопросы ТЗ — предлагаемые решения

| # | Вопрос | Предложение (по умолчанию, если не переопределишь) |
|---|---|---|
| 1 | Один курьер на весь мультимагазинный заказ или на каждый suborder? | **На каждый suborder** (MVP). Проще подбор и расчёт вознаграждения. Батч-маршрут «один курьер на все suborders города» — фаза 2 |
| 2 | Что с MLM-клиентом при непокупке в срок? | `access_status = expired` → блок MLM-функций (бонусы, витрина MLM), аккаунт сохраняется. Напоминания за 3 и 1 день. Повторная оплата доступа перезапускает окно. Всё — конфигурируемо в тарифе |
| 3 | Сколько уровней рефералки и % на каждом? | Структура таблиц — на **произвольную глубину** (closure table). Дефолт для запуска: **3 уровня, 7% / 3% / 1%**, задаётся в `mlm_referral_rates` на тариф |
| 4 | Наценка по странам/городам? | **Да.** `markup_rules` со `scope` включает `REGION` и `CATEGORY_REGION`; резолв по приоритету. Дефолт — `GLOBAL` |
| 5 | Единая валюта платформы или разные? | **Единая валюта на MVP** (например, KGS). `currency` хранится везде. Мультивалютность + конвертация (FX-курсы, расчёт кошельков) — отдельная фаза |
| 6 | Один эквайринг-провайдер или несколько? | Интерфейс `PaymentProvider`, **один провайдер в MVP**, подключаемые по стране — позже. Webhook-эндпоинт параметризован `{provider}` |
| 7 | Кто финансирует промокоды и как это влияет на наценку? | Поле `funded_by`. **Платформа-финансирует** → уменьшается `platform_commission`. **Магазин-финансирует** → уменьшается выплата магазину (`cost_amount`). Наценка товара **не пересчитывается** задним числом |
| 8 | Вывод средств — вручную или через API эквайера? | **Вручную (подтверждение админом)** в MVP — безопаснее, есть аудит. `payout_requests` со стейт-машиной готов и под авто-выплаты через API — позже |
| 9 | Источник истины по активации MLM | **Маркетплейс считает** «покупка на X в срок Y» (владеет заказами) и пушит статус в MLM-бэк. MLM-бэк владеет оплатой доступа и реф-выплатами. Подтвердить с командой MLM |
| 10 | Способ SSO с MLM-бэком | **OIDC при возможности**, иначе подписанный **JWT RS256 + JWKS** (HMAC-секрет — крайний вариант). Обёрнуто в ACL (`MlmIdentityProvider`). Согласовать с командой MLM |
| 11 | Какие поля отдаёт MLM при входе / что отдаём обратно | Входящие: `mlm_user_id` (обяз.), `referral_code`, `upline_id`, `access_status`, опц. `email/phone/full_name/locale/country_code/city`. Обратно: статус активации (`activation`) + опц. покупки (`purchases`, если реф-бонусы считает MLM). Зафиксировать в контракт-доке с командой MLM |

---

## 9. Порядок реализации

Денежный путь MVP: **auth → catalog → order → finance(payment) → finance(wallet) → courier**.
MLM — отдельная фаза после того, как внешний клиент может купить и получить доставку.

| Этап | Что | Результат |
|---|---|---|
| 0 | ✅ **Сделано**: каркас monorepo (parent pom, `common-*`, 7 сервисов + gateway), `docker-compose.infra` + `docker-compose.yml`, `.gitlab-ci.yml`, `mock-mlm`, Flyway-baseline с `processed_events` + `outbox`, конверт события и payload-контракты в `common-events`, actuator health/prometheus. Осталось: реальная реализация `DomainEventPublisher` (outbox-публикатор) + трейсинг (Micrometer Tracing) — добавляется в этап 1 | Скелет собирается (`./mvnw compile`) |
| 1 | **auth-service** + **api-gateway**: users/roles, регистрация (email+пароль), login, JWT + refresh с ротацией, OTP-заглушка (в MailHog), RBAC, фильтр JWT в гейтвее | Пользователь регистрируется и получает токен; гейтвей пускает по ролям |
| 2 | **catalog-service**: гео-справочники (админ CRUD), категории, регистрация+модерация магазинов, загрузка+модерация+публикация товаров, `markup_rules` + резолв цены, гео-фильтрованная витрина, Redis-кэш цены. События `ShopApproved`, `ProductPublished` | Гость видит витрину своего города; магазин грузит товар, админ публикует с наценкой |
| 3 | **order-service**: мультивендорная корзина (один город), `checkout` → `order` + `suborders`, снапшоты цен, синхронный резерв остатков в catalog, стейт-машина статусов, история. Событие `OrderCreated` | Клиент собирает корзину из разных магазинов и оформляет заказ |
| 4 | **finance / payment**: платёжное намерение по `OrderCreated`, mock-эквайринг + webhook (подпись, идемпотентность), `OrderPaid` / `PaymentFailed`, отдельный `POST /payments/mlm-access` → `MlmAccessPaid`. Consumer в order (→paid) и catalog (списание остатков) | Заказ оплачивается онлайн, остатки списываются, статус едет |
| 5 | **finance / wallet**: кошельки (создаются по `ShopApproved`), расчёт распределения по `OrderPaid` (ledger двойной записи, комиссия платформы), `WalletCredited`, заявки на вывод + подтверждение админом | Деньги за заказ раскладываются по кошелькам; магазин видит баланс и подаёт на вывод |
| 6 | **courier-service**: регистрация+модерация, привязка к городу/зонам, подбор по `OrderPaid` (один курьер на suborder), стейт-машина доставки → события в order, заработок. Fallback `NoCourierAvailable` | Оплаченный suborder назначается курьеру, статусы доставки доходят до клиента |
| 7 | **notification-service**: consumer на все существенные события + `notifications.commands`, шаблоны, каналы (для начала — email через MailHog + лог), настройки пользователя | Клиент/магазин/курьер получают уведомления о статусах |
| 8 | **mlm-service (core)**: `mlm_accounts`, окно активации по `MlmAccessPaid`, зачёт покупок по `OrderPaid`/`OrderCompleted`, `MlmConditionMet` → `MlmActivated`, планировщик просрочек → `MlmExpired`, реф-дерево (для начала 1 уровень), начисление бонусов | Внутренний клиент оплачивает доступ, покупает в срок, активируется, капают реф-бонусы |
| 9 | **mlm-service (integration)**: `MlmIdentityProvider` (RS256/JWKS против `mock-mlm`), `POST /auth/sso/mlm` → `/internal/mlm/sso/resolve`, `mlm_sync_outbox` + обратные webhook в `mock-mlm`, replay-защита по `jti` | Клиент заходит из MLM-системы по SSO, статус активации улетает обратно |
| 10 | **Админка / BFF**: агрегация в гейтвее, дашборд (выручка, комиссии, конверсия, активные/неактивные MLM, топ-магазины/товары), промокоды, возвраты/отмены | Админ управляет платформой из одной панели |
| 11 | **Харденинг**: DLT-обработка, интеграционные тесты Testcontainers на сагу заказа, метрики/алерты, `rules:changes` в CI. Опционально: вынос первого сервиса из `app`, Elasticsearch/PostGIS при росте | Готовность к нагрузке и к росту команды |

**Фокус для рабочего MVP:** этапы 1–6 (внешний клиент: витрина → корзина → онлайн-оплата →
распределение по кошелькам → доставка) + этап 7 (уведомления). Этапы 8–9 (MLM) — самые
рискованные из-за внешнего несогласованного контракта; их можно вести параллельно против
`mock-mlm`, но не блокировать ими запуск основной части.
