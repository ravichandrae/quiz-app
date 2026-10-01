CREATE TABLE questions (
    id                 BIGSERIAL PRIMARY KEY,
    text               VARCHAR(500) NOT NULL,
    option_a           VARCHAR(200) NOT NULL,
    option_b           VARCHAR(200) NOT NULL,
    option_c           VARCHAR(200) NOT NULL,
    option_d           VARCHAR(200) NOT NULL,
    correct_option     INT          NOT NULL CHECK (correct_option BETWEEN 0 AND 3),
    time_limit_seconds INT          NOT NULL CHECK (time_limit_seconds > 0),
    created_by         BIGINT       REFERENCES users (id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

CREATE INDEX idx_questions_created_at ON questions (created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE quizzes (
    id                       BIGSERIAL PRIMARY KEY,
    title                    VARCHAR(150) NOT NULL,
    total_time_limit_seconds INT          CHECK (total_time_limit_seconds > 0),
    show_answers             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by               BIGINT       REFERENCES users (id),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ
);

CREATE INDEX idx_quizzes_updated_at ON quizzes (updated_at DESC) WHERE deleted_at IS NULL;

-- Ordered questions of a quiz. Uniqueness of a question within a quiz is enforced by the API, so
-- reordering can update rows in place without tripping a constraint.
CREATE TABLE quiz_questions (
    quiz_id     BIGINT NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    position    INT    NOT NULL,
    question_id BIGINT NOT NULL REFERENCES questions (id),
    PRIMARY KEY (quiz_id, position)
);

CREATE INDEX idx_quiz_questions_question_id ON quiz_questions (question_id);
