package dev.mehuol.finsight.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mehuol.finsight.exception.AccountExistsException;
import dev.mehuol.finsight.repository.ChatRepository;
import dev.mehuol.finsight.repository.UserRepository;

/** Sign-up. Signing in is handled by Spring Security (see SecurityConfig). */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt only uses the first 72 bytes of a password.
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final ChatRepository chats;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, ChatRepository chats, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.chats = chats;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an account and returns the normalized (lower-case) email. */
    @Transactional
    public String register(String email, String password) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("The password is too long");
        }

        long userId = users.insert(normalized, passwordEncoder.encode(password))
                .orElseThrow(AccountExistsException::new);
        if (users.count() == 1) {
            // Chats created before accounts existed belong to whoever sets the app up first.
            int claimed = chats.assignUnowned(userId);
            if (claimed > 0) {
                log.info("First account {} took over {} existing chat(s)", normalized, claimed);
            }
        }
        return normalized;
    }
}
