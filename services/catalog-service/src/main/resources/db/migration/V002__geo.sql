-- catalog-service — гео-справочники: страны, города, зоны доставки.

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
