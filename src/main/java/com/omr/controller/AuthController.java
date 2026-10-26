package com.omr.controller;

import com.omr.model.Teacher;
import com.omr.repository.TeacherRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Teacher account registration, login, and logout endpoints. */
@WebServlet("/auth/*")
public class AuthController extends HttpServlet {
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private TeacherRepository teachers;
    @Override public void init()throws ServletException{teachers=new TeacherRepository();}

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        req.setCharacterEncoding("UTF-8");String path=req.getPathInfo();
        if("/login".equals(path)){login(req,resp);return;}
        if("/register".equals(path)){register(req,resp);return;}
        if("/logout".equals(path)){HttpSession s=req.getSession(false);if(s!=null)s.invalidate();resp.sendRedirect(req.getContextPath()+"/login.jsp?message=signed-out");return;}
        resp.sendError(404);
    }
    private void login(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String email=normalize(req.getParameter("email")),password=req.getParameter("password");
        Optional<Teacher> teacher=(email.isBlank()||password==null)?Optional.empty():teachers.authenticate(email,password);
        if(teacher.isEmpty()){resp.sendRedirect(req.getContextPath()+"/login.jsp?error=invalid");return;}
        HttpSession old=req.getSession(false);if(old!=null)old.invalidate();
        HttpSession session=req.getSession(true);session.setAttribute("teacher",teacher.get());session.setMaxInactiveInterval(30*60);
        resp.sendRedirect(req.getContextPath()+"/");
    }
    private void register(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String name=req.getParameter("name")==null?"":req.getParameter("name").trim();
        String email=normalize(req.getParameter("email"));String password=req.getParameter("password");String confirm=req.getParameter("confirmPassword");
        String err=null;
        if(name.length()<2||name.length()>120)err="name";
        else if(email.length()>320||!EMAIL.matcher(email).matches())err="email";
        else if(password==null||password.length()<10||password.length()>128)err="password";
        else if(!password.equals(confirm))err="confirm";
        else if(!teachers.create(name,email,password))err="exists";
        if(err!=null){resp.sendRedirect(req.getContextPath()+"/register.jsp?error="+err);return;}
        resp.sendRedirect(req.getContextPath()+"/login.jsp?message=registered");
    }
    private static String normalize(String email){return email==null?"":email.trim().toLowerCase(java.util.Locale.ROOT);}
}
