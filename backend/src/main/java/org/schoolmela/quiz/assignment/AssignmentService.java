package org.schoolmela.quiz.assignment;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignRequest;
import org.schoolmela.quiz.assignment.AssignmentDtos.AssignmentDto;
import org.schoolmela.quiz.assignment.AssignmentDtos.MyQuiz;
import org.schoolmela.quiz.assignment.AssignmentDtos.MyQuizStatus;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.group.StudentGroup;
import org.schoolmela.quiz.group.StudentGroupRepository;
import org.schoolmela.quiz.quiz.Quiz;
import org.schoolmela.quiz.quiz.QuizRepository;
import org.schoolmela.quiz.user.Role;
import org.schoolmela.quiz.user.User;
import org.schoolmela.quiz.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final QuizRepository quizzes;
    private final UserRepository users;
    private final StudentGroupRepository groups;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, QuizRepository quizzes, UserRepository users,
            StudentGroupRepository groups, Clock clock) {
        this.assignments = assignments;
        this.quizzes = quizzes;
        this.users = users;
        this.groups = groups;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> listForQuiz(Long quizId) {
        findQuiz(quizId);
        return assignments.findByQuizId(quizId).stream()
                .sorted(Comparator.comparing((Assignment a) -> a.getTargetType().ordinal()).reversed())
                .map(AssignmentDto::from)
                .toList();
    }

    @Transactional
    public AssignmentDto assign(Long adminId, Long quizId, AssignRequest req) {
        Quiz quiz = findQuiz(quizId);
        Instant now = clock.instant();
        if (req.dueAt() != null && !req.dueAt().isAfter(now)) {
            throw ApiException.invalidField("dueAt", "The due date has already passed");
        }
        Assignment assignment = switch (req.targetType()) {
            case STUDENT -> Assignment.toStudent(quiz, findStudent(req.studentId()), req.dueAt(), adminId, now);
            case GROUP -> Assignment.toGroup(quiz, findGroup(req.groupId()), req.dueAt(), adminId, now);
            case ALL -> Assignment.toEveryone(quiz, req.dueAt(), adminId, now);
        };
        try {
            return AssignmentDto.from(assignments.saveAndFlush(assignment));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ASSIGNED",
                    "This quiz is already given to " + describe(assignment) + ".");
        }
    }

    @Transactional
    public void unassign(Long quizId, Long assignmentId) {
        Assignment assignment = assignments.findById(assignmentId)
                .filter(a -> a.getQuiz().getId().equals(quizId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND",
                        "This assignment no longer exists."));
        assignments.delete(assignment);
    }

    /**
     * The quizzes a student can see. A quiz given to the student in several ways (directly and
     * through a group, say) appears once, with the earliest assignment date and the latest due date.
     */
    @Transactional(readOnly = true)
    public List<MyQuiz> quizzesFor(Long studentId) {
        Map<Long, MyQuiz> byQuiz = new LinkedHashMap<>();
        for (Assignment a : assignments.findVisibleTo(studentId)) {
            Quiz quiz = a.getQuiz();
            MyQuiz existing = byQuiz.get(quiz.getId());
            Instant assignedAt = existing == null || a.getAssignedAt().isBefore(existing.assignedAt())
                    ? a.getAssignedAt() : existing.assignedAt();
            Instant dueAt = existing == null ? a.getDueAt() : laterDueDate(existing.dueAt(), a.getDueAt());
            byQuiz.put(quiz.getId(), new MyQuiz(quiz.getId(), quiz.getTitle(), quiz.getQuestions().size(),
                    quiz.getQuestionTimeSeconds(), quiz.getTotalTimeLimitSeconds(), assignedAt, dueAt,
                    MyQuizStatus.NEW));
        }
        return byQuiz.values().stream()
                .sorted(Comparator.comparing(MyQuiz::assignedAt).reversed())
                .toList();
    }

    /** No due date is more generous than any due date. */
    private static Instant laterDueDate(Instant a, Instant b) {
        if (a == null || b == null) {
            return null;
        }
        return a.isAfter(b) ? a : b;
    }

    private Quiz findQuiz(Long id) {
        return quizzes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "Quiz not found."));
    }

    private User findStudent(Long id) {
        return Optional.ofNullable(id)
                .flatMap(users::findById)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.invalidField("studentId", "Please choose a student"));
    }

    private StudentGroup findGroup(Long id) {
        return Optional.ofNullable(id)
                .flatMap(groups::findById)
                .orElseThrow(() -> ApiException.invalidField("groupId", "Please choose a group"));
    }

    private static String describe(Assignment a) {
        return switch (a.getTargetType()) {
            case STUDENT -> a.getStudent().getName();
            case GROUP -> "the group \"" + a.getGroup().getName() + "\"";
            case ALL -> "all students";
        };
    }
}
