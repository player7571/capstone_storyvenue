create table if not exists session_artifacts (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references interview_sessions(id) on delete cascade,
    artifact_type text not null,
    storage_url text not null,
    mime_type text,
    linked_question_no int,
    summary text,
    created_at timestamptz not null default now()
);

create index if not exists idx_session_artifacts_session_id
    on session_artifacts(session_id);

create index if not exists idx_session_artifacts_linked_question_no
    on session_artifacts(session_id, linked_question_no);
