package dev.social.auth.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One login attempt, successful or not.
 *
 * <p>Read-only from Java: rows are written exclusively by
 * {@code sp_record_login_attempt}, so every column except the key is mapped
 * {@code insertable = false, updatable = false}. Mapping it at all is what lets
 * the lockout pre-check run as an ordinary JPA query rather than another
 * procedure.
 *
 * <p>Nothing here identifies the attempt beyond the username - no password, not
 * even its length. An audit trail that leaks what was typed is a liability.
 */
@Entity
@Table(name = "login_audit")
public class LoginAudit {

    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    @Column(name = "seq", nullable = false, insertable = false, updatable = false)
    private Long seq;

    @Column(name = "username", nullable = false, insertable = false, updatable = false, length = 50)
    private String username;

    @Column(name = "successful", nullable = false, insertable = false, updatable = false)
    private boolean successful;

    @Column(name = "failure_code", insertable = false, updatable = false, length = 40)
    private String failureCode;

    @Column(name = "ip_address", insertable = false, updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", insertable = false, updatable = false, length = 255)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false, insertable = false, updatable = false)
    private Instant attemptedAt;

    protected LoginAudit() {
        // required by JPA
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
