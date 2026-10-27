package com.praansetu.web.dao;

import com.praansetu.web.db.WebDatabase;
import com.praansetu.web.security.WebUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads users for web login and the assignment drop-down. */
public class UserDao {

    /** Login data for one user. */
    public record Credentials(WebUser user, String role, String passwordHash, int failedAttempts, Long lockedUntil) {
    }

    private final WebDatabase db;

    public UserDao(WebDatabase db) {
        this.db = db;
    }

    public Optional<Credentials> findCredentials(String username) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, username, full_name, organization, role, password_hash, failed_attempts, locked_until"
                        + " FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long locked = rs.getLong("locked_until");
                Long lockedUntil = rs.wasNull() ? null : locked;
                return Optional.of(new Credentials(new WebUser(rs.getString("id"), rs.getString("username"),
                        rs.getString("full_name"), rs.getString("organization")), rs.getString("role"),
                        rs.getString("password_hash"), rs.getInt("failed_attempts"), lockedUntil));
            }
        }
    }

    public void recordFailure(String userId, int attempts, Long lockedUntil, long now) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, attempts);
            if (lockedUntil == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setLong(2, lockedUntil);
            }
            ps.setLong(3, now);
            ps.setString(4, userId);
            ps.executeUpdate();
        }
    }

    public void recordSuccess(String userId, long now) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = ?, updated_at = ? WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setLong(2, now);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    public List<WebUser> findResponders() throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, username, full_name, organization FROM users WHERE role = 'RESPONDER' ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            List<WebUser> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new WebUser(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
            return result;
        }
    }

    public boolean isResponder(String userId) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM users WHERE id = ? AND role = 'RESPONDER'")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
