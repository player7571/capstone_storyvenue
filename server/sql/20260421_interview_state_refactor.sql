ALTER TABLE interview_sessions
    ADD COLUMN IF NOT EXISTS current_question_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS question_bank_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_interview_complete BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS session_question_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_no INTEGER NOT NULL CHECK (question_no BETWEEN 1 AND 10),
    main_question TEXT NOT NULL,
    question_hint TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    follow_up_count INTEGER NOT NULL DEFAULT 0,
    last_follow_up_question TEXT,
    aggregated_answer_text TEXT NOT NULL DEFAULT '',
    has_answer BOOLEAN NOT NULL DEFAULT FALSE,
    story_ready BOOLEAN NOT NULL DEFAULT FALSE,
    last_decision TEXT,
    last_reason_code TEXT,
    total_score INTEGER NOT NULL DEFAULT 0,
    required_slot_hits INTEGER NOT NULL DEFAULT 0,
    last_selected_missing_slot TEXT,
    last_pass_route TEXT,
    question_bank_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(session_id, question_no)
);

CREATE TABLE IF NOT EXISTS session_question_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_no INTEGER NOT NULL CHECK (question_no BETWEEN 1 AND 10),
    source_type TEXT NOT NULL CHECK (source_type IN ('voice', 'text')),
    user_text TEXT NOT NULL,
    stt_raw_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_question_states_session_id
    ON session_question_states(session_id);

CREATE INDEX IF NOT EXISTS idx_session_question_answers_session_id
    ON session_question_answers(session_id);
