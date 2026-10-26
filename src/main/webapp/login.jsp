<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.omr.model.Teacher" %>
<% if (session.getAttribute("teacher") instanceof Teacher) { response.sendRedirect(request.getContextPath()+"/"); return; } %>
<!DOCTYPE html><html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Teacher sign in · OMR Scanner</title><link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css?v=2">
</head><body class="auth-page"><main class="auth-shell"><a class="auth-brand" href="<%=request.getContextPath()%>/login.jsp"><span class="auth-mark">OMR</span><span>OMR Scanner</span></a>
<section class="auth-card"><div class="auth-eyebrow">TEACHER PORTAL</div><h1>Welcome back</h1><p class="auth-subtitle">Sign in to manage quizzes and scan answer sheets.</p>
<% String error=request.getParameter("error"); if("invalid".equals(error)){ %><div class="auth-alert">Email or password is incorrect.</div><% } if("registered".equals(request.getParameter("message"))){ %><div class="auth-success">Account created. Sign in to continue.</div><% } if("signed-out".equals(request.getParameter("message"))){ %><div class="auth-success">You have signed out.</div><% } %>
<form action="<%=request.getContextPath()%>/auth/login" method="post" class="auth-form">
<label for="email">Email address</label><input id="email" name="email" type="email" autocomplete="username" required maxlength="320" placeholder="teacher@school.edu">
<label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" required>
<button class="auth-submit" type="submit">Sign in <span aria-hidden="true">→</span></button>
</form><p class="auth-switch">New to OMR Scanner? <a href="<%=request.getContextPath()%>/register.jsp">Create a teacher account</a></p>
</section><p class="auth-footnote">Teacher accounts and quiz records are stored locally on this computer.</p></main></body></html>
