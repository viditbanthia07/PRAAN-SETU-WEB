package com.praansetu.web.servlet;

import com.praansetu.web.dao.AlertDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** SOS alerts that reached a responder station, with severity/status filters, search and sorting. */
@WebServlet("/app/alerts")
public class AlertListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String severity = whitelist(req.getParameter("severity"), AlertDao.SEVERITIES);
        String status = req.getParameter("status") == null ? "OPEN"
                : "OPEN".equals(req.getParameter("status")) ? "OPEN" : whitelist(req.getParameter("status"), AlertDao.STATUSES);
        String sortParam = req.getParameter("sort");
        String sort = sortParam != null && Set.of("newest", "oldest").contains(sortParam) ? sortParam : "priority";
        String q = text(req, "q", 80);
        try {
            List<AlertDao.AlertView> list = alerts().list(severity, status, q, sort);
            req.setAttribute("alerts", list);
            req.setAttribute("openCount", list.stream().filter(a -> !"RESOLVED".equals(a.status())).count());
            req.setAttribute("criticalCount", list.stream().filter(a -> "CRITICAL".equals(a.severity())
                    && !"RESOLVED".equals(a.status())).count());
        } catch (SQLException e) {
            log("Alert query failed", e);
            req.setAttribute("error", "Could not load alerts: the database is unavailable.");
            req.setAttribute("alerts", List.of());
        }
        req.setAttribute("severity", severity);
        req.setAttribute("status", status);
        req.setAttribute("sort", sort);
        req.setAttribute("q", q);
        req.setAttribute("severities", List.of("LOW", "MODERATE", "HIGH", "CRITICAL"));
        req.setAttribute("statuses", List.of("PENDING", "QUEUED", "SENT", "DELIVERED", "ACKNOWLEDGED", "RESOLVED", "FAILED"));
        render(req, resp, "alerts");
    }

    private static String whitelist(String v, Set<String> allowed) {
        return v != null && allowed.contains(v) ? v : null;
    }
}
