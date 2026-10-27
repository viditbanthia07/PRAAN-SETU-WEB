<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Praan-Setu · Error</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/app.css">
</head>
<body>
<main class="login">
    <section class="card narrow">
        <h1>Request could not be completed</h1>
        <p>Status <c:out value="${requestScope['jakarta.servlet.error.status_code']}"/>:
            <c:out value="${requestScope['jakarta.servlet.error.message']}"/></p>
        <p><a href="${pageContext.request.contextPath}/app/alerts">Back to alerts</a></p>
    </section>
</main>
</body>
</html>
