package org.schoolmela.quiz.quiz;

import static org.schoolmela.quiz.user.Credentials.blankToNull;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.common.PageResponse;
import org.schoolmela.quiz.question.Question;
import org.schoolmela.quiz.question.QuestionRepository;
import org.schoolmela.quiz.quiz.QuizDtos.QuizDetail;
import org.schoolmela.quiz.quiz.QuizDtos.QuizRequest;
import org.schoolmela.quiz.quiz.QuizDtos.QuizSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final Clock clock;

    public QuizService(QuizRepository quizzes, QuestionRepository questions, Clock clock) {
        this.quizzes = quizzes;
        this.questions = questions;
        this.clock = clock;
    }

    /** Lists quizzes, most recently changed first. {@code query} matches part of the title. */
    @Transactional(readOnly = true)
    public PageResponse<QuizSummary> list(String query, int page, int size) {
        Specification<Quiz> spec = Specification.unrestricted();
        String q = blankToNull(query);
        if (q != null) {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = (root, cq, cb) -> cb.like(cb.lower(root.get("title")), pattern);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return PageResponse.from(quizzes.findAll(spec, pageRequest), QuizSummary::from);
    }

    @Transactional(readOnly = true)
    public QuizDetail get(Long id) {
        return QuizDetail.from(find(id));
    }

    @Transactional
    public QuizDetail create(Long adminId, QuizRequest req) {
        Quiz quiz = new Quiz(adminId, clock.instant());
        apply(quiz, req);
        return QuizDetail.from(quizzes.save(quiz));
    }

    @Transactional
    public QuizDetail update(Long id, QuizRequest req) {
        Quiz quiz = find(id);
        apply(quiz, req);
        return QuizDetail.from(quiz);
    }

    @Transactional
    public void delete(Long id) {
        find(id).delete(clock.instant());
    }

    private void apply(Quiz quiz, QuizRequest req) {
        List<Long> ids = req.questionIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.invalidField("questionIds", "A question was added more than once");
        }
        Map<Long, Question> found = questions.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw ApiException.invalidField("questionIds",
                    "Some questions were deleted from the question bank. Remove them and try again.");
        }
        List<Question> ordered = ids.stream().map(found::get).toList();
        quiz.update(req.title().trim(), req.totalTimeLimitSeconds(), req.showAnswers(), ordered, clock.instant());
    }

    private Quiz find(Long id) {
        return quizzes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "Quiz not found."));
    }
}
