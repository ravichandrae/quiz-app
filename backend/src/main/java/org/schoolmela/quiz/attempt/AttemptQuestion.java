package org.schoolmela.quiz.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.schoolmela.quiz.question.Question;

/** A copy of one quiz question inside an attempt, with what the student did with it. */
@Entity
@Table(name = "attempt_questions")
public class AttemptQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private Attempt attempt;

    /** 1-based place in the quiz. */
    @Column(nullable = false)
    private int position;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "option_a", nullable = false, length = 200)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 200)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 200)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 200)
    private String optionD;

    @Column(name = "correct_option", nullable = false)
    private int correctOption;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "served_at")
    private Instant servedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "selected_option")
    private Integer selectedOption;

    @Column(nullable = false)
    private boolean correct;

    protected AttemptQuestion() {
    }

    AttemptQuestion(Attempt attempt, int position, Question question) {
        List<String> options = question.getOptions();
        this.attempt = attempt;
        this.position = position;
        this.questionId = question.getId();
        this.text = question.getText();
        this.optionA = options.get(0);
        this.optionB = options.get(1);
        this.optionC = options.get(2);
        this.optionD = options.get(3);
        this.correctOption = question.getCorrectOption();
        this.timeLimitSeconds = question.getTimeLimitSeconds();
    }

    /** Starts this question's timer, unless it is already running. */
    void serve(Instant now) {
        if (servedAt == null) {
            servedAt = now;
        }
    }

    /** Records an answer given in time; {@code selected} is null when the student gave none. */
    void record(Integer selected, Instant now) {
        this.selectedOption = selected;
        this.answeredAt = now;
        this.correct = selected != null && selected == correctOption;
    }

    /** Closes the question because its time ran out at {@code deadline}. */
    void timeOut(Instant deadline) {
        this.selectedOption = null;
        this.answeredAt = deadline;
        this.correct = false;
    }

    boolean isOpen() {
        return answeredAt == null;
    }

    public List<String> getOptions() {
        return List.of(optionA, optionB, optionC, optionD);
    }

    public int getPosition() {
        return position;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public Instant getServedAt() {
        return servedAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Integer getSelectedOption() {
        return selectedOption;
    }

    public boolean isCorrect() {
        return correct;
    }
}
