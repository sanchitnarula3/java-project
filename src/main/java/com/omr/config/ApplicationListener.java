package com.omr.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Initializes OpenCV native libraries when the web application starts. */
@WebListener
public class ApplicationListener implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event) {
        try { nu.pattern.OpenCV.loadLocally(); event.getServletContext().log("OpenCV loaded: " + org.opencv.core.Core.VERSION); }
        catch (Throwable error) { event.getServletContext().log("OpenCV native initialization failed", error); }
    }
}
