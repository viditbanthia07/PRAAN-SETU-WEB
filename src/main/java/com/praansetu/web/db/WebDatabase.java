package com.praansetu.web.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Access to the Praan-Setu SQLite database created by the desktop application.
 *
 * <p>The web console never creates or migrates the schema: the desktop application owns it. Location
 * resolution mirrors the desktop app: context parameter / {@code -Dpraansetu.home} / env {@code PRAANSETU_HOME}
 * / {@code %APPDATA%\PraanSetu} / {@code ~/.praansetu}.
 */
public class WebDatabase {

    public static final int REQUIRED_SCHEMA = 1;

    static {
        // Inside Tomcat, DriverManager's ServiceLoader scan does not see drivers in the webapp classloader.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("sqlite-jdbc missing from WEB-INF/lib", e);
        }
    }

    private final Path file;
    private final String url;

    public WebDatabase(Path file) {
        this.file = file.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.file;
    }

    public static Path resolveDefault(String contextOverride) {
        if (contextOverride != null && !contextOverride.isBlank()) {
            return Paths.get(contextOverride);
        }
        String home = System.getProperty("praansetu.home");
        if (home == null || home.isBlank()) {
            home = System.getenv("PRAANSETU_HOME");
        }
        if (home == null || home.isBlank()) {
            String appData = System.getenv("APPDATA");
            home = System.getProperty("os.name", "").toLowerCase().contains("win") && appData != null
                    ? Paths.get(appData, "PraanSetu").toString()
                    : Paths.get(System.getProperty("user.home"), ".praansetu").toString();
        }
        return Paths.get(home, "praansetu.db");
    }

    public Path file() {
        return file;
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    /** Returns null if the database is usable, otherwise a human-readable problem description. */
    public String problem() {
        if (!Files.isRegularFile(file)) {
            return "Database not found at " + file + ". Start the Praan-Setu desktop application once to create it.";
        }
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            int v = rs.next() ? rs.getInt(1) : 0;
            return v >= REQUIRED_SCHEMA ? null : "Database schema version " + v + " is older than required.";
        } catch (SQLException e) {
            return "Database cannot be opened: " + e.getMessage();
        }
    }
}
