package org.schoolmela.quiz.attempt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignedQuiz;
import org.schoolmela.quiz.assignment.AssignmentService;
import org.schoolmela.quiz.attempt.Attempt.AnswerOutcome;
import org.schoolmela.quiz.attempt.Attempt.Status;
import org.schoolmela.quiz.attempt.AttemptDtos.AttemptState;
import org.schoolmela.quiz.attempt.AttemptDtos.CurrentQuestion;
import org.schoolmela.quiz.attempt.AttemptDtos.MyQuiz;
import org.schoolmela.quiz.attempt.AttemptDtos.Result;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.config.QuizProperties;
import org.schoolmela.quiz.quiz.Quiz;
import org.schoolmela.quiz.quiz.QuizRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs quizzes for students: their quiz list, starting or resuming, and answering. */
@Service
public class AttemptService {

    private final AttemptRepository attempts;
    private final AssignmentService assignments;
    private final QuizRepository quizzes;
    private final Clock clock;
    private final Duration grace;

    public AttemptService(AttemptRepository attempts, AssignmentService assignments, QuizRepository quizzes,
            QuizProperties props, Clock clock) {
        this.attempts = attempts;
        this.assignments = assignments;
        this.quizzes = quizzes;
        this.clock = clock;
        this.grace = props.answerGrace();
    }

    /** The student's assigned quizzes with whether each is new, in progress or done. */
    @Transactional
    public List<MyQuiz> myQuizzes(Long studentId) {
        Instant now = clock.instant();
        List<Attempt> own = attempts.findByStudentId(studentId);
        // Attempts left open (e.g. the browser was closed) may have run out of time since.
        own.forEach(a -> a.catchUp(now, grace));
        Map<Long, Attempt> byQuiz = own.stream().collect(Collectors.toMap(Attempt::getQuizId, Function.identity()));
        return assignments.quizzesFor(studentId).stream()
                .map(q -> MyQuiz.from(q, byQuiz.get(q.quizId())))
                .toList();
    }

    @Transactional
    public MyQuiz myQuiz(Long studentId, Long quizId) {
        return myQuizzes(studentId).stream()
                .filter(q -> q.quizId().equals(quizId))
                .findFirst()
                .orElseThrow(AttemptService::quizNotAvailable);
    }

    /**
     * Starts the quiz, or carries on with the attempt already in progress. Refusing a finished quiz
     * still saves any time-outs worked out on the way, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AttemptState start(Long studentId, Long quizId) {
        AssignedQuiz assigned = assignments.quizzesFor(studentId).stream()
                .filter(q -> q.quizId().equals(quizId))
                .findFirst()
                .orElseThrow(AttemptService::quizNotAvailable);
        Instant now = clock.instant();
        Optional<Attempt> existing = attempts.findByQuizAndStudentForUpdate(quizId, studentId);
        if (existing.isPresent()) {
            Attempt attempt = existing.get();
            attempt.serve(now, grace);
            if (attempt.getStatus() == Status.COMPLETED) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_TAKEN", "You have already taken this quiz.");
            }
            return stateOf(attempt, now);
        }
        Quiz quiz = quizzes.findById(quizId).orElseThrow(AttemptService::quizNotAvailable);
        Attempt attempt = Attempt.start(quiz, studentId, assigned.dueAt(), now);
        attempt.serve(now, grace);
        try {
            attempts.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException e) {
            // Another request started the same quiz at the same moment (e.g. a double tap).
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_STARTED", "This quiz has already started. Please try again.");
        }
        return stateOf(attempt, now);
    }

    /** The question to answer now, or the result once the attempt is over. */
    @Transactional
    public AttemptState current(Long studentId, Long attemptId) {
        Attempt attempt = findOwn(studentId, attemptId);
        Instant now = clock.instant();
        attempt.serve(now, grace);
        return stateOf(attempt, now);
    }

    /** Submits an answer and returns the next question (or the result). */
    @Transactional
    public AttemptState answer(Long studentId, Long attemptId, int position, Integer selectedOption) {
        Attempt attempt = findOwn(studentId, attemptId);
        Instant now = clock.instant();
        if (attempt.answer(position, selectedOption, now, grace) == AnswerOutcome.NOT_OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_NOT_OPEN",
                    "This question is not open yet. Please reload the page.");
        }
        attempt.serve(now, grace);
        return stateOf(attempt, now);
    }

    private Attempt findOwn(Long studentId, Long attemptId) {
        return attempts.findByIdForUpdate(attemptId)
                .filter(a -> a.getStudentId().equals(studentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Quiz not found."));
    }

    private AttemptState stateOf(Attempt attempt, Instant now) {
        if (attempt.getStatus() == Status.COMPLETED) {
            Result result = new Result(attempt.getScore(), attempt.getQuestionCount(), attempt.getPercentage(),
                    attempt.getFinishReason());
            return new AttemptState(attempt.getId(), attempt.getQuizTitle(), attempt.getStatus(),
                    attempt.getQuestionCount(), null, null, result);
        }
        AttemptQuestion q = attempt.current().orElseThrow();
        CurrentQuestion question = new CurrentQuestion(q.getPosition(), q.getText(), q.getOptions(),
                q.getTimeLimitSeconds(), secondsUntil(attempt.deadlineFor(q), now));
        Long quizSecondsLeft = attempt.getDeadlineAt() == null ? null : secondsUntil(attempt.getDeadlineAt(), now);
        return new AttemptState(attempt.getId(), attempt.getQuizTitle(), attempt.getStatus(),
                attempt.getQuestionCount(), question, quizSecondsLeft, null);
    }

    /** Whole seconds left, rounded up so the screen never shows 0 while time remains. */
    private static long secondsUntil(Instant deadline, Instant now) {
        long millis = Duration.between(now, deadline).toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    private static ApiException quizNotAvailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "QUIZ_NOT_AVAILABLE", "This quiz is not available to you.");
    }
}
