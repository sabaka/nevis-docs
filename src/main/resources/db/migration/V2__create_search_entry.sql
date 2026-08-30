create extension if not exists vector;

create table search_entry (
    entity_type      text        not null,
    entity_id        uuid        not null,
    searchable_text  text        not null,
    lexical_index    tsvector generated always as (to_tsvector('simple', searchable_text)) stored,
    embedding        vector(1024),
    embedding_status text        not null,
    embedding_error  text,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,

    primary key (entity_type, entity_id),

    constraint search_entry_entity_type_check
        check (entity_type in ('CLIENT', 'DOCUMENT')),

    constraint search_entry_embedding_status_check
        check (embedding_status in ('NOT_REQUIRED', 'PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

create index search_entry_lexical_index_idx on search_entry using gin (lexical_index);
