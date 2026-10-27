package com.praansetu.web.filter;

import com.praansetu.web.dao.UserDao;
import com.praansetu.web.security.WebUser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Protects /app/*: requires a logged-in responder. The role is re-checked against the database on every request
 * so a demoted or deleted account loses access immediately.
 */
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);
        WebUser user = session == null ? null : (WebUser) session.getAttribute(WebUser.SESSION_KEY);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        UserDao users = (UserDao) req.getServletContext().getAttribute("userDao");
        try {
            if (!users.isResponder(user.id())) {
                session.invalidate();
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Responder role required");
                return;
            }
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Database unavailable");
            return;
        }
        chain.doFilter(request, response);
    }
}
