package org.schoolmela.quiz.question;

import static org.schoolmela.quiz.user.Credentials.blankToNull;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.common.PageResponse;
import org.schoolmela.quiz.question.QuestionDtos.QuestionDto;
import org.schoolmela.quiz.question.QuestionDtos.QuestionRequest;
import org.schoolmela.quiz.quiz.QuizRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuizRepository quizzes;
    private final Clock clock;

    public QuestionService(QuestionRepository questions, QuizRepository quizzes, Clock clock) {
        this.questions = questions;
        this.quizzes = quizzes;
        this.clock = clock;
    }

    /** Lists the question bank, newest first. {@code query} matches part of the question text. */
    @Transactional(readOnly = true)
    public PageResponse<QuestionDto> list(String query, int page, int size) {
        Specification<Question> spec = Specification.unrestricted();
        String q = blankToNull(query);
        if (q != null) {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = (root, cq, cb) -> cb.like(cb.lower(root.get("text")), pattern);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(questions.findAll(spec, pageRequest), QuestionDto::from);
    }

    @Transactional(readOnly = true)
    public QuestionDto get(Long id) {
        return QuestionDto.from(find(id));
    }

    @Transactional
    public QuestionDto create(Long adminId, QuestionRequest req) {
        Question question = new Question(adminId, clock.instant());
        apply(question, req);
        return QuestionDto.from(questions.save(question));
    }

    @Transactional
    public QuestionDto update(Long id, QuestionRequest req) {
        Question question = find(id);
        apply(question, req);
        return QuestionDto.from(question);
    }

    /** Deletes a question unless a quiz still uses it, so quizzes never lose questions silently. */
    @Transactional
    public void delete(Long id) {
        Question question = find(id);
        List<String> usedIn = quizzes.findTitlesUsingQuestion(id);
        if (!usedIn.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_IN_USE",
                    "This question is used in " + describe(usedIn) + ". Remove it from the quiz first.");
        }
        question.delete(clock.instant());
    }

    private void apply(Question question, QuestionRequest req) {
        List<String> options = req.options().stream().map(String::trim).toList();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            if (!seen.add(options.get(i).toLowerCase(Locale.ROOT))) {
                throw ApiException.invalidField("options[" + i + "]", "Each answer must be different");
            }
        }
        question.update(req.text().trim(), options, req.correctOption(), req.timeLimitSeconds(), clock.instant());
    }

    private Question find(Long id) {
        return questions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question not found."));
    }

    private static String describe(List<String> quizTitles) {
        String first = "\"" + quizTitles.getFirst() + "\"";
        return switch (quizTitles.size()) {
            case 1 -> "the quiz " + first;
            case 2 -> "the quizzes " + first + " and 1 other";
            default -> "the quizzes " + first + " and " + (quizTitles.size() - 1) + " others";
        };
    }
}
