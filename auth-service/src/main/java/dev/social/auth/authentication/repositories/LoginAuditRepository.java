package dev.social.auth.authentication.repositories;

import dev.social.auth.authentication.domain.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the audit trail. Writes go through
 * {@code sp_record_login_attempt}, never through this interface.
 */
public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {


    @Query("""
            SELECT COUNT(a)
              FROM LoginAudit a
             WHERE a.username = LOWER(:username)
               AND a.successful = FALSE
               AND a.attemptedAt > :since
               AND a.seq > COALESCE(
                     (SELECT MAX(s.seq)
                        FROM LoginAudit s
                       WHERE s.username = LOWER(:username)
                         AND s.successful = TRUE), 0)
            """)
    long countRecentFailures(@Param("username") String username, @Param("since") Instant since);


    @Query("""
            SELECT a
              FROM LoginAudit a
             WHERE a.username = LOWER(:username)
             ORDER BY a.seq DESC
            """)
    List<LoginAudit> findRecentByUsername(@Param("username") String username);
}
