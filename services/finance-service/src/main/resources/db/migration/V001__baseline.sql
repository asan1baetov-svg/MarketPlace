-- finance-service — базовая миграция.
-- Таблица идемпотентности консюмеров Kafka: eventId уже обработанных событий.
create table processed_events (
    event_id   uuid        not null,
    consumer   varchar(100) not null,
    processed_at timestamptz not null default now(),
    primary key (event_id, consumer)
);

-- Transactional outbox: событие пишется в одной транзакции с бизнес-данными,
-- отдельный публикатор досылает его в Kafka.
create table outbox (
    id          uuid        primary key default gen_random_uuid(),
    topic       varchar(100) not null,
    partition_key varchar(200) not null,
    event_type  varchar(100) not null,
    payload     jsonb       not null,
    created_at  timestamptz not null default now(),
    sent_at     timestamptz
);
create index idx_outbox_unsent on outbox (created_at) where sent_at is null;
