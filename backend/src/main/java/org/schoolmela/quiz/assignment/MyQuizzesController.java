package org.schoolmela.quiz.assignment;

import java.util.List;
import org.schoolmela.quiz.assignment.AssignmentDtos.MyQuiz;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyQuizzesController {

    private final AssignmentService service;

    public MyQuizzesController(AssignmentService service) {
        this.service = service;
    }

    /** The logged-in student's assigned quizzes, newest first. */
    @GetMapping("/me/quizzes")
    public List<MyQuiz> myQuizzes(@AuthenticationPrincipal Jwt jwt) {
        return service.quizzesFor(Long.valueOf(jwt.getSubject()));
    }
}
