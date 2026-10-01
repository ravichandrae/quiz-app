package org.schoolmela.quiz.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.SQLRestriction;

/** A multiple-choice question with four options. Deleted questions are hidden from every query. */
@Entity
@Table(name = "questions")
@SQLRestriction("deleted_at is null")
public class Question {

    public static final int OPTION_COUNT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    /** Index (0-3) of the correct option. */
    @Column(name = "correct_option", nullable = false)
    private int correctOption;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Question() {
    }

    public Question(Long createdBy, Instant now) {
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String text, List<String> options, int correctOption, int timeLimitSeconds, Instant now) {
        if (options.size() != OPTION_COUNT) {
            throw new IllegalArgumentException("A question needs exactly " + OPTION_COUNT + " options");
        }
        this.text = text;
        this.optionA = options.get(0);
        this.optionB = options.get(1);
        this.optionC = options.get(2);
        this.optionD = options.get(3);
        this.correctOption = correctOption;
        this.timeLimitSeconds = timeLimitSeconds;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }

    public List<String> getOptions() {
        return List.of(optionA, optionB, optionC, optionD);
    }

    public Long getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
