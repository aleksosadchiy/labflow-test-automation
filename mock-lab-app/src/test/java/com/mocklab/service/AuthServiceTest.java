package com.mocklab.service;

// @layer: unit
// @spec: 001-mock-lab-application

import com.mocklab.domain.User;
import com.mocklab.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthService#authenticate(String, String)}, per
 * functional-spec.md §2.1 (Sign In) and technical-considerations.md §8
 * ("AuthService: valid/invalid/blank credential combinations return the
 * right outcome/message"). No Spring context — a plain in-memory
 * {@link UserRepository}, matching the sibling {@code SampleServiceTest} /
 * {@code ProcessingJobServiceTest} style.
 *
 * @spec: 001-mock-lab-application
 */
class AuthServiceTest {

    private static final String SEEDED_USERNAME = "labtech";
    private static final String SEEDED_PASSWORD = "password123";

    private AuthService authService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        userRepository.save(new User(SEEDED_USERNAME, SEEDED_PASSWORD));

        authService = new AuthService(userRepository);
    }

    // @regression
    @Test
    void validCredentialsReturnTheAuthenticatedUser() {
        Optional<User> result = authService.authenticate(SEEDED_USERNAME, SEEDED_PASSWORD);

        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo(SEEDED_USERNAME);
    }

    // @regression
    @Test
    void wrongPasswordIsRejected() {
        Optional<User> result = authService.authenticate(SEEDED_USERNAME, "wrong-password");

        assertThat(result).isEmpty();
    }

    // @regression
    @Test
    void unknownUsernameIsRejected() {
        Optional<User> result = authService.authenticate("nobody", SEEDED_PASSWORD);

        assertThat(result).isEmpty();
    }

    // @regression
    @Test
    void blankUsernameIsRejected() {
        Optional<User> result = authService.authenticate("", SEEDED_PASSWORD);

        assertThat(result).isEmpty();
    }

    // @regression
    @Test
    void blankPasswordIsRejected() {
        Optional<User> result = authService.authenticate(SEEDED_USERNAME, "");

        assertThat(result).isEmpty();
    }

    @Test
    void usernameLookupIsCaseSensitive() {
        // Documents actual current behavior (ConcurrentHashMap key match is
        // case-sensitive) rather than an explicit acceptance criterion —
        // not tagged @regression since it isn't spec-mandated.
        Optional<User> result = authService.authenticate("LABTECH", SEEDED_PASSWORD);

        assertThat(result).isEmpty();
    }
}
