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

/** Writes web-console actions to, and reads alert trails from, the shared audit log. */
public class AuditDao {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public record EventView(String time, String type, String level, String node, String actor, String details) {
    }

    private final WebDatabase db;

    public AuditDao(WebDatabase db) {
        this.db = db;
    }

    public void record(String category, String type, String level, String actor, String referenceId, String details)
            throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_events(created_at, category, event_type, level, actor, reference_id, details)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, category);
            ps.setString(3, type);
            ps.setString(4, level);
            ps.setString(5, actor);
            ps.setString(6, referenceId);
            ps.setString(7, details);
            ps.executeUpdate();
        }
    }

    /** Events for an alert and its carrying message, oldest first. */
    public List<EventView> trail(String alertId, String messageId) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM audit_events WHERE reference_id = ? OR reference_id = ? ORDER BY id LIMIT 500")) {
            ps.setString(1, alertId);
            ps.setString(2, messageId == null ? alertId : messageId);
            List<EventView> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String node = rs.getString("node_id");
                    String peer = rs.getString("peer_node_id");
                    result.add(new EventView(FMT.format(Instant.ofEpochMilli(rs.getLong("created_at"))),
                            rs.getString("event_type"), rs.getString("level"),
                            node == null ? "" : node + (peer == null ? "" : "→" + peer),
                            rs.getString("actor"), rs.getString("details")));
                }
            }
            return result;
        }
    }
}
