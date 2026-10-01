package org.schoolmela.quiz.group;

import java.util.List;
import java.util.Optional;
import org.schoolmela.quiz.group.GroupDtos.GroupSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    @Query("""
            select new org.schoolmela.quiz.group.GroupDtos$GroupSummary(g.id, g.name, size(g.members), g.createdAt)
            from StudentGroup g
            order by lower(g.name)
            """)
    List<GroupSummary> findAllSummaries();

    Optional<StudentGroup> findFirstByNameIgnoreCase(String name);
}
