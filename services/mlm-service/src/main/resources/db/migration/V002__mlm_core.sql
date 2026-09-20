-- mlm-service — тарифы доступа, аккаунты и окно активации, реферальное дерево (closure table),
-- заказы MLM-клиентов, бонусы, outbox обратной синхронизации во внешний MLM-бэк.
-- processed_events и outbox (Kafka) уже созданы в V001__baseline.sql.

create table mlm_tariffs (
    id                             uuid primary key default gen_random_uuid(),
    name                           varchar(100) not null,
    country_id                     uuid,
    access_price_minor             bigint       not null,
    currency                       varchar(3)   not null,
    required_purchase_amount_minor bigint       not null,
    purchase_window_days           integer      not null,
    is_default                     boolean      not null default false,
    active                         boolean      not null default true
);
create unique index uq_mlm_tariffs_single_default on mlm_tariffs (is_default) where is_default and active;

create table mlm_referral_rates (
    id        uuid primary key default gen_random_uuid(),
    tariff_id uuid         not null references mlm_tariffs(id) on delete cascade,
    level     integer      not null,
    percent   numeric(5,2) not null,
    constraint uq_referral_rates_tariff_level unique (tariff_id, level)
);

create table mlm_accounts (
    id                             uuid primary key default gen_random_uuid(),
    user_id                        uuid         not null unique,
    mlm_user_id                    varchar(128) not null unique,
    referral_code                  varchar(64),
    upline_mlm_user_id             varchar(128),
    tariff_id                      uuid references mlm_tariffs(id),
    access_status                  varchar(16)  not null default 'NONE', -- NONE | MUST_PURCHASE | ACTIVE | EXPIRED
    external_status                varchar(32),
    blocked                        boolean      not null default false,
    access_paid_at                 timestamptz,
    activation_deadline            timestamptz,
    required_purchase_amount_minor bigint       not null default 0,
    achieved_purchase_amount_minor bigint       not null default 0,
    activated_at                   timestamptz,
    last_reminder_days             integer,
    bonus_balance_minor            bigint       not null default 0,
    currency                       varchar(3)   not null,
    created_at                     timestamptz  not null default now(),
    updated_at                     timestamptz  not null default now()
);
create index idx_mlm_accounts_status_deadline on mlm_accounts (access_status, activation_deadline);
create index idx_mlm_accounts_upline on mlm_accounts (upline_mlm_user_id);

create table mlm_referral_tree (
    ancestor_id   uuid    not null references mlm_accounts(id),
    descendant_id uuid    not null references mlm_accounts(id),
    depth         integer not null,
    primary key (ancestor_id, descendant_id)
);
create index idx_mlm_tree_descendant on mlm_referral_tree (descendant_id, depth);

create table mlm_orders (
    order_id       uuid primary key,
    mlm_account_id uuid        not null references mlm_accounts(id),
    amount_minor   bigint      not null,
    currency       varchar(3)  not null,
    status         varchar(12) not null default 'CREATED',  -- CREATED | COUNTED | PAID | REVERSED
    paid_at        timestamptz,
    created_at     timestamptz not null default now()
);
create index idx_mlm_orders_account on mlm_orders (mlm_account_id, created_at);

create table mlm_bonus_transactions (
    id              uuid primary key default gen_random_uuid(),
    mlm_account_id  uuid         not null references mlm_accounts(id),
    source_type     varchar(24)  not null,
    source_ref      varchar(128) not null,
    from_account_id uuid         not null,
    level           integer      not null,
    percent         numeric(5,2) not null,
    amount_minor    bigint       not null,
    currency        varchar(3)   not null,
    status          varchar(12)  not null default 'ACCRUED',
    created_at      timestamptz  not null default now(),
    constraint uq_bonus_source_account unique (source_ref, mlm_account_id)
);
create index idx_mlm_bonus_account on mlm_bonus_transactions (mlm_account_id, created_at);

create table mlm_sync_outbox (
    id              uuid primary key default gen_random_uuid(),
    event_type      varchar(24)   not null,   -- ACTIVATION_STATUS | PURCHASE_REPORTED
    payload         jsonb         not null,
    status          varchar(12)   not null default 'PENDING',
    attempts        integer       not null default 0,
    next_attempt_at timestamptz   not null default now(),
    last_attempt_at timestamptz,
    last_error      varchar(1000),
    created_at      timestamptz   not null default now()
);
create index idx_mlm_sync_pending on mlm_sync_outbox (status, next_attempt_at);

-- ─── дефолтный тариф (значения — заглушки до решения бизнеса, меняются в админке) ──
-- Входной билет 10 000 KGS (ТЗ §4.2), условие — покупки на 5 000 KGS за 30 дней;
-- реферальные ставки 7% / 3% / 1% (docs/ARCHITECTURE.md §8.2 вопрос 3), применяются
-- только при mlm.local-bonuses-enabled=true.
insert into mlm_tariffs (id, name, access_price_minor, currency, required_purchase_amount_minor,
                         purchase_window_days, is_default, active)
values ('00000000-0000-0000-0000-000000000001', 'Базовый', 1000000, 'KGS', 500000, 30, true, true);

insert into mlm_referral_rates (tariff_id, level, percent) values
 ('00000000-0000-0000-0000-000000000001', 1, 7.00),
 ('00000000-0000-0000-0000-000000000001', 2, 3.00),
 ('00000000-0000-0000-0000-000000000001', 3, 1.00);
