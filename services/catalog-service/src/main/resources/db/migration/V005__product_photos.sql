-- Фото товара: оригинал магазина + варианты, сгенерированные ИИ из оригинала в выбранном магазином
-- стиле (студия, интерьер, с человеком, …) с учётом названия/описания товара и пожелания магазина.
-- Покупатель видит только опубликованные готовые фото.
alter table product_images
    add column kind            varchar(16)  not null default 'ORIGINAL',  -- ORIGINAL | AI
    add column style           varchar(24),                               -- стиль кадра для AI (PhotoStyle)
    add column wish            varchar(200),                              -- пожелание магазина к кадру
    add column source_image_id uuid references product_images (id) on delete cascade,
    add column status          varchar(16)  not null default 'READY',     -- PENDING | PROCESSING | READY | FAILED
    add column published       boolean      not null default true,
    add column storage_key     varchar(300),
    add column error           varchar(300),
    add column attempts        int          not null default 0,
    add column created_at      timestamptz  not null default now();
alter table product_images alter column url drop not null;  -- у ещё не сгенерированного варианта ссылки нет
create index ix_product_images_pending on product_images (status, created_at) where status = 'PENDING';
