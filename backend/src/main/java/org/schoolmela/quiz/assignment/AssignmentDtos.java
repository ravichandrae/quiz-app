package org.schoolmela.quiz.assignment;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.schoolmela.quiz.assignment.Assignment.TargetType;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /** {@code studentId} is required for STUDENT, {@code groupId} for GROUP; {@code dueAt} is optional. */
    public record AssignRequest(
            @NotNull(message = "Please choose who should take the quiz") TargetType targetType,
            Long studentId,
            Long groupId,
            Instant dueAt) {
    }

    public record AssignmentDto(
            Long id,
            TargetType targetType,
            Long studentId,
            String studentName,
            String studentMobile,
            Long groupId,
            String groupName,
            Instant assignedAt,
            Instant dueAt) {

        public static AssignmentDto from(Assignment a) {
            return new AssignmentDto(
                    a.getId(),
                    a.getTargetType(),
                    a.getStudent() == null ? null : a.getStudent().getId(),
                    a.getStudent() == null ? null : a.getStudent().getName(),
                    a.getStudent() == null ? null : a.getStudent().getMobile(),
                    a.getGroup() == null ? null : a.getGroup().getId(),
                    a.getGroup() == null ? null : a.getGroup().getName(),
                    a.getAssignedAt(),
                    a.getDueAt());
        }
    }

    public enum MyQuizStatus {
        /** Assigned but not started yet. */
        NEW
    }

    /** A quiz as a student sees it on their dashboard: no questions or answers. */
    public record MyQuiz(
            Long quizId,
            String title,
            int questionCount,
            int questionTimeSeconds,
            Integer totalTimeLimitSeconds,
            Instant assignedAt,
            Instant dueAt,
            MyQuizStatus status) {
    }
}
