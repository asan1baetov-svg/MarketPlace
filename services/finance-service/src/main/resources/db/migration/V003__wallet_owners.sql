-- Владелец-пользователь «хозяина» кошельков (магазина / курьера) — для проверки доступа по sub
-- access-JWT без синхронных вызовов в catalog/courier. Один на (owner_type, owner_ref), действует
-- для кошельков во всех валютах. Заполняется из catalog.ShopApproved и couriers.CourierRegistered.
create table wallet_owners (
    owner_type varchar(16)  not null,
    owner_ref  varchar(128) not null,
    user_id    uuid         not null,
    created_at timestamptz  not null default now(),
    primary key (owner_type, owner_ref)
);
create index ix_wallet_owners_user on wallet_owners (user_id);
