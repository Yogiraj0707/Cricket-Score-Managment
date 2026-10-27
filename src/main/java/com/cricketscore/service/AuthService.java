package com.cricketscore.service;

import com.cricketscore.model.AuthRequest;
import com.cricketscore.model.UserProfile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserProfile register(AuthRequest request) {
        String email = normalizeEmail(request.email());
        String password = request.password();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        try {
            jdbcTemplate.update("INSERT INTO app_user (name, email, password_hash) VALUES (?, ?, ?)",
                    normalizeName(request.name()), email, passwordEncoder.encode(password));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        return findByEmail(email).orElseThrow(() ->
                new IllegalStateException("Newly registered account could not be loaded"));
    }

    public UserProfile authenticate(String emailAddress, String password) {
        String email = normalizeEmail(emailAddress);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        var users = jdbcTemplate.query(
                "SELECT name, email, password_hash FROM app_user WHERE email = ?",
                (resultSet, rowNumber) -> new StoredUser(
                        resultSet.getString("name"),
                        resultSet.getString("email"),
                        resultSet.getString("password_hash")),
                email);
        if (users.isEmpty() || !passwordEncoder.matches(password, users.get(0).passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        StoredUser user = users.get(0);
        return new UserProfile(user.name(), user.email());
    }

    public Optional<UserProfile> findByEmail(String emailAddress) {
        return jdbcTemplate.query(
                        "SELECT name, email FROM app_user WHERE email = ?",
                        (resultSet, rowNumber) -> new UserProfile(
                                resultSet.getString("name"),
                                resultSet.getString("email")),
                        normalizeEmail(emailAddress))
                .stream()
                .findFirst();
    }

    public UserProfile updateName(String emailAddress, String name) {
        String cleanName = normalizeName(name);
        int updated = jdbcTemplate.update(
                "UPDATE app_user SET name = ? WHERE email = ?", cleanName, normalizeEmail(emailAddress));
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return findByEmail(emailAddress).orElseThrow(() ->
                new IllegalStateException("Updated account could not be loaded"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private record StoredUser(String name, String email, String passwordHash) {
    }
}
