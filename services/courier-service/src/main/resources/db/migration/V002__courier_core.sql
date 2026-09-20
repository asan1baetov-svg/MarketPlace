-- courier-service — курьеры, зоны, задачи доставки (по одной на suborder), офферы, заработок.
-- processed_events и outbox уже созданы в V001__baseline.sql.

create table couriers (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid         not null unique,
    country_id        uuid         not null,
    city_id           uuid         not null,
    status            varchar(16)  not null default 'OFFLINE',  -- ACTIVE | BUSY | OFFLINE
    moderation_status varchar(16)  not null default 'PENDING',  -- PENDING | APPROVED | REJECTED
    rejection_reason  varchar(500),
    rating            numeric(2,1) not null default 0,
    vehicle           jsonb,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);
create index idx_couriers_city_status on couriers (city_id, moderation_status, status);

create table courier_delivery_zones (
    courier_id       uuid not null references couriers(id) on delete cascade,
    delivery_zone_id uuid not null,
    primary key (courier_id, delivery_zone_id)
);

create table delivery_jobs (
    suborder_id      uuid primary key,
    order_id         uuid         not null,
    shop_id          uuid         not null,
    city_id          uuid         not null,
    status           varchar(20)  not null default 'CREATED',
    courier_id       uuid,
    offer_expires_at timestamptz,
    ready_for_pickup boolean      not null default false,
    failure_reason   varchar(500),
    accepted_at      timestamptz,
    delivered_at     timestamptz,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);
create index idx_delivery_jobs_order on delivery_jobs (order_id);
create index idx_delivery_jobs_courier_status on delivery_jobs (courier_id, status);
create index idx_delivery_jobs_status_expiry on delivery_jobs (status, offer_expires_at);

create table assignment_offers (
    id          uuid primary key default gen_random_uuid(),
    suborder_id uuid        not null,
    courier_id  uuid        not null,
    status      varchar(16) not null default 'PENDING',  -- PENDING | ACCEPTED | REJECTED | EXPIRED
    offered_at  timestamptz not null default now(),
    expires_at  timestamptz not null
);
create index idx_assignment_offers_suborder on assignment_offers (suborder_id, status);

create table courier_earnings (
    id           uuid primary key default gen_random_uuid(),
    courier_id   uuid        not null,
    suborder_id  uuid        not null unique,
    amount_minor bigint      not null,
    currency     varchar(3)  not null,
    created_at   timestamptz not null default now()
);
create index idx_courier_earnings_courier on courier_earnings (courier_id, created_at);
