drop index search_entry_lexical_index_idx;

alter table search_entry
    alter column lexical_index
        set expression as (
            to_tsvector('simple', regexp_replace(searchable_text, '[^[:alnum:]]+', ' ', 'g'))
        );

create index search_entry_lexical_index_idx on search_entry using gin (lexical_index);
