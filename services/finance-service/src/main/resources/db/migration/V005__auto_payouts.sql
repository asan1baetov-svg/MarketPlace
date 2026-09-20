-- Реквизиты для автовыплат (банк + телефон получателя в Finik Payments Gateway), модерируются админом.
create table payout_requisites (
    id         uuid primary key default gen_random_uuid(),
    owner_type varchar(16)  not null,              -- SHOP (курьеры — позже)
    owner_ref  varchar(128) not null,
    bank       varchar(32)  not null,              -- код FinikBank
    phone      varchar(20)  not null,              -- 996XXXXXXXXX
    status     varchar(16)  not null default 'PENDING',  -- PENDING | APPROVED | BLOCKED
    created_at timestamptz  not null default now(),
    decided_at timestamptz
);
create index ix_payout_requisites_owner on payout_requisites (owner_type, owner_ref, status);

-- Автовыплата = заявка на вывод, созданная системой после оплаты заказа и отправляемая фоновым
-- заданием: QUEUED → SENDING → PAID, при отказе банка — FAILED (деньги остаются в hold до решения админа).
alter table payout_requests
    add column auto            boolean      not null default false,
    add column attempts        int          not null default 0,
    add column last_error      varchar(500),
    add column next_attempt_at timestamptz,
    add column requisite_id    uuid references payout_requisites (id);
create index ix_payout_requests_queue on payout_requests (status, next_attempt_at) where auto;
create index ix_payout_requests_requested_by on payout_requests (requested_by);
