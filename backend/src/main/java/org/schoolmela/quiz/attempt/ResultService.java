package org.schoolmela.quiz.attempt;

import static org.schoolmela.quiz.user.Credentials.blankToNull;

import jakarta.persistence.criteria.Join;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.schoolmela.quiz.attempt.Attempt.Status;
import org.schoolmela.quiz.attempt.ResultDtos.AttemptDetail;
import org.schoolmela.quiz.attempt.ResultDtos.AttemptRow;
import org.schoolmela.quiz.attempt.ResultDtos.MyResult;
import org.schoolmela.quiz.attempt.ResultDtos.MyReview;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.common.PageResponse;
import org.schoolmela.quiz.config.QuizProperties;
import org.schoolmela.quiz.config.ReportProperties;
import org.schoolmela.quiz.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scores and answers after quizzes: the student's history and review, and teachers' reports. */
@Service
public class ResultService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "startedAt", "id");

    private final AttemptRepository attempts;
    private final Clock clock;
    private final Duration grace;
    private final DateTimeFormatter reportTime;

    public ResultService(AttemptRepository attempts, QuizProperties quizProps, ReportProperties reportProps,
            Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
        this.grace = quizProps.answerGrace();
        this.reportTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(reportProps.timeZone());
    }

    /** The student's finished quizzes, most recent first. */
    @Transactional
    public List<MyResult> myResults(Long studentId) {
        Instant now = clock.instant();
        attempts.findByStudentId(studentId).forEach(a -> a.catchUp(now, grace));
        return attempts.findByStudentIdAndStatusOrderByFinishedAtDesc(studentId, Status.COMPLETED).stream()
                .map(MyResult::from)
                .toList();
    }

    /** A finished attempt with its answers, if the quiz shows them. */
    @Transactional
    public MyReview myReview(Long studentId, Long attemptId) {
        Attempt attempt = attempts.findById(attemptId)
                .filter(a -> a.getStudentId().equals(studentId))
                .orElseThrow(ResultService::attemptNotFound);
        attempt.catchUp(clock.instant(), grace);
        if (attempt.getStatus() != Status.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_FINISHED", "Finish the quiz to see your results.");
        }
        return MyReview.from(attempt);
    }

    /** Attempts matching the filters, newest first. Attempts whose time has run out are finished on the way. */
    @Transactional
    public PageResponse<AttemptRow> list(Long quizId, String studentQuery, int page, int size) {
        Page<Attempt> found = attempts.findAll(filter(quizId, studentQuery), PageRequest.of(page, size, NEWEST_FIRST));
        Instant now = clock.instant();
        found.forEach(a -> a.catchUp(now, grace));
        return PageResponse.from(found, AttemptRow::from);
    }

    @Transactional
    public AttemptDetail detail(Long attemptId) {
        Attempt attempt = attempts.findById(attemptId).orElseThrow(ResultService::attemptNotFound);
        attempt.catchUp(clock.instant(), grace);
        return AttemptDetail.from(attempt);
    }

    /** The matching attempts as CSV (UTF-8 with a byte order mark, so spreadsheet programs read it correctly). */
    @Transactional
    public byte[] exportCsv(Long quizId, String studentQuery) {
        List<Attempt> rows = attempts.findAll(filter(quizId, studentQuery), NEWEST_FIRST);
        Instant now = clock.instant();
        StringBuilder csv = new StringBuilder("﻿");
        line(csv, "Student name", "Mobile number", "School", "Quiz title", "Status", "Score", "Questions",
                "Percentage", "Started", "Finished", "Late");
        for (Attempt a : rows) {
            a.catchUp(now, grace);
            User s = a.getStudent();
            boolean done = a.getStatus() == Status.COMPLETED;
            line(csv, s.getName(), s.getMobile(), s.getSchool(), a.getQuizTitle(),
                    done ? "Completed" : "Not finished",
                    done ? String.valueOf(a.getScore()) : "",
                    String.valueOf(a.getQuestionCount()),
                    done ? a.getPercentage() + "%" : "",
                    reportTime.format(a.getStartedAt()),
                    a.getFinishedAt() == null ? "" : reportTime.format(a.getFinishedAt()),
                    a.isLate() ? "Yes" : "No");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Specification<Attempt> filter(Long quizId, String studentQuery) {
        // Load each attempt's student in the same query (but not in the count query for paging).
        Specification<Attempt> spec = (root, cq, cb) -> {
            if (cq.getResultType() != Long.class && cq.getResultType() != long.class) {
                root.fetch("student");
            }
            return null;
        };
        if (quizId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("quizId"), quizId));
        }
        String q = blankToNull(studentQuery);
        if (q != null) {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> {
                Join<Attempt, User> student = root.join("student");
                return cb.or(cb.like(cb.lower(student.get("name")), pattern), cb.like(student.get("mobile"), pattern));
            });
        }
        return spec;
    }

    private static void line(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(cells[i]));
        }
        csv.append("\r\n");
    }

    /**
     * Quotes a cell, and stops spreadsheet programs from running text that starts like a formula
     * (a student could register with a name such as "=HYPERLINK(...)").
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String text = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static ApiException attemptNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Result not found.");
    }
}
