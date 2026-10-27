<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Praan-Setu · Responder login</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/app.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main class="login">
    <section class="card narrow">
        <h1>Responder sign-in</h1>
        <p class="muted">Use the same local account as in the desktop application. Victim accounts cannot use this console.</p>
        <c:if test="${not empty dbProblem}"><div class="flash error"><c:out value="${dbProblem}"/></div></c:if>
        <c:if test="${not empty error}"><div class="flash error"><c:out value="${error}"/></div></c:if>
        <form method="post" action="${pageContext.request.contextPath}/login" autocomplete="off">
            <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
            <label>Username <input name="username" maxlength="32" required value="<c:out value='${username}'/>"></label>
            <label>Password <input name="password" type="password" maxlength="128" required></label>
            <button type="submit" class="primary">Sign in</button>
        </form>
    </section>
</main>
</body>
</html>
