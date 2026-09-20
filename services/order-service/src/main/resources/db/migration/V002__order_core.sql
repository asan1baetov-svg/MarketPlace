-- order-service — корзина, заказы, suborders, история статусов, промокоды.
-- Значения enum хранятся строкой в верхнем регистре (EnumType.STRING), как в catalog-service.
-- processed_events и outbox уже созданы в V001__baseline.sql.

create table carts (
    id             uuid primary key default gen_random_uuid(),
    client_user_id uuid        not null unique,
    city_id        uuid        not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

create table cart_items (
    id                    uuid primary key default gen_random_uuid(),
    cart_id               uuid         not null references carts(id) on delete cascade,
    product_id            uuid         not null,
    shop_id               uuid         not null,
    product_name          varchar(300) not null,
    qty                   integer      not null,
    unit_sale_price_minor bigint       not null,
    currency              varchar(3)   not null,
    added_at              timestamptz  not null default now()
);
create index idx_cart_items_cart on cart_items (cart_id);
create unique index uq_cart_items_cart_product on cart_items (cart_id, product_id);

create table orders (
    id                    uuid primary key default gen_random_uuid(),
    client_user_id        uuid        not null,
    city_id               uuid        not null,
    delivery_address      jsonb       not null,
    status                varchar(24) not null default 'CREATED',
    items_amount_minor    bigint      not null,
    discount_amount_minor bigint      not null default 0,
    total_amount_minor    bigint      not null,
    currency              varchar(3)  not null,
    payment_id            uuid,
    promocode_id          uuid,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);
create index idx_orders_client on orders (client_user_id, created_at);
create index idx_orders_city_status on orders (city_id, status);

create table suborders (
    id                        uuid primary key default gen_random_uuid(),
    order_id                  uuid        not null references orders(id) on delete cascade,
    shop_id                   uuid        not null,
    status                    varchar(24) not null default 'CREATED',
    goods_amount_minor        bigint      not null,
    cost_amount_minor         bigint      not null,
    platform_commission_minor bigint      not null,
    courier_id                uuid,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now()
);
create index idx_suborders_order on suborders (order_id);
create index idx_suborders_shop_status on suborders (shop_id, status);

create table suborder_items (
    id                      uuid primary key default gen_random_uuid(),
    suborder_id             uuid         not null references suborders(id) on delete cascade,
    product_id              uuid         not null,
    name_snapshot           varchar(300) not null,
    qty                     integer      not null,
    cost_price_minor        bigint       not null,
    sale_price_minor        bigint       not null,
    markup_percent_snapshot numeric(6,2) not null
);
create index idx_suborder_items_suborder on suborder_items (suborder_id);

create table order_status_history (
    id          uuid primary key default gen_random_uuid(),
    order_id    uuid,
    suborder_id uuid,
    from_status varchar(24),
    to_status   varchar(24) not null,
    actor       varchar(64) not null,
    reason      varchar(500),
    created_at  timestamptz not null default now()
);
create index idx_osh_order on order_status_history (order_id);
create index idx_osh_suborder on order_status_history (suborder_id);

create table promocodes (
    id          uuid primary key default gen_random_uuid(),
    code        varchar(64)  not null unique,
    type        varchar(16)  not null,   -- PERCENT | FIXED
    value       numeric(14,2) not null,
    funded_by   varchar(16)  not null,   -- PLATFORM | SHOP
    valid_from  timestamptz  not null,
    valid_to    timestamptz  not null,
    usage_limit integer,
    used_count  integer      not null default 0,
    active      boolean      not null default true,
    created_at  timestamptz  not null default now()
);
