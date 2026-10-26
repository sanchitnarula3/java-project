package com.omr;

import com.omr.model.Quiz;
import com.omr.model.ScanResult;
import com.omr.service.EvaluationService;
import com.omr.service.ScanService;
import com.omr.vision.SampleSheetGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SampleSheetGeneratorTest {

    @BeforeAll
    static void initOpenCv() {
        nu.pattern.OpenCV.loadLocally();
    }

    @Test
    void testSyntheticSheetEvaluation() {
        Quiz quiz = new Quiz("Unit Test Quiz", 5);
        Map<Integer, String> answerKey = Map.of(
                1, "A",
                2, "B",
                3, "C",
                4, "D",
                5, "A"
        );
        quiz.setAnswerKey(answerKey);

        // Student marks 1=A, 2=B, 3=C, 4=D, 5=A (all correct)
        Map<Integer, String> studentAnswers = new HashMap<>(answerKey);
        byte[] sheetBytes = SampleSheetGenerator.generateSheetBytes("Unit Test Quiz", 5, studentAnswers);

        assertNotNull(sheetBytes);
        assertTrue(sheetBytes.length > 0);

        ScanService scanService = new ScanService(new EvaluationService());
        ScanResult result = scanService.processFrame(sheetBytes, quiz);

        assertNotNull(result);
        assertEquals(ScanResult.ScanStatus.SUCCESS, result.getStatus());
        assertEquals(5, result.getCorrectCount());
        assertEquals(100.0, result.getScorePercentage());
    }

    @Test
    void testFull15QuestionSheet() {
        Quiz quiz = new Quiz("15-Question Quiz", 15);
        Map<Integer, String> answerKey = new HashMap<>();
        String[] options = {"A", "B", "C", "D"};
        for (int i = 1; i <= 15; i++) {
            answerKey.put(i, options[(i - 1) % 4]);
        }
        quiz.setAnswerKey(answerKey);

        byte[] sheetBytes = SampleSheetGenerator.generateSheetBytes("15-Question Quiz", 15, answerKey);
        assertNotNull(sheetBytes);

        ScanService scanService = new ScanService(new EvaluationService());
        ScanResult result = scanService.processFrame(sheetBytes, quiz);

        assertNotNull(result);
        assertEquals(ScanResult.ScanStatus.SUCCESS, result.getStatus());
        assertEquals(15, result.getTotalQuestions());
        assertEquals(15, result.getCorrectCount());
        assertEquals(100.0, result.getScorePercentage());
    }

    @Test
    void testBlankSheetEvaluation() {
        Quiz quiz = new Quiz("Blank Quiz", 5);
        quiz.setAnswerKey(Map.of(
                1, "A", 2, "B", 3, "C", 4, "D", 5, "A"
        ));

        // Generate sheet with no marked answers (null map)
        byte[] sheetBytes = SampleSheetGenerator.generateSheetBytes("Blank Quiz", 5, null);
        assertNotNull(sheetBytes);

        ScanService scanService = new ScanService(new EvaluationService());
        ScanResult result = scanService.processFrame(sheetBytes, quiz);

        assertNotNull(result);
        assertEquals(ScanResult.ScanStatus.SUCCESS, result.getStatus());
        assertEquals(5, result.getBlankCount());
        assertEquals(0, result.getCorrectCount());
        assertEquals(0.0, result.getScorePercentage());
    }

    @Test
    void testMultipleMarkedOptions() {
        Quiz quiz = new Quiz("Multiple Marks Quiz", 3);
        quiz.setAnswerKey(Map.of(
                1, "A", 2, "B", 3, "C"
        ));

        Map<Integer, String> studentAnswers = Map.of(
                1, "A",
                2, "AB",     // Multiple marked
                3, "C"
        );

        byte[] sheetBytes = SampleSheetGenerator.generateSheetBytes("Multiple Marks Quiz", 3, studentAnswers);
        assertNotNull(sheetBytes);

        ScanService scanService = new ScanService(new EvaluationService());
        ScanResult result = scanService.processFrame(sheetBytes, quiz);

        assertNotNull(result);
        assertEquals(ScanResult.ScanStatus.SUCCESS, result.getStatus());
        assertEquals(2, result.getCorrectCount());
        assertEquals(1, result.getAmbiguousCount());
        assertEquals("MULTIPLE", result.getDetectedAnswers().get(2));
    }

    @Test
    void testBoundaryAndNullHandling() {
        // Null title, 0 questions, empty answers
        byte[] sheet0 = SampleSheetGenerator.generateSheetBytes(null, 0, Map.of());
        assertNotNull(sheet0);
        assertTrue(sheet0.length > 0);

        // More than max questions clamped to MAX_QUESTIONS without error
        byte[] sheetExcess = SampleSheetGenerator.generateSheetBytes("Clamped Quiz", 50, Map.of(1, "A"));
        assertNotNull(sheetExcess);
        assertTrue(sheetExcess.length > 0);
    }

    @Test
    void testRollNumberDetectionAndCsvExport() {
        String testRoll = "1021034567";
        Quiz quiz = new Quiz("Cognitive Computing UCS420", 5);
        Map<Integer, String> key = Map.of(
                1, "A", 2, "B", 3, "C", 4, "D", 5, "A"
        );
        quiz.setAnswerKey(key);

        byte[] sheet = SampleSheetGenerator.generateSheetBytes("Cognitive Computing UCS420", testRoll, 5, key);
        assertNotNull(sheet);

        ScanService scanService = new ScanService(new EvaluationService());
        ScanResult result = scanService.processFrame(sheet, quiz);

        assertNotNull(result);
        assertEquals(ScanResult.ScanStatus.SUCCESS, result.getStatus());
        assertEquals(testRoll, result.getRollNumber(), "Detected roll number must match generated roll number");
        assertEquals(5, result.getCorrectCount());
        assertEquals(100.0, result.getScorePercentage());

        // Test SubmissionRepository saving and CSV generation
        com.omr.repository.SubmissionRepository repo = new com.omr.repository.SubmissionRepository();
        repo.save(result);

        var list = repo.findByQuizId(quiz.getId());
        assertFalse(list.isEmpty());
        assertEquals(testRoll, list.get(0).getRollNumber());

        String csv = repo.generateCsv(quiz.getId(), quiz);
        assertNotNull(csv);
        assertTrue(csv.contains("Roll Number,Subject,Score,Total Marks,Percentage"));
        assertTrue(csv.contains(testRoll));
        assertTrue(csv.contains("Cognitive Computing UCS420"));
    }
}
