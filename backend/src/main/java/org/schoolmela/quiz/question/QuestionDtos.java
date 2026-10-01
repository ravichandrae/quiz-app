package org.schoolmela.quiz.question;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class QuestionDtos {

    public static final int MIN_TIME_LIMIT_SECONDS = 30;
    public static final int MAX_TIME_LIMIT_SECONDS = 120;
    private static final String TIME_LIMIT_MESSAGE = "Time must be 30 to 120 seconds";

    private QuestionDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "Please type the question")
            @Size(max = 500, message = "The question must be 500 characters or less")
            String text,
            @NotNull(message = "Please fill in all 4 answers")
            @Size(min = Question.OPTION_COUNT, max = Question.OPTION_COUNT, message = "Please fill in all 4 answers")
            List<@NotBlank(message = "Please fill in this answer")
                 @Size(max = 200, message = "Each answer must be 200 characters or less") String> options,
            @NotNull(message = "Please choose the correct answer")
            @Min(value = 0, message = "Please choose the correct answer")
            @Max(value = Question.OPTION_COUNT - 1, message = "Please choose the correct answer")
            Integer correctOption,
            @NotNull(message = TIME_LIMIT_MESSAGE)
            @Min(value = MIN_TIME_LIMIT_SECONDS, message = TIME_LIMIT_MESSAGE)
            @Max(value = MAX_TIME_LIMIT_SECONDS, message = TIME_LIMIT_MESSAGE)
            Integer timeLimitSeconds) {
    }

    public record QuestionDto(
            Long id,
            String text,
            List<String> options,
            int correctOption,
            int timeLimitSeconds,
            Instant createdAt,
            Instant updatedAt) {

        public static QuestionDto from(Question q) {
            return new QuestionDto(q.getId(), q.getText(), q.getOptions(), q.getCorrectOption(),
                    q.getTimeLimitSeconds(), q.getCreatedAt(), q.getUpdatedAt());
        }
    }
}
