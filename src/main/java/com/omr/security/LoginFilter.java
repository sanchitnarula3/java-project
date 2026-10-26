package com.omr.security;

import com.omr.model.Teacher;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;

/** Requires an authenticated teacher session for all API endpoints. */
@WebFilter("/api/*")
public class LoginFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {
        HttpServletRequest req=(HttpServletRequest)request;HttpServletResponse resp=(HttpServletResponse)response;
        HttpSession session=req.getSession(false);
        if(session==null||!(session.getAttribute("teacher") instanceof Teacher)){
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Please log in to continue\"}");return;
        }
        chain.doFilter(request,response);
    }
}
