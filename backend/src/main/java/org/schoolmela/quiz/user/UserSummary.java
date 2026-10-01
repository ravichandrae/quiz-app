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
        Instant createdAt) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getName(),
                user.getMobile(),
                user.getEmail(),
                user.getSchool(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt());
    }
}
