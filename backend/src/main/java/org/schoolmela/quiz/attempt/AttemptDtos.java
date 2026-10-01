package org.schoolmela.quiz.attempt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignedQuiz;
import org.schoolmela.quiz.attempt.Attempt.FinishReason;
import org.schoolmela.quiz.attempt.Attempt.Status;

public final class AttemptDtos {

    private AttemptDtos() {
    }

    public enum MyQuizStatus {
        /** Assigned but not started. */
        NEW,
        IN_PROGRESS,
        COMPLETED
    }

    /** A quiz on the student's dashboard. {@code score} is set once the quiz is completed. */
    public record MyQuiz(
            Long quizId,
            String title,
            int questionCount,
            int questionTimeSeconds,
            Integer totalTimeLimitSeconds,
            Instant assignedAt,
            Instant dueAt,
            MyQuizStatus status,
            Long attemptId,
            Integer score) {

        static MyQuiz from(AssignedQuiz quiz, Attempt attempt) {
            MyQuizStatus status = attempt == null ? MyQuizStatus.NEW
                    : attempt.getStatus() == Status.COMPLETED ? MyQuizStatus.COMPLETED : MyQuizStatus.IN_PROGRESS;
            return new MyQuiz(quiz.quizId(), quiz.title(), quiz.questionCount(), quiz.questionTimeSeconds(),
                    quiz.totalTimeLimitSeconds(), quiz.assignedAt(), quiz.dueAt(), status,
                    attempt == null ? null : attempt.getId(),
                    status == MyQuizStatus.COMPLETED ? attempt.getScore() : null);
        }
    }

    public record AnswerRequest(
            @NotNull @Min(1) Integer position,
            /** Index 0-3 of the chosen option, or null when time ran out without an answer. */
            @Min(0) @Max(3) Integer selectedOption) {
    }

    /**
     * Where a student is in an attempt. While it is in progress, {@code question} is the one to
     * answer now (without its correct answer); once completed, {@code result} holds the score.
     */
    public record AttemptState(
            Long attemptId,
            String quizTitle,
            Status status,
            int questionCount,
            CurrentQuestion question,
            /** Seconds until the whole quiz ends; null when it has no overall limit. */
            Long quizSecondsLeft,
            Result result) {
    }

    public record CurrentQuestion(int position, String text, List<String> options, int timeLimitSeconds,
            long secondsLeft) {
    }

    public record Result(int score, int questionCount, int percentage, FinishReason finishReason) {
    }
}
