package com.mocklab.repository;

import com.mocklab.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link User} accounts, keyed by username.
 * Nothing survives a JVM restart — {@link com.mocklab.config.DataSeeder}
 * repopulates it on every boot.
 */
@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, User> usersByUsername = new ConcurrentHashMap<>();

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public User save(User user) {
        usersByUsername.put(user.username(), user);
        return user;
    }
}
