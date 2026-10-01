package org.schoolmela.quiz.attempt;

import jakarta.validation.Valid;
import java.util.List;
import org.schoolmela.quiz.attempt.AttemptDtos.AnswerRequest;
import org.schoolmela.quiz.attempt.AttemptDtos.AttemptState;
import org.schoolmela.quiz.attempt.AttemptDtos.MyQuiz;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The student's side of quizzes. Responses never include correct answers while a quiz is running. */
@RestController
@RequestMapping("/me")
public class StudentQuizController {

    private final AttemptService service;

    public StudentQuizController(AttemptService service) {
        this.service = service;
    }

    /** Assigned quizzes, newest first, with whether each is new, in progress or done. */
    @GetMapping("/quizzes")
    public List<MyQuiz> myQuizzes(@AuthenticationPrincipal Jwt jwt) {
        return service.myQuizzes(studentId(jwt));
    }

    @GetMapping("/quizzes/{quizId}")
    public MyQuiz myQuiz(@AuthenticationPrincipal Jwt jwt, @PathVariable Long quizId) {
        return service.myQuiz(studentId(jwt), quizId);
    }

    /** Starts the quiz (or resumes it) and returns the first question to answer. */
    @PostMapping("/quizzes/{quizId}/attempt")
    public AttemptState start(@AuthenticationPrincipal Jwt jwt, @PathVariable Long quizId) {
        return service.start(studentId(jwt), quizId);
    }

    /** The question to answer now (starting its timer if needed), or the result once finished. */
    @GetMapping("/attempts/{attemptId}")
    public AttemptState current(@AuthenticationPrincipal Jwt jwt, @PathVariable Long attemptId) {
        return service.current(studentId(jwt), attemptId);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    public AttemptState answer(@AuthenticationPrincipal Jwt jwt, @PathVariable Long attemptId,
            @Valid @RequestBody AnswerRequest request) {
        return service.answer(studentId(jwt), attemptId, request.position(), request.selectedOption());
    }

    private static Long studentId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
