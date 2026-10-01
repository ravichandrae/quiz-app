package org.schoolmela.quiz.attempt;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AttemptRepository extends JpaRepository<Attempt, Long>, JpaSpecificationExecutor<Attempt> {

    /** Locks the attempt so concurrent requests (e.g. a double tap) are handled one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.quizId = :quizId and a.studentId = :studentId")
    Optional<Attempt> findByQuizAndStudentForUpdate(Long quizId, Long studentId);

    List<Attempt> findByStudentId(Long studentId);

    List<Attempt> findByStudentIdAndStatusOrderByFinishedAtDesc(Long studentId, Attempt.Status status);
}
