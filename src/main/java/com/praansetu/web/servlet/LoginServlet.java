package com.praansetu.web.servlet;

import com.praansetu.web.dao.UserDao;
import com.praansetu.web.security.CsrfTokens;
import com.praansetu.web.security.PasswordVerifier;
import com.praansetu.web.security.WebUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Responder login against the shared local user table. Same lock-out policy as the desktop app
 * (5 failures → 60 s lock). Only responder accounts may use the web console.
 */
@WebServlet("/login")
public class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MS = 60_000;
    private static final String GENERIC = "Invalid username or password.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (user(req) != null) {
            resp.sendRedirect(req.getContextPath() + "/app/alerts");
            return;
        }
        req.getSession(true);
        req.setAttribute("dbProblem", db().problem());
        render(req, resp, "login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (rejectIfCsrfInvalid(req, resp)) {
            return;
        }
        String username = text(req, "username", 32);
        String rawPassword = req.getParameter("password");
        char[] password = rawPassword == null ? new char[0] : rawPassword.toCharArray();
        try {
            String error = authenticate(req, username, password);
            if (error != null) {
                req.setAttribute("error", error);
                req.setAttribute("username", username);
                req.setAttribute("dbProblem", db().problem());
                render(req, resp, "login");
                return;
            }
            resp.sendRedirect(req.getContextPath() + "/app/alerts");
        } catch (SQLException e) {
            log("Login failed due to a database error", e);
            req.setAttribute("error", "The database is unavailable. Try again shortly.");
            render(req, resp, "login");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Returns an error message, or null on success (session established). */
    private String authenticate(HttpServletRequest req, String username, char[] password) throws SQLException {
        if (username == null || password.length == 0) {
            return "Enter username and password.";
        }
        long now = System.currentTimeMillis();
        Optional<UserDao.Credentials> found = users().findCredentials(username);
        if (found.isEmpty()) {
            PasswordVerifier.verify(password, "pbkdf2-sha256$210000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            audit().record("AUTH", "LOGIN_FAILED", "WARN", null, null, "Web login failed for '" + username + "': unknown username");
            return GENERIC;
        }
        UserDao.Credentials creds = found.get();
        if (creds.lockedUntil() != null && creds.lockedUntil() > now) {
            return "Account temporarily locked after repeated failed logins. Try again later.";
        }
        if (!PasswordVerifier.verify(password, creds.passwordHash())) {
            int attempts = creds.failedAttempts() + 1;
            boolean lock = attempts >= MAX_FAILURES;
            users().recordFailure(creds.user().id(), lock ? 0 : attempts, lock ? now + LOCKOUT_MS : null, now);
            audit().record("AUTH", "LOGIN_FAILED", "WARN", null, null, "Web login failed for '" + username + "': wrong password");
            return lock ? "Too many failed attempts. Account locked for 60 s." : GENERIC;
        }
        if (!"RESPONDER".equals(creds.role())) {
            audit().record("SECURITY", "ACCESS_DENIED", "WARN", creds.user().username(), null,
                    "Non-responder attempted to use the web console");
            return "The web console is available to responder accounts only.";
        }
        users().recordSuccess(creds.user().id(), now);
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate(); // prevent session fixation
        }
        HttpSession session = req.getSession(true);
        session.setAttribute(WebUser.SESSION_KEY, creds.user());
        CsrfTokens.tokenFor(session);
        audit().record("AUTH", "LOGIN_SUCCESS", "INFO", creds.user().username(), creds.user().id(),
                "Responder @" + creds.user().username() + " logged in to the web console");
        return null;
    }
}
