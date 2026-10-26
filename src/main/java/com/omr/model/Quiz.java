package com.omr.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Plain Java quiz model used by the servlet and in-memory storage. */
public class Quiz {
    private String id;
    private String title;
    private int numberOfQuestions;
    private int optionsPerQuestion = 4;
    private double maximumMarks = 10.0;
    private Map<Integer, String> answerKey = new HashMap<>();

    public Quiz() { this.id = UUID.randomUUID().toString().substring(0, 8); this.numberOfQuestions = 15; }
    public Quiz(String title, int numberOfQuestions) { this(); this.title = title; this.numberOfQuestions = numberOfQuestions; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getNumberOfQuestions() { return numberOfQuestions; }
    public void setNumberOfQuestions(int numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }
    public int getOptionsPerQuestion() { return optionsPerQuestion; }
    public void setOptionsPerQuestion(int optionsPerQuestion) { this.optionsPerQuestion = optionsPerQuestion; }
    public double getMaximumMarks() { return maximumMarks; }
    public void setMaximumMarks(double maximumMarks) { this.maximumMarks = maximumMarks; }
    public Map<Integer, String> getAnswerKey() { return answerKey; }
    public void setAnswerKey(Map<Integer, String> answerKey) { this.answerKey = answerKey; }
}
