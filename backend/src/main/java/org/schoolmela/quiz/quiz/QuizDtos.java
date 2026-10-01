package org.schoolmela.quiz.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.schoolmela.quiz.question.QuestionDtos.QuestionDto;

public final class QuizDtos {

    public static final int MAX_QUESTIONS = 100;

    private QuizDtos() {
    }

    public record QuizRequest(
            @NotBlank(message = "Please give the quiz a title")
            @Size(max = 150, message = "The title must be 150 characters or less")
            String title,
            @Min(value = 60, message = "The quiz time must be at least 1 minute")
            @Max(value = 3 * 60 * 60, message = "The quiz time must be 3 hours or less")
            Integer totalTimeLimitSeconds,
            boolean showAnswers,
            @NotNull(message = "Please add at least 1 question")
            @Size(min = 1, max = MAX_QUESTIONS, message = "A quiz needs 1 to 100 questions")
            List<@NotNull Long> questionIds) {
    }

    public record QuizSummary(
            Long id,
            String title,
            int questionCount,
            int questionTimeSeconds,
            Integer totalTimeLimitSeconds,
            boolean showAnswers,
            Instant updatedAt) {

        public static QuizSummary from(Quiz quiz) {
            return new QuizSummary(quiz.getId(), quiz.getTitle(), quiz.getQuestions().size(),
                    quiz.getQuestionTimeSeconds(), quiz.getTotalTimeLimitSeconds(), quiz.isShowAnswers(),
                    quiz.getUpdatedAt());
        }
    }

    public record QuizDetail(
            Long id,
            String title,
            Integer totalTimeLimitSeconds,
            boolean showAnswers,
            int questionTimeSeconds,
            List<QuestionDto> questions,
            Instant createdAt,
            Instant updatedAt) {

        public static QuizDetail from(Quiz quiz) {
            return new QuizDetail(quiz.getId(), quiz.getTitle(), quiz.getTotalTimeLimitSeconds(), quiz.isShowAnswers(),
                    quiz.getQuestionTimeSeconds(), quiz.getQuestions().stream().map(QuestionDto::from).toList(),
                    quiz.getCreatedAt(), quiz.getUpdatedAt());
        }
    }
}
