<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- View objects are Java records; EL calls their accessors directly, e.g. a.severity(). --%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Praan-Setu · SOS alerts</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/app.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main>
    <div class="kpis">
        <div class="kpi critical"><span>Open (shown)</span><strong><c:out value="${openCount}"/></strong></div>
        <div class="kpi critical"><span>Critical open</span><strong><c:out value="${criticalCount}"/></strong></div>
    </div>
    <c:if test="${not empty error}"><div class="flash error"><c:out value="${error}"/></div></c:if>
    <form class="filters" method="get" action="${pageContext.request.contextPath}/app/alerts">
        <input name="q" placeholder="Search sender, description, location, node" value="<c:out value='${q}'/>" maxlength="80">
        <select name="severity">
            <option value="">All severities</option>
            <c:forEach var="s" items="${severities}">
                <option value="${s}" ${s == severity ? 'selected' : ''}><c:out value="${s}"/></option>
            </c:forEach>
        </select>
        <select name="status">
            <option value="OPEN" ${status == 'OPEN' ? 'selected' : ''}>Open (not resolved)</option>
            <option value="ALL" ${empty status ? 'selected' : ''}>All statuses</option>
            <c:forEach var="s" items="${statuses}">
                <option value="${s}" ${s == status ? 'selected' : ''}><c:out value="${s}"/></option>
            </c:forEach>
        </select>
        <select name="sort">
            <option value="priority" ${sort == 'priority' ? 'selected' : ''}>Sort: priority</option>
            <option value="newest" ${sort == 'newest' ? 'selected' : ''}>Sort: newest</option>
            <option value="oldest" ${sort == 'oldest' ? 'selected' : ''}>Sort: oldest</option>
        </select>
        <button type="submit">Apply</button>
    </form>
    <table>
        <thead><tr><th>Severity</th><th>Category</th><th>Sender</th><th>Location</th><th>Path</th><th>Status</th><th>Assigned</th><th>Raised</th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${alerts}">
            <tr class="${a.severity() == 'CRITICAL' and a.status() != 'RESOLVED' ? 'critical' : ''}">
                <td><span class="badge ${a.severity()}"><c:out value="${a.severity()}"/></span></td>
                <td><a href="${pageContext.request.contextPath}/app/alert?id=<c:out value="${a.id()}"/>"><c:out value="${a.category()}"/></a></td>
                <td><c:out value="${a.senderName()}"/></td>
                <td><c:out value="${empty a.location() ? '—' : a.location()}"/></td>
                <td><c:out value="${a.sourceNode()}"/> → <c:out value="${a.receivedNode()}"/></td>
                <td><span class="badge ${a.status()}"><c:out value="${a.status()}"/></span></td>
                <td><c:out value="${empty a.assignedName() ? '—' : a.assignedName()}"/></td>
                <td class="muted"><c:out value="${a.createdAt()}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty alerts}"><tr><td colspan="8" class="muted">No alerts match. Only alerts that reached a responder station are listed.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
