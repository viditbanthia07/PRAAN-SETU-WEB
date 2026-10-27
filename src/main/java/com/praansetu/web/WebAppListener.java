package com.praansetu.web;

import com.praansetu.web.dao.AlertDao;
import com.praansetu.web.dao.AuditDao;
import com.praansetu.web.dao.MessageDao;
import com.praansetu.web.dao.UserDao;
import com.praansetu.web.db.WebDatabase;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Logger;

/** Creates the database access objects once per web application and shares them via the servlet context. */
@WebListener
public class WebAppListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(WebAppListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        WebDatabase db = new WebDatabase(WebDatabase.resolveDefault(ctx.getInitParameter("praansetu.database")));
        ctx.setAttribute("db", db);
        ctx.setAttribute("userDao", new UserDao(db));
        ctx.setAttribute("alertDao", new AlertDao(db));
        ctx.setAttribute("messageDao", new MessageDao(db));
        ctx.setAttribute("auditDao", new AuditDao(db));
        String problem = db.problem();
        LOG.info("Praan-Setu web console using " + db.file() + (problem == null ? "" : " – " + problem));
    }
}
