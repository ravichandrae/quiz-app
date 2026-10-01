package org.schoolmela.quiz.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.schoolmela.quiz.user.UserSummary;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record GroupRequest(
            @NotBlank(message = "Please give the group a name")
            @Size(max = 100, message = "The name must be 100 characters or less")
            String name) {
    }

    public record AddMembersRequest(
            @NotEmpty(message = "Please choose at least 1 student")
            @Size(max = 1000, message = "Add at most 1000 students at a time")
            List<@NotNull Long> studentIds) {
    }

    public record GroupSummary(Long id, String name, int memberCount, Instant createdAt) {
    }

    public record GroupDetail(Long id, String name, List<UserSummary> members, Instant createdAt) {

        public static GroupDetail from(StudentGroup group) {
            List<UserSummary> members = group.getMembers().stream()
                    .map(UserSummary::from)
                    .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new GroupDetail(group.getId(), group.getName(), members, group.getCreatedAt());
        }
    }
}
