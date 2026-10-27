# Praan-Setu Web Console

The **web version of Praan-Setu**, a secure offline disaster communication and emergency response system.
This console lets **responders** (rescue teams) manage SOS alerts from a browser.

Built with **Java · Jakarta Servlets · JSP/JSTL · JDBC · SQLite · Apache Tomcat 10.1**.

## Features
- **Responder login.** Passwords are checked against PBKDF2 hashes, accounts lock after 5 failed attempts, and only responders can sign in.
- **SOS alert list.** Filter by severity and status, search, and sort by priority, newest or oldest.
- **Alert details.** Sender, location, timestamps, delivery status and the full delivery trail.
- **Actions.** Acknowledge, assign to a responder, and resolve with a note.
- **Message history.** Delivery metadata only; message content stays end-to-end encrypted.

## Security
- CSRF tokens on every form, and session-fixation protection.
- HttpOnly + SameSite cookies, and a 30-minute session timeout.
- Output escaping (`<c:out>`) and whitelisted input.
- CSP, X-Frame-Options and nosniff headers.
- The responder role is re-checked on every request.
- JSPs contain no SQL: servlets call DAOs, which use prepared statements.

## Project structure
```
src/main/java/com/praansetu/web/
  servlet/   LoginServlet, LogoutServlet, AlertListServlet, AlertDetailServlet, MessageHistoryServlet
  filter/    AuthFilter, SecurityHeadersFilter
  dao/       UserDao, AlertDao, MessageDao, AuditDao
  security/  PasswordVerifier, CsrfTokens, WebUser
  db/        WebDatabase
src/main/webapp/
  WEB-INF/jsp/   login, alerts, alert, messages, error pages
  WEB-INF/web.xml
  static/app.css
```

## How to run
1. Install **JDK 17+** and **Apache Tomcat 10.1**.
2. Build the WAR: `mvnw.cmd package` (Windows) or `./mvnw package`. The output is `target/praan-setu-web.war`.
3. Copy the WAR into Tomcat's `webapps` folder and start Tomcat.
4. Open <http://localhost:8080/praan-setu-web/>.

The console reads the SQLite database (`praansetu.db`) created by the Praan-Setu desktop application. Point it
at the database with the Tomcat option `-Dpraansetu.home=<folder containing praansetu.db>`, or set the
`praansetu.database` context parameter in `WEB-INF/web.xml`.

Demo responder login (local demo database): `rescue1` / `Responder@123`.

## Tests
`mvnw.cmd test` runs the DAO tests. The console was also deployed on Tomcat 10.1.48 and passed 22 HTTP checks:
login, CSRF rejection, victim denial, security headers, filters, actions, HTML escaping and logout.
