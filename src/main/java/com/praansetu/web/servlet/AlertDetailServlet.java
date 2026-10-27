package com.praansetu.web.servlet;

import com.praansetu.web.dao.AlertDao;
import com.praansetu.web.security.WebUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Alert details (GET) and responder actions (POST: acknowledge, assign, resolve). POSTs require a CSRF token and
 * follow the Post/Redirect/Get pattern.
 */
@WebServlet("/app/alert")
public class AlertDetailServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String id = req.getParameter("id");
        if (!validId(id)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid alert id");
            return;
        }
        try {
            Optional<AlertDao.AlertView> alert = alerts().find(id);
            if (alert.isEmpty()) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Alert not found or not yet received by a station");
                return;
            }
            req.setAttribute("a", alert.get());
            req.setAttribute("trail", audit().trail(id, alert.get().messageId()));
            req.setAttribute("responders", users().findResponders());
        } catch (SQLException e) {
            log("Alert detail query failed", e);
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Database unavailable");
            return;
        }
        render(req, resp, "alert");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (rejectIfCsrfInvalid(req, resp)) {
            return;
        }
        String id = req.getParameter("id");
        String action = req.getParameter("action");
        if (!validId(id) || action == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request");
            return;
        }
        WebUser me = user(req);
        long now = System.currentTimeMillis();
        try {
            switch (action) {
                case "acknowledge" -> outcome(req, alerts().acknowledge(id, me.id(), now), me, id, "SOS_ACKNOWLEDGED",
                        "SOS acknowledged by " + me.fullName() + " via web console",
                        "Alert acknowledged. The desktop app will send the confirmation to the victim's device.",
                        "This alert cannot be acknowledged in its current state.");
                case "assign" -> {
                    String responder = req.getParameter("responderId");
                    boolean ok = validId(responder) && alerts().assign(id, responder, now);
                    outcome(req, ok, me, id, "SOS_ASSIGNED", "Assigned via web console by " + me.fullName(),
                            "Responder assigned.", "Choose a responder; resolved alerts cannot be reassigned.");
                }
                case "resolve" -> outcome(req, alerts().resolve(id, me.id(), text(req, "note", 300), now), me, id,
                        "SOS_RESOLVED", "Incident resolved by " + me.fullName() + " via web console",
                        "Incident resolved.", "Acknowledge the alert before resolving it.");
                default -> {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                    return;
                }
            }
        } catch (SQLException e) {
            log("Alert action failed", e);
            flash(req, "The action could not be saved: database unavailable.", true);
        }
        resp.sendRedirect(req.getContextPath() + "/app/alert?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));
    }

    private void outcome(HttpServletRequest req, boolean ok, WebUser me, String id, String eventType, String auditText,
                         String success, String failure) throws SQLException {
        if (ok) {
            audit().record("EMERGENCY", eventType, "INFO", me.username(), id, auditText);
        }
        flash(req, ok ? success : failure, !ok);
    }
}
