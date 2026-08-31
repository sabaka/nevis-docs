alter table document
    add column summary            text,
    add column summary_status     text not null default 'PENDING',
    add column summary_error      text,
    add column summary_updated_at timestamptz not null default current_timestamp;

alter table document
    add constraint document_summary_status_check
        check (summary_status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

alter table document
    add constraint document_completed_summary_check
        check (summary_status <> 'COMPLETED'
            or (summary is not null and length(trim(summary)) > 0));

create index document_pending_summary_idx
    on document (created_at, id) where summary_status = 'PENDING';
