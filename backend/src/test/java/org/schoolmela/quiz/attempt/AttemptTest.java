package org.schoolmela.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.attempt.Attempt.AnswerOutcome;
import org.schoolmela.quiz.attempt.Attempt.FinishReason;
import org.schoolmela.quiz.attempt.Attempt.Status;
import org.schoolmela.quiz.question.Question;
import org.schoolmela.quiz.quiz.Quiz;

class AttemptTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(3);

    /** Three questions of 30, 60 and 30 seconds; the correct option is B (1) for all. */
    private static Quiz quiz(Integer totalSeconds) {
        Quiz quiz = new Quiz(1L, T0);
        List<Question> questions = List.of(question("Q1", 30), question("Q2", 60), question("Q3", 30));
        quiz.update("Science", totalSeconds, true, questions, T0);
        return quiz;
    }

    private static Question question(String text, int seconds) {
        Question q = new Question(1L, T0);
        q.update(text, List.of("A", "B", "C", "D"), 1, seconds, T0);
        return q;
    }

    private static Attempt started(Integer totalSeconds) {
        Attempt attempt = Attempt.start(quiz(totalSeconds), 7L, null, T0);
        attempt.serve(T0, GRACE);
        return attempt;
    }

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    @Test
    void copiesTheQuizAndServesTheFirstQuestion() {
        Attempt attempt = started(null);

        assertThat(attempt.getQuizTitle()).isEqualTo("Science");
        assertThat(attempt.getQuestionCount()).isEqualTo(3);
        AttemptQuestion first = attempt.current().orElseThrow();
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(first.getServedAt()).isEqualTo(T0);
        assertThat(attempt.deadlineFor(first)).isEqualTo(at(30));
    }

    @Test
    void scoresOnlyCorrectAnswersAndFinishesAfterTheLastQuestion() {
        Attempt attempt = started(null);

        assertThat(attempt.answer(1, 1, at(10), GRACE)).isEqualTo(AnswerOutcome.RECORDED);
        attempt.serve(at(10), GRACE);
        attempt.answer(2, 0, at(20), GRACE);
        attempt.serve(at(20), GRACE);
        attempt.answer(3, 1, at(25), GRACE);

        assertThat(attempt.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(attempt.getFinishReason()).isEqualTo(FinishReason.ALL_ANSWERED);
        assertThat(attempt.getFinishedAt()).isEqualTo(at(25));
        assertThat(attempt.getScore()).isEqualTo(2);
        assertThat(attempt.getPercentage()).isEqualTo(67);
        assertThat(attempt.getQuestions()).extracting(AttemptQuestion::isCorrect).containsExactly(true, false, true);
    }

    @Test
    void anAnswerWithinTheGracePeriodStillCounts() {
        Attempt attempt = started(null);

        assertThat(attempt.answer(1, 1, at(32), GRACE)).isEqualTo(AnswerOutcome.RECORDED);

        assertThat(attempt.getQuestions().getFirst().isCorrect()).isTrue();
    }

    @Test
    void aLateAnswerIsNotScoredAndMovesOn() {
        Attempt attempt = started(null);

        AnswerOutcome outcome = attempt.answer(1, 1, at(34), GRACE);

        assertThat(outcome).isEqualTo(AnswerOutcome.ALREADY_CLOSED);
        AttemptQuestion first = attempt.getQuestions().getFirst();
        assertThat(first.isCorrect()).isFalse();
        assertThat(first.getSelectedOption()).isNull();
        assertThat(first.getAnsweredAt()).isEqualTo(at(30));
        assertThat(attempt.current().orElseThrow().getPosition()).isEqualTo(2);
    }

    @Test
    void unservedQuestionsKeepTheirTimeWhenTheStudentComesBackLater() {
        Attempt attempt = started(null);
        attempt.answer(1, 1, at(5), GRACE);

        // The student closed the app after answering question 1 and returns an hour later.
        attempt.serve(at(3600), GRACE);

        AttemptQuestion second = attempt.current().orElseThrow();
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(second.getServedAt()).isEqualTo(at(3600));
    }

    @Test
    void cannotAnswerAQuestionThatHasNotBeenShown() {
        Attempt attempt = started(null);

        assertThat(attempt.answer(2, 1, at(5), GRACE)).isEqualTo(AnswerOutcome.NOT_OPEN);
        attempt.answer(1, 1, at(6), GRACE);
        // Question 2 is next but its timer has not started until it is served.
        assertThat(attempt.answer(2, 1, at(7), GRACE)).isEqualTo(AnswerOutcome.NOT_OPEN);
    }

    @Test
    void aRepeatedSubmissionDoesNotChangeTheFirstAnswer() {
        Attempt attempt = started(null);
        attempt.answer(1, 0, at(5), GRACE);
        attempt.serve(at(5), GRACE);

        assertThat(attempt.answer(1, 1, at(6), GRACE)).isEqualTo(AnswerOutcome.ALREADY_CLOSED);

        assertThat(attempt.getQuestions().getFirst().getSelectedOption()).isZero();
    }

    @Test
    void theQuizTimeLimitCutsTheCurrentQuestionShort() {
        Attempt attempt = started(45);
        attempt.answer(1, 1, at(10), GRACE);
        attempt.serve(at(10), GRACE);

        // Question 2 allows 60 seconds, but only 35 remain of the quiz.
        assertThat(attempt.deadlineFor(attempt.current().orElseThrow())).isEqualTo(at(45));
    }

    @Test
    void whenTheQuizTimeRunsOutTheAttemptEnds() {
        Attempt attempt = started(45);
        attempt.answer(1, 1, at(10), GRACE);
        attempt.serve(at(10), GRACE);

        attempt.catchUp(at(49), GRACE);

        assertThat(attempt.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(attempt.getFinishReason()).isEqualTo(FinishReason.TIME_UP);
        assertThat(attempt.getFinishedAt()).isEqualTo(at(45));
        assertThat(attempt.getScore()).isEqualTo(1);
        // Question 3 was never reached.
        assertThat(attempt.getQuestions().get(2).getServedAt()).isNull();
    }

    @Test
    void doesNotShowANewQuestionAfterTheQuizTimeIsOver() {
        Attempt attempt = started(30);
        attempt.answer(1, 1, at(31), GRACE);

        assertThat(attempt.serve(at(31), GRACE)).isEmpty();
        assertThat(attempt.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(attempt.getScore()).isEqualTo(1);
    }

    @Test
    void startingAfterTheDueDateIsMarkedLate() {
        assertThat(Attempt.start(quiz(null), 7L, at(-1), T0).isLate()).isTrue();
        assertThat(Attempt.start(quiz(null), 7L, at(60), T0).isLate()).isFalse();
        assertThat(Attempt.start(quiz(null), 7L, null, T0).isLate()).isFalse();
    }
}
