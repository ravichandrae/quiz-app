package org.schoolmela.quiz.attempt;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.schoolmela.quiz.question.Question;
import org.schoolmela.quiz.quiz.Quiz;
import org.schoolmela.quiz.user.User;

/**
 * A student's attempt at a quiz. The server owns the clock: each question's timer starts when it
 * is first served, and an answer counts only if it arrives before that question's deadline (plus a
 * small grace period for slow networks). Questions are answered in order and cannot be revisited.
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public enum FinishReason {
        ALL_ANSWERED,
        TIME_UP
    }

    /** What happened to a submitted answer. */
    public enum AnswerOutcome {
        RECORDED,
        /** The question was already closed (answered, timed out, or the quiz ended); nothing changed. */
        ALREADY_CLOSED,
        /** The question has not been shown yet. */
        NOT_OPEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private Long quizId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    /** Read-only view of {@code studentId}, for showing and filtering results by student. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private User student;

    @Column(name = "quiz_title", nullable = false, length = 150)
    private String quizTitle;

    @Column(name = "total_time_limit_seconds")
    private Integer totalTimeLimitSeconds;

    @Column(name = "show_answers", nullable = false)
    private boolean showAnswers;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(nullable = false)
    private boolean late;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_reason", length = 12)
    private FinishReason finishReason;

    @Column(nullable = false)
    private int score;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<AttemptQuestion> questions = new ArrayList<>();

    protected Attempt() {
    }

    /** Starts an attempt with a copy of the quiz as it is now. */
    public static Attempt start(Quiz quiz, Long studentId, Instant dueAt, Instant now) {
        Attempt attempt = new Attempt();
        attempt.quizId = quiz.getId();
        attempt.studentId = studentId;
        attempt.quizTitle = quiz.getTitle();
        attempt.totalTimeLimitSeconds = quiz.getTotalTimeLimitSeconds();
        attempt.showAnswers = quiz.isShowAnswers();
        attempt.dueAt = dueAt;
        attempt.late = dueAt != null && now.isAfter(dueAt);
        attempt.startedAt = now;
        attempt.deadlineAt = quiz.getTotalTimeLimitSeconds() == null ? null : now.plusSeconds(quiz.getTotalTimeLimitSeconds());
        attempt.status = Status.IN_PROGRESS;
        List<Question> quizQuestions = quiz.getQuestions();
        for (int i = 0; i < quizQuestions.size(); i++) {
            attempt.questions.add(new AttemptQuestion(attempt, i + 1, quizQuestions.get(i)));
        }
        attempt.questionCount = quizQuestions.size();
        return attempt;
    }

    /** The question the student is on, if the attempt is still going. */
    public Optional<AttemptQuestion> current() {
        if (status == Status.COMPLETED) {
            return Optional.empty();
        }
        return questions.stream().filter(AttemptQuestion::isOpen).findFirst();
    }

    /** When the question's time runs out: its own limit, or the end of the quiz if that is sooner. */
    public Instant deadlineFor(AttemptQuestion question) {
        Instant own = question.getServedAt().plusSeconds(question.getTimeLimitSeconds());
        return deadlineAt != null && deadlineAt.isBefore(own) ? deadlineAt : own;
    }

    /**
     * Brings the attempt up to date: closes questions whose time ran out (they score nothing) and
     * finishes the attempt when no questions are left or the quiz time is over.
     */
    public void catchUp(Instant now, Duration grace) {
        if (status == Status.COMPLETED) {
            return;
        }
        if (deadlineAt != null && now.isAfter(deadlineAt.plus(grace))) {
            endBecauseTimeIsUp();
            return;
        }
        Optional<AttemptQuestion> open = current();
        while (open.isPresent() && open.get().getServedAt() != null
                && now.isAfter(deadlineFor(open.get()).plus(grace))) {
            open.get().timeOut(deadlineFor(open.get()));
            open = current();
        }
        if (open.isEmpty()) {
            Instant lastAnswer = questions.stream().map(AttemptQuestion::getAnsweredAt)
                    .max(Comparator.naturalOrder()).orElse(now);
            finish(lastAnswer, FinishReason.ALL_ANSWERED);
        }
    }

    /**
     * Shows the current question, starting its timer. When the quiz time is already over, the
     * attempt ends instead of showing a question that could not be answered.
     */
    public Optional<AttemptQuestion> serve(Instant now, Duration grace) {
        catchUp(now, grace);
        if (status == Status.IN_PROGRESS && deadlineAt != null && !now.isBefore(deadlineAt)
                && current().map(q -> q.getServedAt() == null).orElse(false)) {
            endBecauseTimeIsUp();
        }
        Optional<AttemptQuestion> open = current();
        open.ifPresent(q -> q.serve(now));
        return open;
    }

    /** Submits the answer for the question at {@code position}; {@code selected} is null for no answer. */
    public AnswerOutcome answer(int position, Integer selected, Instant now, Duration grace) {
        catchUp(now, grace);
        Optional<AttemptQuestion> open = current();
        if (open.isEmpty() || position < open.get().getPosition()) {
            return AnswerOutcome.ALREADY_CLOSED;
        }
        AttemptQuestion question = open.get();
        if (position > question.getPosition() || question.getServedAt() == null) {
            return AnswerOutcome.NOT_OPEN;
        }
        // catchUp has closed the question if its time (plus grace) was over, so this answer is in time.
        question.record(selected, now);
        catchUp(now, grace);
        return AnswerOutcome.RECORDED;
    }

    private void endBecauseTimeIsUp() {
        for (AttemptQuestion q : questions) {
            if (q.isOpen() && q.getServedAt() != null) {
                q.timeOut(deadlineFor(q));
            }
        }
        finish(deadlineAt, FinishReason.TIME_UP);
    }

    private void finish(Instant at, FinishReason reason) {
        status = Status.COMPLETED;
        finishedAt = at;
        finishReason = reason;
        score = (int) questions.stream().filter(AttemptQuestion::isCorrect).count();
    }

    /** Whole-number percentage of questions answered correctly. */
    public int getPercentage() {
        return questionCount == 0 ? 0 : Math.round(score * 100f / questionCount);
    }

    public Long getId() {
        return id;
    }

    public Long getQuizId() {
        return quizId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public User getStudent() {
        return student;
    }

    public String getQuizTitle() {
        return quizTitle;
    }

    public Integer getTotalTimeLimitSeconds() {
        return totalTimeLimitSeconds;
    }

    public boolean isShowAnswers() {
        return showAnswers;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isLate() {
        return late;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Status getStatus() {
        return status;
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public int getScore() {
        return score;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public List<AttemptQuestion> getQuestions() {
        return List.copyOf(questions);
    }
}
