package org.schoolmela.quiz.attempt;

import java.time.Instant;
import java.util.List;
import org.schoolmela.quiz.attempt.Attempt.FinishReason;
import org.schoolmela.quiz.attempt.Attempt.Status;
import org.schoolmela.quiz.attempt.AttemptQuestion.Outcome;
import org.schoolmela.quiz.user.User;

public final class ResultDtos {

    private ResultDtos() {
    }

    /** A finished quiz in the student's score history. */
    public record MyResult(Long attemptId, Long quizId, String quizTitle, Instant finishedAt, int score,
            int questionCount, int percentage) {

        static MyResult from(Attempt a) {
            return new MyResult(a.getId(), a.getQuizId(), a.getQuizTitle(), a.getFinishedAt(), a.getScore(),
                    a.getQuestionCount(), a.getPercentage());
        }
    }

    /**
     * A finished attempt as the student sees it. {@code questions} is empty when the quiz does not
     * show answers; then only the score is shown.
     */
    public record MyReview(Long attemptId, Long quizId, String quizTitle, Instant finishedAt, int score,
            int questionCount, int percentage, FinishReason finishReason, boolean answersShown,
            List<ReviewQuestion> questions) {

        static MyReview from(Attempt a) {
            List<ReviewQuestion> questions = a.isShowAnswers()
                    ? a.getQuestions().stream().map(ReviewQuestion::from).toList()
                    : List.of();
            return new MyReview(a.getId(), a.getQuizId(), a.getQuizTitle(), a.getFinishedAt(), a.getScore(),
                    a.getQuestionCount(), a.getPercentage(), a.getFinishReason(), a.isShowAnswers(), questions);
        }
    }

    public record ReviewQuestion(int position, String text, List<String> options, Integer selectedOption,
            int correctOption, Outcome outcome) {

        static ReviewQuestion from(AttemptQuestion q) {
            return new ReviewQuestion(q.getPosition(), q.getText(), q.getOptions(), q.getSelectedOption(),
                    q.getCorrectOption(), q.getOutcome());
        }
    }

    /** One row of the admin results list. Score fields are meaningful once {@code status} is COMPLETED. */
    public record AttemptRow(Long id, Long studentId, String studentName, String studentMobile, Long quizId,
            String quizTitle, Status status, int score, int questionCount, int percentage, Instant startedAt,
            Instant finishedAt, boolean late) {

        static AttemptRow from(Attempt a) {
            User s = a.getStudent();
            return new AttemptRow(a.getId(), a.getStudentId(), s.getName(), s.getMobile(), a.getQuizId(),
                    a.getQuizTitle(), a.getStatus(), a.getScore(), a.getQuestionCount(), a.getPercentage(),
                    a.getStartedAt(), a.getFinishedAt(), a.isLate());
        }
    }

    /** Everything about one attempt, for teachers. */
    public record AttemptDetail(AttemptRow attempt, String studentSchool, Instant dueAt,
            Integer totalTimeLimitSeconds, FinishReason finishReason, List<DetailQuestion> questions) {

        static AttemptDetail from(Attempt a) {
            return new AttemptDetail(AttemptRow.from(a), a.getStudent().getSchool(), a.getDueAt(),
                    a.getTotalTimeLimitSeconds(), a.getFinishReason(),
                    a.getQuestions().stream().map(DetailQuestion::from).toList());
        }
    }

    public record DetailQuestion(int position, String text, List<String> options, Integer selectedOption,
            int correctOption, Outcome outcome, int timeLimitSeconds, Long secondsTaken) {

        static DetailQuestion from(AttemptQuestion q) {
            return new DetailQuestion(q.getPosition(), q.getText(), q.getOptions(), q.getSelectedOption(),
                    q.getCorrectOption(), q.getOutcome(), q.getTimeLimitSeconds(), q.getSecondsTaken());
        }
    }
}
