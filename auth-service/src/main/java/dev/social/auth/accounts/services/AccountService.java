package dev.social.auth.accounts.services;

import dev.social.auth.accounts.domain.UserAccount;
import dev.social.auth.accounts.dto.AccountResponse;
import dev.social.auth.accounts.dto.RegisterRequest;
import dev.social.auth.accounts.exceptions.AccountErrorCode;
import dev.social.auth.accounts.mappers.AccountMapper;
import dev.social.auth.accounts.repositories.AccountProcedureRepository;
import dev.social.auth.accounts.repositories.UserAccountRepository;
import dev.social.shared.error.ConflictException;
import dev.social.shared.error.NotFoundException;
import dev.social.shared.error.SqlErrorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Account lifecycle: registration and lookup.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository accounts;
    private final AccountProcedureRepository procedures;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper mapper;

    public AccountService(UserAccountRepository accounts,
                          AccountProcedureRepository procedures,
                          PasswordEncoder passwordEncoder,
                          AccountMapper mapper) {
        this.accounts = accounts;
        this.procedures = procedures;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    /**
     * Registers a new account.
     *
     * <p>Uniqueness is <em>not</em> pre-checked with a SELECT. Two simultaneous
     * sign-ups for the same username would both see "free" and both proceed;
     * one would then fail on the unique index with an unmapped 500. Instead the
     * insert is attempted through {@code sp_register_user}, which decides
     * atomically, and its {@code P0001} signal is translated here. Let the
     * database arbitrate - it is the only component that can.
     */
    @Transactional
    public AccountResponse register(RegisterRequest request) {
        String passwordHash = passwordEncoder.encode(request.password());

        try {
            UUID userId = procedures.registerUser(
                    request.username(), request.email(), passwordHash);

            log.info("Registered account {} ({})", request.username(), userId);

            return accounts.findById(userId)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> new IllegalStateException(
                            "sp_register_user returned id %s but no row was found".formatted(userId)));

        } catch (DataAccessException exception) {
            throw translateRegistrationFailure(exception, request);
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(UUID id) {
        return accounts.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of(
                        AccountErrorCode.ACCOUNT_NOT_FOUND, "Account", id));
    }

    @Transactional(readOnly = true)
    public AccountResponse findByUsername(String username) {
        return accounts.findByUsername(username)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of(
                        AccountErrorCode.ACCOUNT_NOT_FOUND, "Account", username));
    }

    /**
     * Turns whatever the database threw into an API-shaped error.
     *
     * <p>Anything that is not one of our own {@code RAISE EXCEPTION} codes is
     * re-thrown untouched, so a genuine infrastructure failure keeps surfacing
     * as a 500 instead of being disguised as a conflict.
     */
    private RuntimeException translateRegistrationFailure(DataAccessException exception,
                                                          RegisterRequest request) {

        return SqlErrorSupport.raisedBusinessCode(exception)
                .map(procedureCode -> {
                    AccountErrorCode errorCode = AccountErrorCode.fromProcedureCode(procedureCode);
                    log.info("Registration rejected for '{}': {}", request.username(), procedureCode);

                    String detail = switch (errorCode) {
                        case USERNAME_ALREADY_EXISTS ->
                                "The username '%s' is already taken".formatted(request.username());
                        case EMAIL_ALREADY_EXISTS ->
                                "The email '%s' is already registered".formatted(request.email());
                        default -> "The account could not be created";
                    };
                    return (RuntimeException) new ConflictException(errorCode, detail);
                })
                .orElse(exception);
    }
}
