package org.schoolmela.quiz.assignment;

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
import jakarta.persistence.Table;
import java.time.Instant;
import org.schoolmela.quiz.group.StudentGroup;
import org.schoolmela.quiz.quiz.Quiz;
import org.schoolmela.quiz.user.User;

/** Gives a quiz to one student, to a group, or to every student. */
@Entity
@Table(name = "assignments")
public class Assignment {

    public enum TargetType {
        STUDENT,
        GROUP,
        ALL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private StudentGroup group;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "assigned_by", updatable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected Assignment() {
    }

    private Assignment(Quiz quiz, TargetType targetType, User student, StudentGroup group, Instant dueAt,
            Long assignedBy, Instant assignedAt) {
        this.quiz = quiz;
        this.targetType = targetType;
        this.student = student;
        this.group = group;
        this.dueAt = dueAt;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public static Assignment toStudent(Quiz quiz, User student, Instant dueAt, Long assignedBy, Instant now) {
        return new Assignment(quiz, TargetType.STUDENT, student, null, dueAt, assignedBy, now);
    }

    public static Assignment toGroup(Quiz quiz, StudentGroup group, Instant dueAt, Long assignedBy, Instant now) {
        return new Assignment(quiz, TargetType.GROUP, null, group, dueAt, assignedBy, now);
    }

    public static Assignment toEveryone(Quiz quiz, Instant dueAt, Long assignedBy, Instant now) {
        return new Assignment(quiz, TargetType.ALL, null, null, dueAt, assignedBy, now);
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public User getStudent() {
        return student;
    }

    public StudentGroup getGroup() {
        return group;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
