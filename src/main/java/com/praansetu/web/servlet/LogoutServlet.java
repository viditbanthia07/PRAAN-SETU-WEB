package com.praansetu.web.servlet;

import com.praansetu.web.security.WebUser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/** Ends the session. POST + CSRF token so a third-party page cannot log the user out. */
@WebServlet("/logout")
public class LogoutServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (rejectIfCsrfInvalid(req, resp)) {
            return;
        }
        WebUser u = user(req);
        HttpSession s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        if (u != null) {
            try {
                audit().record("AUTH", "LOGOUT", "INFO", u.username(), u.id(), "@" + u.username() + " logged out of the web console");
            } catch (SQLException e) {
                log("Could not audit logout", e);
            }
        }
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
