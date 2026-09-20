-- Отзывы о товарах. Оставляет только покупатель, которому товар доставлен (проверка в order-service);
-- один отзыв на товар от пользователя (повторная отправка — правка). Рейтинг товара и магазина
-- денормализован в products/shops и пересчитывается при каждом изменении.
create table product_reviews (
    id             uuid primary key default gen_random_uuid(),
    product_id     uuid          not null references products (id),
    shop_id        uuid          not null references shops (id),
    author_user_id uuid          not null,
    rating         smallint      not null check (rating between 1 and 5),
    text           varchar(2000),
    reply_text     varchar(2000),
    replied_at     timestamptz,
    hidden         boolean       not null default false,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    constraint uq_product_reviews_author unique (product_id, author_user_id)
);
create index ix_product_reviews_product on product_reviews (product_id, hidden, created_at desc);
create index ix_product_reviews_shop on product_reviews (shop_id, created_at desc);
