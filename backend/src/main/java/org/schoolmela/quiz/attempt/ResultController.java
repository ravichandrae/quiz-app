package org.schoolmela.quiz.attempt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.schoolmela.quiz.attempt.ResultDtos.AttemptDetail;
import org.schoolmela.quiz.attempt.ResultDtos.AttemptRow;
import org.schoolmela.quiz.attempt.ResultDtos.MyResult;
import org.schoolmela.quiz.attempt.ResultDtos.MyReview;
import org.schoolmela.quiz.common.PageResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResultController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ResultService service;

    public ResultController(ResultService service) {
        this.service = service;
    }

    /** The logged-in student's finished quizzes, most recent first. */
    @GetMapping("/me/results")
    public List<MyResult> myResults(@AuthenticationPrincipal Jwt jwt) {
        return service.myResults(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/me/results/{attemptId}")
    public MyReview myReview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long attemptId) {
        return service.myReview(Long.valueOf(jwt.getSubject()), attemptId);
    }

    /** Attempts, newest first; {@code q} matches part of the student's name or mobile number. */
    @GetMapping("/admin/attempts")
    public PageResponse<AttemptRow> list(
            @RequestParam(required = false) Long quizId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(quizId, q, page, size);
    }

    @GetMapping("/admin/attempts/{attemptId}")
    public AttemptDetail detail(@PathVariable Long attemptId) {
        return service.detail(attemptId);
    }

    /** The same attempts as {@link #list}, all of them, as a CSV file. */
    @GetMapping("/admin/attempts/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long quizId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("quiz-results.csv").build().toString())
                .body(service.exportCsv(quizId, q));
    }
}
