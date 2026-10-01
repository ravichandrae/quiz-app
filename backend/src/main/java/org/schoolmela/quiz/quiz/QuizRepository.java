package org.schoolmela.quiz.quiz;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface QuizRepository extends JpaRepository<Quiz, Long>, JpaSpecificationExecutor<Quiz> {

    /** Titles of the (not deleted) quizzes that include the question. */
    @Query("select distinct z.title from Quiz z join z.questions q where q.id = :questionId order by z.title")
    List<String> findTitlesUsingQuestion(Long questionId);
}
