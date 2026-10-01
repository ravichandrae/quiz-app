package org.schoolmela.quiz.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;
import org.schoolmela.quiz.question.Question;

/** A titled, ordered set of questions from the bank. Deleted quizzes are hidden from every query. */
@Entity
@Table(name = "quizzes")
@SQLRestriction("deleted_at is null")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    /** Time allowed for the whole quiz; null when only the per-question limits apply. */
    @Column(name = "total_time_limit_seconds")
    private Integer totalTimeLimitSeconds;

    /** Whether students see which answers were right after finishing. */
    @Column(name = "show_answers", nullable = false)
    private boolean showAnswers;

    @ManyToMany
    @JoinTable(name = "quiz_questions",
            joinColumns = @JoinColumn(name = "quiz_id"),
            inverseJoinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<Question> questions = new ArrayList<>();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Quiz() {
    }

    public Quiz(Long createdBy, Instant now) {
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, Integer totalTimeLimitSeconds, boolean showAnswers, List<Question> questions,
            Instant now) {
        this.title = title;
        this.totalTimeLimitSeconds = totalTimeLimitSeconds;
        this.showAnswers = showAnswers;
        this.questions.clear();
        this.questions.addAll(questions);
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }

    /** Sum of the per-question time limits: the longest the quiz can take without a total limit. */
    public int getQuestionTimeSeconds() {
        return questions.stream().mapToInt(Question::getTimeLimitSeconds).sum();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getTotalTimeLimitSeconds() {
        return totalTimeLimitSeconds;
    }

    public boolean isShowAnswers() {
        return showAnswers;
    }

    public List<Question> getQuestions() {
        return List.copyOf(questions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
