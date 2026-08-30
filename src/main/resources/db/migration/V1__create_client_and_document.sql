create table client (
    id           uuid primary key,
    first_name   text not null,
    last_name    text not null,
    email        text not null,
    description  text,
    social_links text[] not null default '{}'
);

create unique index client_email_lower_key on client (lower(email));

create table document (
    id         uuid primary key,
    client_id  uuid not null references client (id),
    title      text not null,
    content    text not null,
    created_at timestamptz not null
);
