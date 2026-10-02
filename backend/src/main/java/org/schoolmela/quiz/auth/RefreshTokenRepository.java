package org.schoolmela.quiz.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Locks the row so a token can only be rotated once, even under concurrent refreshes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(Long userId, Instant now);

    /** Removes tokens that can no longer be used: expired, or revoked before {@code revokedBefore}. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now or t.revokedAt < :revokedBefore")
    int deleteUnusable(Instant now, Instant revokedBefore);
}
