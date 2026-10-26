package com.omr.model;

public class Teacher {
    private final String name;
    private final String email;
    public Teacher(String name, String email) { this.name=name; this.email=email; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}
