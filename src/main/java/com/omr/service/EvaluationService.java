package com.omr.service;

import com.omr.model.ScanResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluation engine that compares detected answers against the teacher's
 * answer key and produces per-question outcomes with aggregate scoring.
 *
 * This is intentionally separated from the image-processing pipeline so
 * it can be tested and improved independently.
 */
public class EvaluationService {

    /**
     * Evaluate detected answers against the answer key.
     *
     * @param answerKey       teacher's correct answers (Q# → option letter)
     * @param detectedAnswers scanner's detected answers (Q# → option letter or status)
     * @param totalQuestions  expected number of questions on the sheet
     * @return per-question result map (Q# → CORRECT/WRONG/BLANK/MULTIPLE/UNCERTAIN/PENDING)
     */
    public Map<Integer, String> evaluate(
            Map<Integer, String> answerKey,
            Map<Integer, String> detectedAnswers,
            int totalQuestions) {

        Map<Integer, String> results = new HashMap<>();

        for (int q = 1; q <= totalQuestions; q++) {
            String correctAnswer = answerKey.get(q);
            String studentAnswer = detectedAnswers != null ? detectedAnswers.get(q) : null;

            if (correctAnswer == null) {
                // Question not in the answer key — skip grading
                results.put(q, "PENDING");
                continue;
            }

            if (studentAnswer == null || studentAnswer.equalsIgnoreCase("BLANK")) {
                results.put(q, "BLANK");
            } else if (studentAnswer.equalsIgnoreCase("MULTIPLE")) {
                results.put(q, "MULTIPLE");
            } else if (studentAnswer.equalsIgnoreCase("UNCERTAIN")) {
                results.put(q, "UNCERTAIN");
            } else if (studentAnswer.equalsIgnoreCase(correctAnswer)) {
                results.put(q, "CORRECT");
            } else {
                results.put(q, "WRONG");
            }
        }

        return results;
    }

    /**
     * Build a complete ScanResult from detected answers and an answer key.
     */
    public ScanResult buildScanResult(
            String quizId,
            Map<Integer, String> answerKey,
            Map<Integer, String> detectedAnswers,
            Map<Integer, Double> confidenceScores,
            int totalQuestions) {

        return buildScanResult(quizId, answerKey, detectedAnswers, confidenceScores, totalQuestions, totalQuestions);
    }

    public ScanResult buildScanResult(
            String quizId,
            Map<Integer, String> answerKey,
            Map<Integer, String> detectedAnswers,
            Map<Integer, Double> confidenceScores,
            int totalQuestions,
            double maximumMarks) {

        ScanResult result = new ScanResult();
        result.setQuizId(quizId);
        result.setDetectedAnswers(detectedAnswers);
        result.setConfidenceScores(confidenceScores);
        result.setMaximumMarks(maximumMarks);
        result.setStatusMessage("Evaluation complete");

        Map<Integer, String> evaluationResults = evaluate(answerKey, detectedAnswers, totalQuestions);
        result.setEvaluationResults(evaluationResults);
        result.computeAggregates();

        return result;
    }
}
