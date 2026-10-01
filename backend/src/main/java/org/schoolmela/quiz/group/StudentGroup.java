package org.schoolmela.quiz.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.schoolmela.quiz.user.User;

/** A named set of students (e.g. a class) that quizzes can be assigned to. */
@Entity
@Table(name = "student_groups")
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToMany
    @JoinTable(name = "group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    private Set<User> members = new HashSet<>();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentGroup() {
    }

    public StudentGroup(String name, Long createdBy, Instant createdAt) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void addMember(User student) {
        members.add(student);
    }

    public void removeMember(Long studentId) {
        members.removeIf(m -> m.getId().equals(studentId));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<User> getMembers() {
        return Set.copyOf(members);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
