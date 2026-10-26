package com.omr.service;

import com.omr.model.Quiz;
import com.omr.model.ScanResult;
import com.omr.vision.BubbleDetector;
import com.omr.vision.SheetDetector;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.Map;

/**
 * Orchestrates the full scan pipeline:
 * 1. Decode the uploaded image
 * 2. Detect the sheet and correct perspective
 * 3. Detect filled bubbles
 * 4. Evaluate answers against the answer key
 * 5. Return the result
 */
public class ScanService {

    private final EvaluationService evaluationService;

    public ScanService(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Process a camera frame and return the scan result.
     *
     * @param imageBytes raw image bytes (JPEG or PNG)
     * @param quiz       the quiz being evaluated
     * @return ScanResult with detected answers and grades
     */
    public ScanResult processFrame(byte[] imageBytes, Quiz quiz) {
        Mat src = null;
        Mat warped = null;
        MatOfByte encodedImage = null;
        Mat binary = null;

        try {
            // Step 1: Decode image
            encodedImage = new MatOfByte(imageBytes);
            src = Imgcodecs.imdecode(encodedImage, Imgcodecs.IMREAD_COLOR);
            if (src.empty()) {
                return ScanResult.error(ScanResult.ScanStatus.PROCESSING_ERROR, "Could not decode image");
            }

            // Step 2: Detect sheet and warp (if already 600x800, use directly)
            if ((src.cols() == 600 && src.rows() == 800)
                    || (src.cols() == 600 && (src.rows() == 849 || src.rows() == 247))) {
                warped = src.clone();
            } else {
                SheetDetector.DetectionResult detection = SheetDetector.detectAndWarp(src);
                if (detection.found) {
                    warped = detection.warpedImage;
                } else {
                    return ScanResult.error(ScanResult.ScanStatus.NO_SHEET_DETECTED,
                            "Could not find the answer sheet. Hold the full page or its bordered answer panel inside the camera frame.");
                }
            }

            // Step 3: Preprocess and detect roll number + bubbles
            binary = com.omr.vision.ImagePreprocessor.preprocess(warped);
            com.omr.vision.RollNumberDetector.RollNumberResult rollResult =
                    com.omr.vision.RollNumberDetector.detectRollNumber(binary);

            int numQuestions = quiz.getNumberOfQuestions();
            Map<Integer, BubbleDetector.QuestionDetection> detections =
                    BubbleDetector.detectAll(warped, binary, numQuestions);

            Map<Integer, String> detectedAnswers = BubbleDetector.extractAnswers(detections);
            Map<Integer, Double> confidenceScores = BubbleDetector.extractConfidence(detections);

            // Step 4: Evaluate
            ScanResult result = evaluationService.buildScanResult(
                    quiz.getId(),
                    quiz.getAnswerKey(),
                    detectedAnswers,
                    confidenceScores,
                    numQuestions,
                    quiz.getMaximumMarks()
            );

            if (rollResult != null && rollResult.isValid
                    && rollResult.rollNumber != null && !rollResult.rollNumber.isBlank()) {
                result.setRollNumber(rollResult.rollNumber);
            } else {
                result.setRollNumber("Unknown");
            }

            return result;

        } catch (Exception e) {
            return ScanResult.error(ScanResult.ScanStatus.PROCESSING_ERROR,
                    "Processing failed: " + e.getMessage());
        } finally {
            if (src != null) src.release();
            if (warped != null) warped.release();
            if (binary != null) binary.release();
            if (encodedImage != null) encodedImage.release();
        }
    }
}
