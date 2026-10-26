package com.omr.model;

import java.time.Instant;
import java.util.Map;

/**
 * The result of scanning and evaluating a single answer sheet.
 * Contains per-question results and aggregate scoring.
 */
public class ScanResult {

    /** Status of the overall scan attempt */
    public enum ScanStatus {
        SUCCESS,           // Sheet detected and graded
        NO_SHEET_DETECTED, // No valid sheet found in the frame
        ALIGNMENT_ERROR,   // Sheet found but couldn't correct perspective
        PROCESSING_ERROR   // An unexpected error occurred
    }

    /** Per-question evaluation outcome */
    public enum QuestionResult {
        CORRECT,
        WRONG,
        BLANK,
        MULTIPLE,   // More than one bubble filled
        UNCERTAIN,  // Detection confidence is too low
        PENDING     // Not yet evaluated
    }

    private String quizId;
    private String rollNumber;
    private String studentName;
    private ScanStatus status;
    private String statusMessage;
    private Instant timestamp;

    // Per-question data
    private Map<Integer, String> detectedAnswers;    // Q# → detected option or status
    private Map<Integer, String> evaluationResults;  // Q# → CORRECT/WRONG/BLANK/...
    private Map<Integer, Double> confidenceScores;   // Q# → 0.0–1.0 detection confidence

    // Aggregate scores
    private int totalQuestions;
    private int correctCount;
    private int wrongCount;
    private int blankCount;
    private int ambiguousCount;
    private double scorePercentage;
    private double marksAwarded;
    private double maximumMarks;

    public ScanResult() {
        this.timestamp = Instant.now();
        this.status = ScanStatus.SUCCESS;
        this.rollNumber = "Unknown";
    }

    // --- Convenience builders ---

    public static ScanResult error(ScanStatus status, String message) {
        ScanResult result = new ScanResult();
        result.setStatus(status);
        result.setStatusMessage(message);
        return result;
    }

    public void computeAggregates() {
        if (evaluationResults == null) return;

        correctCount = 0;
        wrongCount = 0;
        blankCount = 0;
        ambiguousCount = 0;

        for (String outcome : evaluationResults.values()) {
            switch (outcome) {
                case "CORRECT"   -> correctCount++;
                case "WRONG"     -> wrongCount++;
                case "BLANK"     -> blankCount++;
                case "MULTIPLE", "UNCERTAIN" -> ambiguousCount++;
            }
        }

        totalQuestions = evaluationResults.size();
        scorePercentage = totalQuestions > 0
                ? Math.round((double) correctCount / totalQuestions * 10000.0) / 100.0
                : 0.0;
        marksAwarded = totalQuestions > 0
                ? Math.round((double) correctCount * maximumMarks / totalQuestions * 100.0) / 100.0
                : 0.0;
    }

    // --- Getters and Setters ---

    public String getQuizId() { return quizId; }
    public void setQuizId(String quizId) { this.quizId = quizId; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<Integer, String> getDetectedAnswers() { return detectedAnswers; }
    public void setDetectedAnswers(Map<Integer, String> detectedAnswers) { this.detectedAnswers = detectedAnswers; }

    public Map<Integer, String> getEvaluationResults() { return evaluationResults; }
    public void setEvaluationResults(Map<Integer, String> evaluationResults) { this.evaluationResults = evaluationResults; }

    public Map<Integer, Double> getConfidenceScores() { return confidenceScores; }
    public void setConfidenceScores(Map<Integer, Double> confidenceScores) { this.confidenceScores = confidenceScores; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public int getWrongCount() { return wrongCount; }
    public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }

    public int getBlankCount() { return blankCount; }
    public void setBlankCount(int blankCount) { this.blankCount = blankCount; }

    public int getAmbiguousCount() { return ambiguousCount; }
    public void setAmbiguousCount(int ambiguousCount) { this.ambiguousCount = ambiguousCount; }

    public double getScorePercentage() { return scorePercentage; }
    public void setScorePercentage(double scorePercentage) { this.scorePercentage = scorePercentage; }
    public double getMarksAwarded() { return marksAwarded; }
    public void setMarksAwarded(double marksAwarded) { this.marksAwarded = marksAwarded; }
    public double getMaximumMarks() { return maximumMarks; }
    public void setMaximumMarks(double maximumMarks) { this.maximumMarks = maximumMarks; }
}
