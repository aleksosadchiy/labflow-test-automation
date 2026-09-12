package com.mocklab.service;

import com.mocklab.domain.User;
import com.mocklab.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates Sign In credentials against the seeded {@link User} accounts.
 * Plain string comparison — no password hashing — per the project's
 * "no production-grade auth" non-goal.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.password().equals(password));
    }
}
