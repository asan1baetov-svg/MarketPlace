-- auth-service — учётные записи, роли, токены, SSO-идентичности MLM.
-- Значения enum хранятся в верхнем регистре (совпадают с именами Java-enum, EnumType.STRING).

create table users (
    id            uuid primary key default gen_random_uuid(),
    email         varchar(320) unique,
    phone         varchar(32)  unique,
    password_hash varchar(200),
    status        varchar(20)  not null default 'PENDING',   -- PENDING | ACTIVE | BLOCKED
    client_type   varchar(20)  not null default 'EXTERNAL',  -- EXTERNAL | INTERNAL_MLM
    locale        varchar(8)   not null default 'ru',
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    -- локальная регистрация требует контакт; SSO-клиент MLM идентифицируется по mlm_sso_identities.mlm_user_id
    constraint users_contact_present check (
        client_type = 'INTERNAL_MLM' or email is not null or phone is not null
    )
);

create table roles (
    id   smallint primary key,
    code varchar(32) unique not null
);
insert into roles (id, code) values
    (1, 'CLIENT_EXTERNAL'),
    (2, 'CLIENT_MLM'),
    (3, 'SHOP'),
    (4, 'COURIER'),
    (5, 'ADMIN'),
    (6, 'SUPER_ADMIN');

create table user_roles (
    user_id uuid     not null references users(id) on delete cascade,
    role_id smallint not null references roles(id),
    primary key (user_id, role_id)
);

create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid        not null references users(id) on delete cascade,
    token_hash  varchar(64) not null unique,          -- sha-256(hex) от самого токена
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked     boolean     not null default false,
    replaced_by uuid        references refresh_tokens(id)
);
create index idx_refresh_user_active on refresh_tokens (user_id) where revoked = false;

create table otp_codes (
    id          uuid         primary key default gen_random_uuid(),
    target      varchar(320) not null,                -- email или телефон
    channel     varchar(10)  not null,                -- EMAIL | SMS
    code_hash   varchar(64)  not null,
    purpose     varchar(30)  not null,                -- REGISTRATION | LOGIN | RESET
    attempts    int          not null default 0,
    expires_at  timestamptz  not null,
    consumed_at timestamptz,
    created_at  timestamptz  not null default now()
);
create index idx_otp_target on otp_codes (target, purpose);

create table mlm_sso_identities (
    user_id            uuid         primary key references users(id) on delete cascade,
    mlm_user_id        varchar(100) not null unique,
    referral_code      varchar(64),
    upline_mlm_user_id varchar(100),
    last_access_status varchar(30),                    -- как отдал MLM: none | access_paid | active | expired
    linked_at          timestamptz  not null default now(),
    last_login_at      timestamptz
);

create table mlm_sso_token_log (   -- replay-защита: каждый jti входящего SSO-токена одноразовый
    jti         varchar(64)  primary key,
    mlm_user_id varchar(100) not null,
    issued_at   timestamptz  not null,
    consumed_at timestamptz  not null default now()
);
