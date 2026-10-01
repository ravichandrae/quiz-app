package org.schoolmela.quiz.assignment;

import jakarta.validation.Valid;
import java.util.List;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignRequest;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/quizzes/{quizId}/assignments")
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    /** Who the quiz is given to: everyone first, then groups, then individual students. */
    @GetMapping
    public List<AssignmentDto> list(@PathVariable Long quizId) {
        return service.listForQuiz(quizId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDto assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long quizId,
            @Valid @RequestBody AssignRequest request) {
        return service.assign(Long.valueOf(jwt.getSubject()), quizId, request);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable Long quizId, @PathVariable Long assignmentId) {
        service.unassign(quizId, assignmentId);
    }
}
