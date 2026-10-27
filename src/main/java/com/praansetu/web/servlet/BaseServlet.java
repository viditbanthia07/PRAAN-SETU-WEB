package com.praansetu.web.servlet;

import com.praansetu.web.dao.AlertDao;
import com.praansetu.web.dao.AuditDao;
import com.praansetu.web.dao.MessageDao;
import com.praansetu.web.dao.UserDao;
import com.praansetu.web.db.WebDatabase;
import com.praansetu.web.security.CsrfTokens;
import com.praansetu.web.security.WebUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.regex.Pattern;

/** Shared helpers: DAO lookup, rendering JSPs from WEB-INF, CSRF checks, flash messages and input validation. */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    protected WebDatabase db() {
        return (WebDatabase) getServletContext().getAttribute("db");
    }

    protected UserDao users() {
        return (UserDao) getServletContext().getAttribute("userDao");
    }

    protected AlertDao alerts() {
        return (AlertDao) getServletContext().getAttribute("alertDao");
    }

    protected MessageDao messages() {
        return (MessageDao) getServletContext().getAttribute("messageDao");
    }

    protected AuditDao audit() {
        return (AuditDao) getServletContext().getAttribute("auditDao");
    }

    protected static WebUser user(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (WebUser) s.getAttribute(WebUser.SESSION_KEY);
    }

    /** Forwards to /WEB-INF/jsp/{view}.jsp (not directly reachable from the browser). */
    protected void render(HttpServletRequest req, HttpServletResponse resp, String view)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            req.setAttribute("csrf", CsrfTokens.tokenFor(session));
            Object flash = session.getAttribute("flash");
            if (flash != null) {
                req.setAttribute("flash", flash);
                req.setAttribute("flashError", session.getAttribute("flashError"));
                session.removeAttribute("flash");
                session.removeAttribute("flashError");
            }
        }
        req.setAttribute("me", user(req));
        req.getRequestDispatcher("/WEB-INF/jsp/" + view + ".jsp").forward(req, resp);
    }

    protected static void flash(HttpServletRequest req, String message, boolean error) {
        HttpSession s = req.getSession();
        s.setAttribute("flash", message);
        s.setAttribute("flashError", error);
    }

    protected static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** Trims and bounds an optional free-text parameter. */
    protected static String text(HttpServletRequest req, String name, int max) {
        String v = req.getParameter(name);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v.length() > max ? v.substring(0, max) : v;
    }

    protected static boolean rejectIfCsrfInvalid(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!CsrfTokens.isValid(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
            return true;
        }
        return false;
    }
}
