package dev.mehuol.finsight.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Accounts in the {@code app_user} table. Emails are stored in lower case. */
@Repository
public class UserRepository {

    /** An account row; {@code passwordHash} is a BCrypt hash. */
    public record UserRecord(long id, String email, String passwordHash) {
    }

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByEmail(String email) {
        return jdbc.query("SELECT id, email, password_hash FROM app_user WHERE email = ?",
                (rs, i) -> new UserRecord(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash")),
                email).stream().findFirst();
    }

    /** Returns the new user's id, or empty if the email is already registered. */
    public Optional<Long> insert(String email, String passwordHash) {
        return jdbc.query("""
                INSERT INTO app_user (email, password_hash) VALUES (?, ?)
                ON CONFLICT (email) DO NOTHING
                RETURNING id
                """, (rs, i) -> rs.getLong("id"), email, passwordHash).stream().findFirst();
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        return count == null ? 0 : count;
    }
}
