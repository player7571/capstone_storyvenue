ALTER TABLE session_question_states
    ADD COLUMN IF NOT EXISTS story_quality TEXT NOT NULL DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS answer_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_answered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS generated_chapter_id UUID,
    ADD COLUMN IF NOT EXISTS generated_at TIMESTAMPTZ;

ALTER TABLE chapter_drafts
    ADD COLUMN IF NOT EXISTS source_question_no INTEGER,
    ADD COLUMN IF NOT EXISTS answer_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS story_quality_at_generation TEXT;

CREATE INDEX IF NOT EXISTS idx_session_question_states_story_ready
    ON session_question_states(session_id, question_no, story_ready);
