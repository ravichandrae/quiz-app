package org.schoolmela.quiz.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String mobile;

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(length = 254)
    private String email;

    @Column(length = 150)
    private String school;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String name, String mobile, String pinHash, String email, String school, Role role, Instant createdAt) {
        this.name = name;
        this.mobile = mobile;
        this.pinHash = pinHash;
        this.email = email;
        this.school = school;
        this.role = role;
        this.createdAt = createdAt;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a wrong PIN; once {@code maxAttempts} is reached the account is locked and the count starts again. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockoutDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockoutDuration);
            failedLoginAttempts = 0;
        }
    }

    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public String getPinHash() {
        return pinHash;
    }

    public String getEmail() {
        return email;
    }

    public String getSchool() {
        return school;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
