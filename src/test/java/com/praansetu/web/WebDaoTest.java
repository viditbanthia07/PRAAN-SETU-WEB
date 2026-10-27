package com.praansetu.web;

import com.praansetu.web.dao.AlertDao;
import com.praansetu.web.dao.AuditDao;
import com.praansetu.web.dao.MessageDao;
import com.praansetu.web.dao.UserDao;
import com.praansetu.web.db.WebDatabase;
import com.praansetu.web.security.PasswordVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Web console data access against a database built from the desktop module's own migration script. */
class WebDaoTest {

    /** Copy of the desktop app's schema, bundled so this module also builds as a standalone repository. */
    private static final Path SCHEMA = Path.of("src/test/resources/schema/V1__initial_schema.sql");

    @TempDir
    Path dir;
    private WebDatabase db;
    private AlertDao alerts;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Files.exists(SCHEMA), "desktop schema script not found (run from web-module directory)");
        db = new WebDatabase(dir.resolve("praansetu.db"));
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, script TEXT, applied_at INTEGER)");
            s.execute("INSERT INTO schema_version VALUES (1, 'V1', 0)");
            for (String stmt : Files.readString(SCHEMA, StandardCharsets.UTF_8).split(";\\s*\\n")) {
                String sql = stmt.lines().filter(l -> !l.trim().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim();
                if (!sql.isEmpty()) {
                    s.execute(sql);
                }
            }
            s.execute("INSERT INTO nodes(id,label,public_key,private_key,created_at,updated_at) VALUES ('A','A',x'00',x'00',0,0),('D','D',x'00',x'00',0,0)");
            s.execute("INSERT INTO users(id,username,full_name,role,password_hash,node_id,created_at,updated_at) VALUES"
                    + " ('v1','asha','Asha','VICTIM','" + hash("Victim@123") + "','A',0,0),"
                    + " ('r1','rescue1','Meera','RESPONDER','" + hash("Responder@123") + "','D',0,0),"
                    + " ('r2','medic1','Kabir','RESPONDER','x','D',0,0)");
            s.execute("INSERT INTO emergency_alerts(id,sender_user_id,source_node,category,severity,description,status,message_id,received_node_id,received_at,created_at,updated_at)"
                    + " VALUES ('al-1','v1','A','MEDICAL','CRITICAL','<script>alert(1)</script>','DELIVERED','m-1','D',10,5,10),"
                    + " ('al-2','v1','A','FIRE','LOW','Small fire','QUEUED','m-2',NULL,NULL,6,6)");
            s.execute("INSERT INTO messages(id,kind,sender_user_id,source_node,dest_node,alert_id,priority,status,max_attempts,hop_limit,created_at,updated_at,expires_at,received_at,ciphertext)"
                    + " VALUES ('m-1','SOS','v1','A','D','al-1',140,'DELIVERED',4,8,5,10,999999999999,10,x'0102030405')");
        }
        alerts = new AlertDao(db);
    }

    private static String hash(String password) throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 10_000, 256)).getEncoded();
        return "pbkdf2-sha256$10000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(h);
    }

    @Test
    void verifiesDesktopPasswordFormat() throws Exception {
        String stored = hash("Responder@123");
        assertTrue(PasswordVerifier.verify("Responder@123".toCharArray(), stored));
        assertFalse(PasswordVerifier.verify("wrong".toCharArray(), stored));
        assertFalse(PasswordVerifier.verify("x".toCharArray(), "garbage"));
    }

    @Test
    void databaseProblemsAreReported() {
        assertEquals(null, db.problem());
        assertNotNull(new WebDatabase(dir.resolve("missing.db")).problem());
    }

    @Test
    void listsOnlyAlertsReceivedByAStation() throws Exception {
        var list = alerts.list(null, null, null, "priority");
        assertEquals(1, list.size(), "the QUEUED alert has not reached a station");
        assertEquals("al-1", list.get(0).id());
        assertTrue(alerts.list("LOW", null, null, null).isEmpty());
        assertEquals(1, alerts.list(null, "OPEN", "script", null).size());
    }

    @Test
    void responderActionsFollowTheStateMachine() throws Exception {
        assertFalse(alerts.resolve("al-1", "r1", "done", 20), "cannot resolve before acknowledging");
        assertFalse(alerts.acknowledge("al-2", "r1", 20), "cannot acknowledge an alert no station received");
        assertTrue(alerts.acknowledge("al-1", "r1", 20));
        assertFalse(alerts.acknowledge("al-1", "r1", 21), "cannot acknowledge twice");
        assertFalse(alerts.assign("al-1", "v1", 22), "victims cannot be assignees");
        assertTrue(alerts.assign("al-1", "r2", 22));
        assertTrue(alerts.resolve("al-1", "r1", "evacuated", 23));
        assertFalse(alerts.assign("al-1", "r1", 24), "resolved alerts cannot be reassigned");
        var a = alerts.find("al-1").orElseThrow();
        assertEquals("RESOLVED", a.status());
        assertEquals("Kabir", a.assignedName());
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT read_at FROM messages WHERE id='m-1'");
             var rs = ps.executeQuery()) {
            rs.next();
            assertEquals(20, rs.getLong(1), "acknowledgement marks the SOS read so the desktop sends the mesh receipt");
        }
    }

    @Test
    void usersAndMessagesAndAudit() throws Exception {
        UserDao users = new UserDao(db);
        assertEquals("RESPONDER", users.findCredentials("rescue1").orElseThrow().role());
        assertTrue(users.isResponder("r1"));
        assertFalse(users.isResponder("v1"));
        assertEquals(2, users.findResponders().size());
        var msgs = new MessageDao(db).list(null, null, 50);
        assertEquals(1, msgs.size());
        assertEquals(5, msgs.get(0).ciphertextBytes());
        AuditDao audit = new AuditDao(db);
        audit.record("EMERGENCY", "SOS_ACKNOWLEDGED", "INFO", "rescue1", "al-1", "via web");
        assertEquals(1, audit.trail("al-1", "m-1").size());
    }
}
