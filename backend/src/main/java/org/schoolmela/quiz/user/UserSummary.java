package org.schoolmela.quiz.user;

import java.time.Instant;

public record UserSummary(
        Long id,
        String name,
        String mobile,
        String email,
        String school,
        Role role,
        boolean active,
        Instant createdAt,
        /** When a lockout for wrong PINs ends; in the past (or null) if the account is not locked. */
        Instant lockedUntil) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getName(),
                user.getMobile(),
                user.getEmail(),
                user.getSchool(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLockedUntil());
    }
}
