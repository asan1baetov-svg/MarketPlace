-- notification-service — шаблоны, журнал отправок, настройки каналов, push-токены,
-- read-model получателей. processed_events и outbox уже созданы в V001__baseline.sql.

create table notification_templates (
    id      uuid primary key default gen_random_uuid(),
    code    varchar(64)   not null,
    channel varchar(16)   not null,   -- PUSH | SMS | TELEGRAM
    locale  varchar(8)    not null,
    subject varchar(200),
    body    varchar(2000) not null,
    constraint uq_templates_code_channel_locale unique (code, channel, locale)
);

create table notifications (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid,                       -- null = администраторы
    channel       varchar(16)  not null,
    template_code varchar(64)  not null,
    payload       jsonb        not null,
    rendered_text varchar(2000),
    status        varchar(16)  not null default 'QUEUED',  -- QUEUED | SENT | FAILED | SKIPPED
    provider_ref  varchar(200),
    error         varchar(1000),
    created_at    timestamptz  not null default now(),
    sent_at       timestamptz
);
create index idx_notifications_user on notifications (user_id, created_at);

create table user_notification_prefs (
    user_id uuid        not null,
    channel varchar(16) not null,
    enabled boolean     not null,
    primary key (user_id, channel)
);

create table device_tokens (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid         not null,
    platform   varchar(16)  not null,         -- IOS | ANDROID | WEB
    token      varchar(500) not null unique,
    created_at timestamptz  not null default now()
);
create index idx_device_tokens_user on device_tokens (user_id);

create table recipient_links (
    ref_type varchar(16)  not null,           -- ORDER | SHOP | COURIER | WALLET | PAYOUT | MLM_USER
    ref_id   varchar(128) not null,
    user_id  uuid         not null,
    primary key (ref_type, ref_id)
);

-- ─── стартовые шаблоны (ru) ─────────────────────────────────────────────────
insert into notification_templates (code, channel, locale, subject, body) values
 ('USER_WELCOME',           'PUSH', 'ru', 'Грин Эко Молл', 'Добро пожаловать в Грин Эко Молл!'),
 ('ORDER_CREATED',          'PUSH', 'ru', 'Заказ оформлен', 'Заказ {{orderId}} на сумму {{amount}} оформлен. Ожидаем оплату.'),
 ('ORDER_STATUS_CHANGED',   'PUSH', 'ru', 'Статус заказа', 'Заказ {{orderId}}: статус «{{status}}».'),
 ('ORDER_CANCELLED',        'PUSH', 'ru', 'Заказ отменён', 'Заказ {{orderId}} отменён. {{reason}}'),
 ('ORDER_COMPLETED',        'PUSH', 'ru', 'Заказ выполнен', 'Заказ {{orderId}} доставлен. Спасибо за покупку!'),
 ('SHOP_APPROVED',          'PUSH', 'ru', 'Магазин одобрен', 'Ваш магазин прошёл модерацию и может добавлять товары.'),
 ('SHOP_SUSPENDED',         'PUSH', 'ru', 'Магазин приостановлен', 'Работа магазина приостановлена. {{reason}}'),
 ('SHOP_NEW_SUBORDER',      'PUSH', 'ru', 'Новый заказ', 'Оплачен новый заказ {{suborderId}} — примите и соберите его.'),
 ('PRODUCT_PUBLISHED',      'PUSH', 'ru', 'Товар опубликован', 'Товар {{productId}} опубликован на витрине.'),
 ('PRODUCT_REJECTED',       'PUSH', 'ru', 'Товар отклонён', 'Товар {{productId}} отклонён: {{reason}}'),
 ('COURIER_NEW_OFFER',      'PUSH', 'ru', 'Новая доставка', 'Вам предложена доставка {{suborderId}}. Примите заказ в приложении.'),
 ('WALLET_CREDITED',        'PUSH', 'ru', 'Зачисление', 'На кошелёк зачислено {{amount}} ({{type}}).'),
 ('PAYOUT_STATUS_CHANGED',  'PUSH', 'ru', 'Вывод средств', 'Заявка на вывод {{amount}}: {{status}}.'),
 ('MLM_ACCESS_ACTIVATED',   'PUSH', 'ru', 'Доступ оплачен', 'Доступ оплачен. Совершите покупки на {{required}} до {{deadline}}.'),
 ('MLM_ACTIVATED',          'PUSH', 'ru', 'Аккаунт активирован', 'Условие активации выполнено — ваш аккаунт активен.'),
 ('MLM_EXPIRED',            'PUSH', 'ru', 'Срок активации истёк', 'Срок активации истёк {{deadline}}. Оплатите доступ повторно.'),
 ('MLM_DEADLINE_REMINDER',  'PUSH', 'ru', 'Напоминание', 'До окончания срока активации осталось {{daysLeft}} дн. Нужно ещё {{remaining}}.'),
 ('ADMIN_COURIER_REGISTERED','TELEGRAM', 'ru', null, 'Новая заявка курьера {{courierId}} (город {{cityId}}).'),
 ('ADMIN_NO_COURIER',       'TELEGRAM', 'ru', null, '⚠️ Нет свободных курьеров для {{suborderId}} в городе {{cityId}}.'),
 ('ADMIN_PAYOUT_REQUESTED', 'TELEGRAM', 'ru', null, 'Заявка на вывод {{payoutId}} на {{amount}} ждёт подтверждения.');
