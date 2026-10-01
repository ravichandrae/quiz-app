package org.schoolmela.quiz.group;

import jakarta.validation.Valid;
import java.util.List;
import org.schoolmela.quiz.group.GroupDtos.AddMembersRequest;
import org.schoolmela.quiz.group.GroupDtos.GroupDetail;
import org.schoolmela.quiz.group.GroupDtos.GroupRequest;
import org.schoolmela.quiz.group.GroupDtos.GroupSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    /** All groups, by name. Schools have few groups, so this is not paged. */
    @GetMapping
    public List<GroupSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public GroupDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GroupRequest request) {
        return service.create(Long.valueOf(jwt.getSubject()), request.name());
    }

    @PutMapping("/{id}")
    public GroupDetail rename(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return service.rename(id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/members")
    public GroupDetail addMembers(@PathVariable Long id, @Valid @RequestBody AddMembersRequest request) {
        return service.addMembers(id, request.studentIds());
    }

    @DeleteMapping("/{id}/members/{studentId}")
    public GroupDetail removeMember(@PathVariable Long id, @PathVariable Long studentId) {
        return service.removeMember(id, studentId);
    }
}
