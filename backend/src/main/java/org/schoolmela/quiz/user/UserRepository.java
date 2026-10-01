package org.schoolmela.quiz.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByMobile(String mobile);

    Optional<User> findByMobile(String mobile);

    /** Locks the row so concurrent wrong-PIN attempts are all counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.mobile = :mobile")
    Optional<User> findByMobileForUpdate(String mobile);
}
