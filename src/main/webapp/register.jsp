<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.omr.model.Teacher" %>
<% if (session.getAttribute("teacher") instanceof Teacher) { response.sendRedirect(request.getContextPath()+"/"); return; } %>
<!DOCTYPE html><html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Create account · OMR Scanner</title><link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css?v=2">
</head><body class="auth-page"><main class="auth-shell"><a class="auth-brand" href="<%=request.getContextPath()%>/login.jsp"><span class="auth-mark">OMR</span><span>OMR Scanner</span></a>
<section class="auth-card"><div class="auth-eyebrow">TEACHER PORTAL</div><h1>Create account</h1><p class="auth-subtitle">Set up a teacher account to save quizzes and scan results.</p>
<% String error=request.getParameter("error"); if(error!=null){ String message="Please check your details and try again."; if("name".equals(error)) message="Enter a name between 2 and 120 characters."; else if("email".equals(error)) message="Enter a valid email address."; else if("password".equals(error)) message="Use a password between 10 and 128 characters."; else if("confirm".equals(error)) message="The passwords do not match."; else if("exists".equals(error)) message="An account already exists for this email."; %><div class="auth-alert"><%=message%></div><% } %>
<form action="<%=request.getContextPath()%>/auth/register" method="post" class="auth-form">
<label for="name">Full name</label><input id="name" name="name" type="text" autocomplete="name" required maxlength="120" placeholder="Your name">
<label for="email">Email address</label><input id="email" name="email" type="email" autocomplete="email" required maxlength="320" placeholder="teacher@school.edu">
<label for="password">Password</label><input id="password" name="password" type="password" autocomplete="new-password" required minlength="10" maxlength="128" placeholder="At least 10 characters">
<label for="confirmPassword">Confirm password</label><input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" required minlength="10" maxlength="128">
<button class="auth-submit" type="submit">Create teacher account <span aria-hidden="true">→</span></button>
</form><p class="auth-switch">Already registered? <a href="<%=request.getContextPath()%>/login.jsp">Sign in</a></p>
</section><p class="auth-footnote">Passwords are hashed. Teacher accounts are stored locally on this computer.</p></main></body></html>
