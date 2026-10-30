package com.ayden.cinemabookingsystem.security.auth;

import com.ayden.cinemabookingsystem.user.AppUser;
import com.ayden.cinemabookingsystem.user.AppUserRepository;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalize user input
        String displayName = request.displayName().trim();

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 2. Check email before inserting
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        // 3. Hash the raw password
        String passwordHash = passwordEncoder.encode(
                request.password()
        );

        // 4. Create a CUSTOMER, never an ADMIN
        AppUser user = new AppUser(
                displayName,
                email,
                passwordHash
        );

        // 5. Save and force database constraints to run
        AppUser savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {

            // Handle a concurrent registration of the same email.
            if (hasSqlState(exception, "23505")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Email is already registered",
                        exception
                );
            }

            throw exception;
        }

        // 6. Load database-generated createdAt
        entityManager.refresh(savedUser);

        return RegisterResponse.from(savedUser);
    }

    private boolean hasSqlState(
            Throwable throwable,
            String expectedState
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SQLException sql) {
                if (expectedState.equals(sql.getSQLState())) {
                    return true;
                }

                SQLException next = sql.getNextException();

                while (next != null) {
                    if (expectedState.equals(next.getSQLState())) {
                        return true;
                    }
                    next = next.getNextException();
                }
            }

            current = current.getCause();
        }

        return false;
    }
}

