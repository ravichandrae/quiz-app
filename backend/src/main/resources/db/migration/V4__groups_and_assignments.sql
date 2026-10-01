CREATE TABLE student_groups (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_by BIGINT       REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_student_groups_name ON student_groups (lower(name));

CREATE TABLE group_members (
    group_id   BIGINT      NOT NULL REFERENCES student_groups (id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, student_id)
);

CREATE INDEX idx_group_members_student_id ON group_members (student_id);

-- A quiz is given to one student, to a group, or to every student. Targets are resolved when a
-- student opens their dashboard, so later group members and new students see existing assignments.
CREATE TABLE assignments (
    id          BIGSERIAL PRIMARY KEY,
    quiz_id     BIGINT      NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('STUDENT', 'GROUP', 'ALL')),
    student_id  BIGINT      REFERENCES users (id) ON DELETE CASCADE,
    group_id    BIGINT      REFERENCES student_groups (id) ON DELETE CASCADE,
    due_at      TIMESTAMPTZ,
    assigned_by BIGINT      REFERENCES users (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((target_type = 'STUDENT' AND student_id IS NOT NULL AND group_id IS NULL)
        OR (target_type = 'GROUP' AND group_id IS NOT NULL AND student_id IS NULL)
        OR (target_type = 'ALL' AND student_id IS NULL AND group_id IS NULL))
);

CREATE UNIQUE INDEX uq_assignments_student ON assignments (quiz_id, student_id) WHERE target_type = 'STUDENT';
CREATE UNIQUE INDEX uq_assignments_group ON assignments (quiz_id, group_id) WHERE target_type = 'GROUP';
CREATE UNIQUE INDEX uq_assignments_all ON assignments (quiz_id) WHERE target_type = 'ALL';
CREATE INDEX idx_assignments_student_id ON assignments (student_id);
CREATE INDEX idx_assignments_group_id ON assignments (group_id);
