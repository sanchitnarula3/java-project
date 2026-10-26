package com.omr;

import com.omr.service.EvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the EvaluationService — the core grading engine.
 * These can run without a camera or OpenCV.
 */
class EvaluationServiceTest {

    private EvaluationService evaluationService;
    private Map<Integer, String> answerKey;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService();

        // Standard 15-question answer key
        answerKey = new HashMap<>();
        answerKey.put(1, "B"); answerKey.put(2, "D"); answerKey.put(3, "A");
        answerKey.put(4, "C"); answerKey.put(5, "B"); answerKey.put(6, "A");
        answerKey.put(7, "D"); answerKey.put(8, "B"); answerKey.put(9, "C");
        answerKey.put(10, "A"); answerKey.put(11, "D"); answerKey.put(12, "B");
        answerKey.put(13, "A"); answerKey.put(14, "C"); answerKey.put(15, "D");
    }

    @Test
    @DisplayName("All correct answers should return CORRECT for every question")
    void testAllCorrect() {
        // Student answers match the key perfectly
        Map<Integer, String> detected = new HashMap<>(answerKey);
        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);

        assertEquals(15, results.size());
        results.values().forEach(v -> assertEquals("CORRECT", v));
    }

    @Test
    @DisplayName("All wrong answers should return WRONG for every question")
    void testAllWrong() {
        Map<Integer, String> detected = new HashMap<>();
        for (int q = 1; q <= 15; q++) {
            // Pick an option that's definitely wrong
            String correct = answerKey.get(q);
            String wrong = correct.equals("A") ? "B" : "A";
            detected.put(q, wrong);
        }

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);
        results.values().forEach(v -> assertEquals("WRONG", v));
    }

    @Test
    @DisplayName("Blank answers should be classified as BLANK")
    void testBlankAnswers() {
        Map<Integer, String> detected = new HashMap<>();
        detected.put(1, "BLANK");
        detected.put(2, "BLANK");
        // Q3–Q15 are null (not in the map) — should also be BLANK

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);

        assertEquals("BLANK", results.get(1));
        assertEquals("BLANK", results.get(2));
        assertEquals("BLANK", results.get(3)); // null → BLANK
    }

    @Test
    @DisplayName("MULTIPLE marks should be classified as MULTIPLE")
    void testMultipleMarks() {
        Map<Integer, String> detected = new HashMap<>();
        detected.put(1, "MULTIPLE");
        detected.put(2, "B");

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);

        assertEquals("MULTIPLE", results.get(1));
    }

    @Test
    @DisplayName("UNCERTAIN detections should be classified as UNCERTAIN")
    void testUncertainDetection() {
        Map<Integer, String> detected = new HashMap<>();
        detected.put(1, "UNCERTAIN");

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);
        assertEquals("UNCERTAIN", results.get(1));
    }

    @Test
    @DisplayName("Mixed scenario: correct, wrong, blank, multiple")
    void testMixedScenario() {
        Map<Integer, String> detected = new HashMap<>();
        detected.put(1, "B");        // Correct (key is B)
        detected.put(2, "A");        // Wrong (key is D)
        detected.put(3, "BLANK");    // Blank
        detected.put(4, "MULTIPLE"); // Multiple
        detected.put(5, "B");        // Correct (key is B)
        // Q6-Q15 null → BLANK

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);

        assertEquals("CORRECT", results.get(1));
        assertEquals("WRONG", results.get(2));
        assertEquals("BLANK", results.get(3));
        assertEquals("MULTIPLE", results.get(4));
        assertEquals("CORRECT", results.get(5));
        assertEquals("BLANK", results.get(6));
    }

    @Test
    @DisplayName("Case-insensitive matching should work")
    void testCaseInsensitive() {
        Map<Integer, String> detected = new HashMap<>();
        detected.put(1, "b"); // lowercase

        Map<Integer, String> results = evaluationService.evaluate(answerKey, detected, 15);
        assertEquals("CORRECT", results.get(1)); // answerKey has "B"
    }

    @Test
    @DisplayName("Null detected answers map should result in all BLANK")
    void testNullDetected() {
        Map<Integer, String> results = evaluationService.evaluate(answerKey, null, 15);
        assertEquals(15, results.size());
        results.values().forEach(v -> assertEquals("BLANK", v));
    }
}
