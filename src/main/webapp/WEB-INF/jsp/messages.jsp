<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Praan-Setu · Message history</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/app.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main>
    <h1>Message history</h1>
    <p class="muted">Delivery metadata only. Message content is end-to-end encrypted between devices and is never decrypted here.</p>
    <c:if test="${not empty error}"><div class="flash error"><c:out value="${error}"/></div></c:if>
    <form class="filters" method="get" action="${pageContext.request.contextPath}/app/messages">
        <input name="q" placeholder="Search id, node, sender, route" value="<c:out value='${q}'/>" maxlength="80">
        <select name="status">
            <option value="">All statuses</option>
            <c:forEach var="s" items="${statuses}">
                <option value="${s}" ${s == status ? 'selected' : ''}><c:out value="${s}"/></option>
            </c:forEach>
        </select>
        <button type="submit">Apply</button>
    </form>
    <table>
        <thead><tr><th>Created</th><th>Id</th><th>Kind</th><th>Sender</th><th>From → To</th><th>Status</th><th>Attempts</th><th>Route</th><th>Delivered</th><th>Payload</th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${messages}">
            <tr>
                <td class="muted"><c:out value="${m.createdAt()}"/></td>
                <td class="mono"><c:out value="${m.shortId()}"/></td>
                <td><c:out value="${m.kind()}"/></td>
                <td><c:out value="${m.sender()}"/></td>
                <td><c:out value="${m.sourceNode()}"/> → <c:out value="${empty m.destNode() ? 'station' : m.destNode()}"/></td>
                <td><span class="badge ${m.status()}"><c:out value="${m.status()}"/></span></td>
                <td><c:out value="${m.attempts()}"/>/<c:out value="${m.maxAttempts()}"/></td>
                <td><c:out value="${empty m.route() ? '—' : m.route()}"/></td>
                <td class="muted"><c:out value="${empty m.deliveredAt() ? '—' : m.deliveredAt()}"/></td>
                <td class="muted">🔒 <c:out value="${m.ciphertextBytes()}"/> B</td>
            </tr>
        </c:forEach>
        <c:if test="${empty messages}"><tr><td colspan="10" class="muted">No messages.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
