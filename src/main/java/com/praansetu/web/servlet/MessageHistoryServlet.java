package com.praansetu.web.servlet;

import com.praansetu.web.dao.MessageDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Message delivery metadata (content stays end-to-end encrypted and is never shown). */
@WebServlet("/app/messages")
public class MessageHistoryServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String status = req.getParameter("status");
        status = status != null && MessageDao.STATUSES.contains(status) ? status : null;
        String q = text(req, "q", 80);
        try {
            req.setAttribute("messages", messages().list(status, q, 300));
        } catch (SQLException e) {
            log("Message query failed", e);
            req.setAttribute("error", "Could not load messages: the database is unavailable.");
            req.setAttribute("messages", List.of());
        }
        req.setAttribute("status", status);
        req.setAttribute("q", q);
        req.setAttribute("statuses", List.of("QUEUED", "SENT", "DELIVERED", "ACKNOWLEDGED", "FAILED"));
        render(req, resp, "messages");
    }
}
