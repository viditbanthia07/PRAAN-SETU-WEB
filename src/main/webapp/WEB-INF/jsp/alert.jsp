<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Praan-Setu · Alert <c:out value="${a.shortId()}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/app.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main class="split">
    <section class="card">
        <p><a href="${pageContext.request.contextPath}/app/alerts">← All alerts</a></p>
        <p><span class="badge ${a.severity()}"><c:out value="${a.severity()}"/></span>
            <span class="badge ${a.status()}"><c:out value="${a.status()}"/></span>
            <span class="muted">alert <c:out value="${a.shortId()}"/></span></p>
        <h1><c:out value="${a.category()}"/></h1>
        <p class="lead"><c:out value="${a.description()}"/></p>
        <dl>
            <dt>Sender</dt><dd><c:out value="${a.senderName()}"/> · <c:out value="${a.senderPhone()}"/> · device <c:out value="${a.sourceNode()}"/></dd>
            <dt>Location</dt><dd><c:out value="${empty a.location() ? 'Not provided' : a.location()}"/></dd>
            <dt>Raised</dt><dd><c:out value="${a.createdAt()}"/></dd>
            <dt>Received</dt><dd>Station <c:out value="${a.receivedNode()}"/> at <c:out value="${a.receivedAt()}"/></dd>
            <dt>Mesh delivery</dt><dd>Carrier message <c:out value="${a.messageStatus()}"/></dd>
            <dt>Acknowledged</dt><dd><c:out value="${empty a.acknowledgedAt() ? 'Not yet' : a.acknowledgedAt()}"/></dd>
            <dt>Assigned to</dt><dd><c:out value="${empty a.assignedName() ? 'Unassigned' : a.assignedName()}"/></dd>
            <dt>Resolution</dt><dd><c:out value="${empty a.resolvedAt() ? 'Open' : a.resolvedAt()}"/> <c:out value="${a.resolutionNote()}"/></dd>
        </dl>
    </section>
    <section class="card">
        <h2>Actions</h2>
        <form method="post" action="${pageContext.request.contextPath}/app/alert">
            <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
            <input type="hidden" name="id" value="<c:out value='${a.id()}'/>">
            <input type="hidden" name="action" value="acknowledge">
            <button type="submit" class="primary" ${a.canAcknowledge() ? '' : 'disabled'}>Acknowledge alert</button>
        </form>
        <form method="post" action="${pageContext.request.contextPath}/app/alert" class="row">
            <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
            <input type="hidden" name="id" value="<c:out value='${a.id()}'/>">
            <input type="hidden" name="action" value="assign">
            <select name="responderId">
                <c:forEach var="r" items="${responders}">
                    <option value="<c:out value='${r.id()}'/>"><c:out value="${r.fullName()}"/> · <c:out value="${r.organization()}"/></option>
                </c:forEach>
            </select>
            <button type="submit" ${a.canAssign() ? '' : 'disabled'}>Assign</button>
        </form>
        <form method="post" action="${pageContext.request.contextPath}/app/alert" class="row">
            <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
            <input type="hidden" name="id" value="<c:out value='${a.id()}'/>">
            <input type="hidden" name="action" value="resolve">
            <input name="note" maxlength="300" placeholder="Resolution note">
            <button type="submit" class="warn" ${a.canResolve() ? '' : 'disabled'}>Resolve</button>
        </form>
        <h2>Delivery trail</h2>
        <ul class="trail">
            <c:forEach var="e" items="${trail}">
                <li class="${e.level()}"><span class="mono"><c:out value="${e.time()}"/></span>
                    <strong><c:out value="${e.type()}"/></strong> <span class="muted"><c:out value="${e.node()}"/></span><br>
                    <c:out value="${e.details()}"/></li>
            </c:forEach>
        </ul>
    </section>
</main>
</body>
</html>
