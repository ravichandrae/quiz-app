-- A student's single attempt at a quiz. The quiz's title, settings and questions are copied in when
-- the attempt starts, so later edits to the quiz never change an attempt in progress or its result.
CREATE TABLE attempts (
    id                       BIGSERIAL PRIMARY KEY,
    quiz_id                  BIGINT       NOT NULL REFERENCES quizzes (id),
    student_id               BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    quiz_title               VARCHAR(150) NOT NULL,
    total_time_limit_seconds INT,
    show_answers             BOOLEAN      NOT NULL,
    due_at                   TIMESTAMPTZ,
    late                     BOOLEAN      NOT NULL DEFAULT FALSE,
    started_at               TIMESTAMPTZ  NOT NULL,
    -- started_at + total_time_limit_seconds; null when the quiz has no overall limit.
    deadline_at              TIMESTAMPTZ,
    finished_at              TIMESTAMPTZ,
    status                   VARCHAR(12)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    finish_reason            VARCHAR(12)  CHECK (finish_reason IN ('ALL_ANSWERED', 'TIME_UP')),
    score                    INT          NOT NULL DEFAULT 0,
    question_count           INT          NOT NULL,
    UNIQUE (quiz_id, student_id)
);

CREATE INDEX idx_attempts_student_id ON attempts (student_id);

CREATE TABLE attempt_questions (
    id                 BIGSERIAL PRIMARY KEY,
    attempt_id         BIGINT       NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    position           INT          NOT NULL,
    question_id        BIGINT       NOT NULL REFERENCES questions (id),
    text               VARCHAR(500) NOT NULL,
    option_a           VARCHAR(200) NOT NULL,
    option_b           VARCHAR(200) NOT NULL,
    option_c           VARCHAR(200) NOT NULL,
    option_d           VARCHAR(200) NOT NULL,
    correct_option     INT          NOT NULL,
    time_limit_seconds INT          NOT NULL,
    -- When the question was first shown; its timer runs from here.
    served_at          TIMESTAMPTZ,
    -- When it was answered, or when its time ran out. Null if never reached.
    answered_at        TIMESTAMPTZ,
    selected_option    INT CHECK (selected_option BETWEEN 0 AND 3),
    -- True only for the correct option submitted in time.
    correct            BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (attempt_id, position)
);
