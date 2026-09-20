-- finance-service — payment (эквайринг + webhook + возвраты) и wallet (кошельки, ledger, выплаты).
-- Значения enum хранятся строкой в верхнем регистре (EnumType.STRING).
-- processed_events и outbox уже созданы в V001__baseline.sql.

-- ─── payment ────────────────────────────────────────────────────────────────
create table payments (
    id                  uuid primary key default gen_random_uuid(),
    type                varchar(16)  not null,          -- ORDER | MLM_ACCESS
    order_id            uuid,
    mlm_user_id         varchar(128),
    mlm_tariff_id       uuid,
    client_user_id      uuid         not null,
    amount_minor        bigint       not null,
    currency            varchar(3)   not null,
    provider            varchar(40)  not null,
    provider_payment_id varchar(200),
    status              varchar(16)  not null default 'PENDING',
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now()
);
create index idx_payments_order on payments (order_id);
create index idx_payments_client on payments (client_user_id);
create unique index uq_payments_provider_payment_id on payments (provider_payment_id)
    where provider_payment_id is not null;

create table payment_events (
    id                uuid primary key default gen_random_uuid(),
    payment_id        uuid,
    provider_event_id varchar(200) not null unique,
    raw_payload       jsonb        not null,
    signature_valid   boolean      not null,
    received_at       timestamptz  not null default now()
);
create index idx_payment_events_payment on payment_events (payment_id);

create table refunds (
    id                 uuid primary key default gen_random_uuid(),
    payment_id         uuid         not null references payments(id),
    amount_minor       bigint       not null,
    reason             varchar(300) not null,
    status             varchar(16)  not null default 'REQUESTED',
    provider_refund_id varchar(200),
    created_at         timestamptz  not null default now()
);
create index idx_refunds_payment on refunds (payment_id);

-- ─── wallet ─────────────────────────────────────────────────────────────────
create table wallets (
    id            uuid primary key default gen_random_uuid(),
    owner_type    varchar(16)  not null,               -- SHOP | COURIER | PLATFORM
    owner_ref     varchar(128) not null,
    currency      varchar(3)   not null,
    balance_minor bigint       not null default 0,
    held_minor    bigint       not null default 0,
    updated_at    timestamptz  not null default now(),
    constraint uq_wallets_owner unique (owner_type, owner_ref, currency)
);

create table wallet_transactions (
    id                  uuid primary key default gen_random_uuid(),
    wallet_id           uuid         not null references wallets(id),
    direction           varchar(8)   not null,          -- CREDIT | DEBIT
    amount_minor        bigint       not null,
    currency            varchar(3)   not null,
    type                varchar(24)  not null,          -- ORDER_SETTLEMENT | COMMISSION | PAYOUT | REFUND | ...
    reference_type      varchar(40),
    reference_id        varchar(128),
    balance_after_minor bigint       not null,
    idempotency_key     varchar(200) not null unique,
    created_at          timestamptz  not null default now()
);
create index idx_wtx_wallet on wallet_transactions (wallet_id, created_at);
create index idx_wtx_reference on wallet_transactions (reference_type, reference_id);

create table payout_requests (
    id                 uuid primary key default gen_random_uuid(),
    wallet_id          uuid         not null references wallets(id),
    amount_minor       bigint       not null,
    currency           varchar(3)   not null,
    status             varchar(16)  not null default 'REQUESTED',
    requested_by       varchar(128) not null,
    approved_by        varchar(128),
    bank_details       jsonb        not null,
    provider_payout_id varchar(200),
    created_at         timestamptz  not null default now(),
    processed_at       timestamptz
);
create index idx_payouts_wallet on payout_requests (wallet_id);

-- ─── settlement plan (снят из orders.OrderCreated, исполняется при payments.OrderPaid) ──
create table order_settlement_plans (
    order_id   uuid primary key,
    currency   varchar(3)  not null,
    lines      jsonb       not null,
    status     varchar(12) not null default 'PENDING',  -- PENDING | SETTLED | REVERSED
    created_at timestamptz not null default now()
);
