package org.schoolmela.quiz.group;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.group.GroupDtos.GroupDetail;
import org.schoolmela.quiz.group.GroupDtos.GroupSummary;
import org.schoolmela.quiz.user.Role;
import org.schoolmela.quiz.user.User;
import org.schoolmela.quiz.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private final StudentGroupRepository groups;
    private final UserRepository users;
    private final Clock clock;

    public GroupService(StudentGroupRepository groups, UserRepository users, Clock clock) {
        this.groups = groups;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list() {
        return groups.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(Long id) {
        return GroupDetail.from(find(id));
    }

    @Transactional
    public GroupDetail create(Long adminId, String name) {
        String trimmed = name.trim();
        ensureNameFree(trimmed, null);
        try {
            return GroupDetail.from(groups.saveAndFlush(new StudentGroup(trimmed, adminId, clock.instant())));
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    @Transactional
    public GroupDetail rename(Long id, String name) {
        StudentGroup group = find(id);
        String trimmed = name.trim();
        ensureNameFree(trimmed, id);
        group.rename(trimmed);
        return GroupDetail.from(group);
    }

    /** Deletes the group; quizzes assigned to it are no longer shown to its members. */
    @Transactional
    public void delete(Long id) {
        groups.delete(find(id));
    }

    /** Adds students to the group; students already in it are left as they are. */
    @Transactional
    public GroupDetail addMembers(Long id, List<Long> studentIds) {
        StudentGroup group = find(id);
        List<User> students = users.findAllById(new HashSet<>(studentIds));
        if (students.size() != new HashSet<>(studentIds).size()
                || students.stream().anyMatch(u -> u.getRole() != Role.STUDENT)) {
            throw ApiException.invalidField("studentIds", "Some of these students could not be found. Please refresh.");
        }
        students.forEach(group::addMember);
        return GroupDetail.from(group);
    }

    @Transactional
    public GroupDetail removeMember(Long id, Long studentId) {
        StudentGroup group = find(id);
        group.removeMember(studentId);
        return GroupDetail.from(group);
    }

    StudentGroup find(Long id) {
        return groups.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found."));
    }

    private void ensureNameFree(String name, Long excludeId) {
        if (groups.findFirstByNameIgnoreCase(name).filter(g -> !g.getId().equals(excludeId)).isPresent()) {
            throw nameTaken();
        }
    }

    private static ApiException nameTaken() {
        return ApiException.invalidField("name", "A group with this name already exists");
    }
}
