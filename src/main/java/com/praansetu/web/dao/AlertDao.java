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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SOS alert queries and responder actions for the web console. Status changes use the same guarded
 * (compare-and-set) rules as the desktop application, so both front-ends can safely operate on one database.
 */
public class AlertDao {

    public static final Set<String> SEVERITIES = Set.of("LOW", "MODERATE", "HIGH", "CRITICAL");
    public static final Set<String> STATUSES = Set.of("PENDING", "QUEUED", "SENT", "DELIVERED", "ACKNOWLEDGED",
            "RESOLVED", "FAILED");
    private static final Map<String, String> CATEGORY_NAMES = Map.of("MEDICAL", "Medical emergency",
            "TRAPPED", "Trapped person", "FIRE", "Fire", "FLOOD", "Flood", "EARTHQUAKE", "Earthquake",
            "OTHER", "Other emergency");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Display-ready alert (JSP accesses fields through record accessor calls, e.g. {@code a.severity()}). */
    public record AlertView(String id, String shortId, String category, String severity, String status,
                            String description, String location, String senderName, String senderPhone,
                            String sourceNode, String receivedNode, String createdAt, String receivedAt,
                            String acknowledgedAt, String resolvedAt, String assignedName, String resolutionNote,
                            String messageId, String messageStatus) {
        public boolean canAcknowledge() {
            return acknowledgedAt == null && !"RESOLVED".equals(status);
        }

        public boolean canResolve() {
            return "ACKNOWLEDGED".equals(status);
        }

        public boolean canAssign() {
            return !"RESOLVED".equals(status);
        }
    }

    private static final String SELECT = "SELECT a.*, s.full_name AS sender_name, s.phone AS sender_phone,"
            + " r.full_name AS assigned_name, m.status AS message_status FROM emergency_alerts a"
            + " LEFT JOIN users s ON s.id = a.sender_user_id"
            + " LEFT JOIN users r ON r.id = a.assigned_responder_id"
            + " LEFT JOIN messages m ON m.id = a.message_id"
            + " WHERE a.received_at IS NOT NULL";

    private final WebDatabase db;

    public AlertDao(WebDatabase db) {
        this.db = db;
    }

    /**
     * Alerts that reached a responder station, filtered and sorted.
     *
     * @param severity one of {@link #SEVERITIES} or null
     * @param status   one of {@link #STATUSES}, "OPEN", or null
     * @param sort     "newest", "oldest" or anything else for priority
     */
    public List<AlertView> list(String severity, String status, String query, String sort) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> params = new ArrayList<>();
        if (severity != null && SEVERITIES.contains(severity)) {
            sql.append(" AND a.severity = ?");
            params.add(severity);
        }
        if ("OPEN".equals(status)) {
            sql.append(" AND a.status <> 'RESOLVED'");
        } else if (status != null && STATUSES.contains(status)) {
            sql.append(" AND a.status = ?");
            params.add(status);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (a.description LIKE ? OR a.location LIKE ? OR s.full_name LIKE ? OR a.source_node LIKE ?)");
            String like = "%" + query.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if ("newest".equals(sort)) {
            sql.append(" ORDER BY a.created_at DESC");
        } else if ("oldest".equals(sort)) {
            sql.append(" ORDER BY a.created_at ASC");
        } else {
            sql.append(" ORDER BY CASE WHEN a.status = 'RESOLVED' THEN 1 ELSE 0 END,"
                    + " CASE a.severity WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MODERATE' THEN 2 ELSE 1 END DESC,"
                    + " a.created_at DESC");
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<AlertView> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        }
    }

    public Optional<AlertView> find(String id) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(SELECT + " AND a.id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Acknowledges a received alert. Also marks the carrying SOS message as read so the desktop application's
     * retry processor sends the acknowledgement back to the victim's device over the mesh.
     */
    public boolean acknowledge(String id, String responderId, long now) throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = c.prepareStatement("UPDATE emergency_alerts SET status = 'ACKNOWLEDGED',"
                        + " acknowledged_by = ?, acknowledged_at = ?, assigned_responder_id = COALESCE(assigned_responder_id, ?),"
                        + " updated_at = ? WHERE id = ? AND received_at IS NOT NULL"
                        + " AND status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED')")) {
                    ps.setString(1, responderId);
                    ps.setLong(2, now);
                    ps.setString(3, responderId);
                    ps.setLong(4, now);
                    ps.setString(5, id);
                    updated = ps.executeUpdate();
                }
                if (updated == 1) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE messages SET read_at = ?, updated_at = ?"
                            + " WHERE alert_id = ? AND received_at IS NOT NULL AND read_at IS NULL")) {
                        ps.setLong(1, now);
                        ps.setLong(2, now);
                        ps.setString(3, id);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return updated == 1;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean assign(String id, String responderId, long now) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE emergency_alerts SET assigned_responder_id = ?, updated_at = ? WHERE id = ?"
                        + " AND received_at IS NOT NULL AND status <> 'RESOLVED'"
                        + " AND EXISTS (SELECT 1 FROM users WHERE id = ? AND role = 'RESPONDER')")) {
            ps.setString(1, responderId);
            ps.setLong(2, now);
            ps.setString(3, id);
            ps.setString(4, responderId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean resolve(String id, String responderId, String note, long now) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE emergency_alerts SET status = 'RESOLVED', resolved_by = ?, resolved_at = ?, resolution_note = ?,"
                        + " updated_at = ? WHERE id = ? AND status = 'ACKNOWLEDGED' AND received_at IS NOT NULL")) {
            ps.setString(1, responderId);
            ps.setLong(2, now);
            ps.setString(3, note);
            ps.setLong(4, now);
            ps.setString(5, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static String time(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : FMT.format(Instant.ofEpochMilli(v));
    }

    private static AlertView map(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new AlertView(id, id.substring(0, Math.min(8, id.length())),
                CATEGORY_NAMES.getOrDefault(rs.getString("category"), rs.getString("category")),
                rs.getString("severity"), rs.getString("status"), rs.getString("description"),
                rs.getString("location"), rs.getString("sender_name"), rs.getString("sender_phone"),
                rs.getString("source_node"), rs.getString("received_node_id"), time(rs, "created_at"),
                time(rs, "received_at"), time(rs, "acknowledged_at"), time(rs, "resolved_at"),
                rs.getString("assigned_name"), rs.getString("resolution_note"), rs.getString("message_id"),
                rs.getString("message_status"));
    }
}
