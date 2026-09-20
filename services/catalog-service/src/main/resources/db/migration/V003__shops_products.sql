-- catalog-service — магазины, товары, категории, наценка, остатки.
-- Значения enum — верхний регистр (совпадают с именами Java-enum, EnumType.STRING).

create table categories (
    id         uuid primary key default gen_random_uuid(),
    parent_id  uuid references categories(id),
    name       varchar(200) not null,
    slug       varchar(200) not null unique,
    sort       int          not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index idx_categories_parent on categories (parent_id);

create table shops (
    id             uuid primary key default gen_random_uuid(),
    owner_user_id  uuid         not null,
    name           varchar(200) not null,
    legal_info     jsonb,
    country_id     uuid         not null references countries(id),
    city_id        uuid         not null references cities(id),
    status         varchar(20)  not null default 'MODERATION', -- DRAFT|MODERATION|ACTIVE|REJECTED|SUSPENDED
    rejection_reason varchar(500),
    rating         numeric(2,1) not null default 0,
    reviews_count  int          not null default 0,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now()
);
create index idx_shops_owner on shops (owner_user_id);
create index idx_shops_city_status on shops (city_id, status);

create table shop_delivery_zones (
    shop_id           uuid not null references shops(id) on delete cascade,
    delivery_zone_id  uuid not null references delivery_zones(id) on delete cascade,
    primary key (shop_id, delivery_zone_id)
);

create table products (
    id                 uuid primary key default gen_random_uuid(),
    shop_id            uuid          not null references shops(id),
    category_id        uuid          not null references categories(id),
    name               varchar(300)  not null,
    description        text,
    unit               varchar(10)   not null default 'PCS', -- PCS|KG|L
    cost_price_minor   bigint        not null,
    currency           char(3)       not null,
    status             varchar(20)   not null default 'DRAFT', -- DRAFT|MODERATION|PUBLISHED|ARCHIVED|REJECTED
    rejection_reason   varchar(500),
    -- денормализация гео для витрины (копируется из shop при создании):
    city_id            uuid          not null references cities(id),
    country_id         uuid          not null references countries(id),
    rating             numeric(2,1)  not null default 0,
    reviews_count      int           not null default 0,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now()
);
create index idx_products_city_category_status on products (city_id, category_id, status);
create index idx_products_city_status_rating on products (city_id, status, rating desc);
create index idx_products_shop_status on products (shop_id, status);

create table product_images (
    id         uuid primary key default gen_random_uuid(),
    product_id uuid         not null references products(id) on delete cascade,
    url        varchar(500) not null,
    sort       int          not null default 0
);
create index idx_product_images_product on product_images (product_id);

create table product_stock (
    product_id        uuid primary key references products(id) on delete cascade,
    quantity          int not null default 0,
    reserved_quantity int not null default 0
);

create table markup_rules (
    id              uuid primary key default gen_random_uuid(),
    scope           varchar(20)  not null, -- GLOBAL|CATEGORY|SHOP|PRODUCT|REGION|CATEGORY_REGION
    category_id     uuid references categories(id),
    shop_id         uuid references shops(id),
    product_id      uuid references products(id),
    country_id      uuid references countries(id),
    city_id         uuid references cities(id),
    markup_percent  numeric(6,2) not null,
    priority        int          not null default 0,
    active          boolean      not null default true,
    created_at      timestamptz  not null default now()
);
create index idx_markup_scope_active on markup_rules (scope, active);

-- дефолтный фолбэк, чтобы резолв цены никогда не оставался без правила
insert into markup_rules (scope, markup_percent, priority, active)
values ('GLOBAL', 20.00, 0, true);
