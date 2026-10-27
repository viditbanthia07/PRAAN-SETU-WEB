package com.praansetu.web.dao;

import com.praansetu.web.db.WebDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Message metadata for the web console. Message bodies are end-to-end encrypted between devices and are never
 * decrypted by the web console.
 */
public class MessageDao {

    public static final Set<String> STATUSES = Set.of("QUEUED", "SENT", "DELIVERED", "ACKNOWLEDGED", "FAILED");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm:ss").withZone(ZoneId.systemDefault());

    public record MessageView(String shortId, String kind, String sender, String recipient, String sourceNode,
                              String destNode, String status, int attempts, int maxAttempts, String route,
                              String createdAt, String deliveredAt, String note, int ciphertextBytes) {
    }

    private final WebDatabase db;

    public MessageDao(WebDatabase db) {
        this.db = db;
    }

    public List<MessageView> list(String status, String query, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT m.*, s.full_name AS sender_name, r.full_name AS recipient_name,"
                + " LENGTH(m.ciphertext) AS ct_len FROM messages m"
                + " LEFT JOIN users s ON s.id = m.sender_user_id LEFT JOIN users r ON r.id = m.recipient_user_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null && STATUSES.contains(status)) {
            sql.append(" AND m.status = ?");
            params.add(status);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (m.id LIKE ? OR m.source_node LIKE ? OR m.dest_node LIKE ? OR s.full_name LIKE ? OR m.last_route LIKE ?)");
            String like = "%" + query.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        sql.append(" ORDER BY m.created_at DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 1000)));
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<MessageView> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    long delivered = rs.getLong("delivered_at");
                    String deliveredAt = rs.wasNull() ? null : FMT.format(Instant.ofEpochMilli(delivered));
                    result.add(new MessageView(id.substring(0, Math.min(8, id.length())), rs.getString("kind"), rs.getString("sender_name"),
                            rs.getString("recipient_name"), rs.getString("source_node"), rs.getString("dest_node"),
                            rs.getString("status"), rs.getInt("attempts"), rs.getInt("max_attempts"),
                            rs.getString("last_route"), FMT.format(Instant.ofEpochMilli(rs.getLong("created_at"))),
                            deliveredAt, rs.getString("note"), rs.getInt("ct_len")));
                }
            }
            return result;
        }
    }
}
