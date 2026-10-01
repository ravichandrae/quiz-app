package org.schoolmela.quiz.assignment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("""
            select a from Assignment a
            left join fetch a.student
            left join fetch a.group
            where a.quiz.id = :quizId
            order by a.assignedAt, a.id
            """)
    List<Assignment> findByQuizId(Long quizId);

    /** Assignments that reach the student directly, through one of their groups, or as "all students". */
    @Query("""
            select a from Assignment a
            join fetch a.quiz
            left join a.student s
            left join a.group g
            where a.targetType = org.schoolmela.quiz.assignment.Assignment.TargetType.ALL
               or s.id = :studentId
               or g.id in (select sg.id from StudentGroup sg join sg.members m where m.id = :studentId)
            """)
    List<Assignment> findVisibleTo(Long studentId);

    @Modifying
    @Query("delete from Assignment a where a.quiz.id = :quizId")
    void deleteByQuizId(Long quizId);
}
