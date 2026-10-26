package com.omr.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects filled bubbles on a fixed-layout OMR sheet with 15 questions
 * and 4 options per question (A, B, C, D).
 *
 * The algorithm inspects each expected bubble region, calculates the
 * proportion of dark pixels, and classifies the answer.
 *
 * <p>Expected input: a perspective-corrected (warped), top-down image
 * of the OMR sheet at 600×800 pixels.</p>
 *
 * <h3>Sheet Layout</h3>
 * The standard sheet has known regions:
 * <ul>
 *   <li>Header area: rows 0–100 (title, student info)</li>
 *   <li>Bubble grid: rows 100–750</li>
 *   <li>Each question occupies a horizontal row with 4 bubble columns</li>
 * </ul>
 */
public class BubbleDetector {

    // --- 3-Column Thapar Layout constants (600×800 sheet) ---
    public static final int COL1_LEFT = 40;
    public static final int COL2_LEFT = 230;
    public static final int COL3_LEFT = 420;
    public static final int OPT_SPACING = 35;
    public static final int Q_GRID_TOP = 525;
    public static final int Q_ROW_SPACING = 48;
    public static final int THAPAR_BUBBLE_RADIUS = 10;

    // --- Legacy single-column layout constants ---
    private static final int LEGACY_GRID_LEFT = 150;
    private static final int LEGACY_COL_SPACING = 110;
    private static final int LEGACY_GRID_TOP = 140;
    private static final int LEGACY_ROW_SPACING = 40;
    private static final int LEGACY_BUBBLE_RADIUS = 14;

    /** Dark-pixel-ratio threshold above which a bubble is considered filled. */
    private static final double FILL_THRESHOLD = 0.32;
    /** If a bubble's ratio is between these values, detection is uncertain. */
    private static final double UNCERTAIN_THRESHOLD = 0.22;

    private static final String[] OPTIONS = {"A", "B", "C", "D"};

    // Coordinates for the uploaded Thapar answer sheet, normalized to either
    // the full A4 page (600x849) or its bordered answer panel (600x247).
    private static final int[] PAPER_PAGE_OPTION_X = {
            52, 88, 126, 162, 252, 288, 325, 362, 437, 475, 512, 550
    };
    private static final int[] PAPER_PANEL_OPTION_X = {
            40, 79, 119, 158, 252, 290, 329, 367, 448, 487, 526, 565
    };
    private static final int[] PAPER_PAGE_ROW_Y = {284, 302, 320, 338, 356};
    private static final int[] PAPER_PANEL_ROW_Y = {154, 172, 190, 210, 228};

    /**
     * Result for a single question.
     */
    public static class QuestionDetection {
        public final String answer;        // A, B, C, D, BLANK, MULTIPLE, UNCERTAIN
        public final double confidence;    // 0.0–1.0
        public final double[] ratios;      // dark-pixel ratio for each option

        public QuestionDetection(String answer, double confidence, double[] ratios) {
            this.answer = answer;
            this.confidence = confidence;
            this.ratios = ratios;
        }
    }

    /**
     * Detect answers for questions on the warped sheet image.
     *
     * @param warped perspective-corrected grayscale or BGR image (600×800)
     * @param numQuestions number of questions to detect
     * @return map of question number → detection result
     */
    public static Map<Integer, QuestionDetection> detectAll(Mat warped, int numQuestions) {
        Mat binary = ImagePreprocessor.preprocess(warped);
        try {
            return detectAll(warped, binary, numQuestions);
        } finally {
            binary.release();
        }
    }

    /**
     * Detect answers using a precomputed binary image.
     */
    public static Map<Integer, QuestionDetection> detectAll(Mat warped, Mat binary, int numQuestions) {
        if (binary.rows() >= 820 || binary.rows() <= 300) {
            return detectUploadedPaperLayout(binary, numQuestions, binary.rows() <= 300);
        }
        if (isThaparLayout(binary)) {
            return detectThaparLayout(binary, numQuestions);
        }

        // Try 3-column Thapar layout first
        Map<Integer, QuestionDetection> thaparResults = detectThaparLayout(binary, numQuestions);

        int thaparAnswerCount = 0;
        for (QuestionDetection qd : thaparResults.values()) {
            if (!"BLANK".equalsIgnoreCase(qd.answer)) {
                thaparAnswerCount++;
            }
        }

        if (thaparAnswerCount > 0) {
            return thaparResults;
        }

        // Fallback to legacy 1-column layout
        return detectLegacyLayout(binary, numQuestions);
    }

    private static Map<Integer, QuestionDetection> detectUploadedPaperLayout(Mat binary, int numQuestions, boolean panel) {
        Map<Integer, QuestionDetection> results = new HashMap<>();
        int[] optionXs = panel ? PAPER_PANEL_OPTION_X : PAPER_PAGE_OPTION_X;
        int[] rowYs = panel ? PAPER_PANEL_ROW_Y : PAPER_PAGE_ROW_Y;
        for (int q = 0; q < numQuestions; q++) {
            int col = q / 5;
            int row = q % 5;
            double[] ratios = new double[4];
            for (int option = 0; option < 4; option++) {
                ratios[option] = measureBubbleFill(binary, optionXs[col * 4 + option], rowYs[row], 5);
            }
            results.put(q + 1, classifyAnswer(ratios));
        }
        return results;
    }

    private static boolean isThaparLayout(Mat binary) {
        if (binary == null || binary.rows() < 505 || binary.cols() < 500) return false;
        try {
            Rect roi = new Rect(100, 496, 400, 5);
            Mat region = new Mat(binary, roi);
            int nonZero = Core.countNonZero(region);
            region.release();
            return nonZero >= 150;
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<Integer, QuestionDetection> detectThaparLayout(Mat binary, int numQuestions) {
        Map<Integer, QuestionDetection> results = new HashMap<>();
        for (int q = 0; q < numQuestions; q++) {
            int questionNum = q + 1;
            int colIdx = q / 5;
            int rowIdx = q % 5;
            int baseColX = colIdx == 0 ? COL1_LEFT : (colIdx == 1 ? COL2_LEFT : COL3_LEFT);
            int rowY = Q_GRID_TOP + (rowIdx * Q_ROW_SPACING);

            double[] ratios = new double[4];
            for (int opt = 0; opt < 4; opt++) {
                int colX = baseColX + 30 + (opt * OPT_SPACING);
                ratios[opt] = measureBubbleFill(binary, colX, rowY, THAPAR_BUBBLE_RADIUS);
            }

            results.put(questionNum, classifyAnswer(ratios));
        }
        return results;
    }

    private static Map<Integer, QuestionDetection> detectLegacyLayout(Mat binary, int numQuestions) {
        Map<Integer, QuestionDetection> results = new HashMap<>();
        for (int q = 0; q < numQuestions; q++) {
            int questionNum = q + 1;
            int rowY = LEGACY_GRID_TOP + (q * LEGACY_ROW_SPACING);

            double[] ratios = new double[4];
            for (int opt = 0; opt < 4; opt++) {
                int colX = LEGACY_GRID_LEFT + (opt * LEGACY_COL_SPACING);
                ratios[opt] = measureBubbleFill(binary, colX, rowY, LEGACY_BUBBLE_RADIUS);
            }

            results.put(questionNum, classifyAnswer(ratios));
        }
        return results;
    }

    /**
     * Measure the dark-pixel ratio inside a circular bubble region.
     *
     * @param binary binary image (white marks on black, or vice versa after threshold)
     * @param cx     center X of the bubble
     * @param cy     center Y of the bubble
     * @return ratio of dark (filled) pixels to total pixels in the region
     */
    private static double measureBubbleFill(Mat binary, int cx, int cy, int radius) {
        // Define the bounding rectangle for the bubble
        int x1 = Math.max(0, cx - radius);
        int y1 = Math.max(0, cy - radius);
        int x2 = Math.min(binary.cols(), cx + radius);
        int y2 = Math.min(binary.rows(), cy + radius);

        if (x1 >= x2 || y1 >= y2) return 0.0;

        Rect roi = new Rect(x1, y1, x2 - x1, y2 - y1);
        Mat bubbleRegion = new Mat(binary, roi);

        // Create a circular mask to ignore corners of the bounding box
        Mat mask = Mat.zeros(bubbleRegion.size(), CvType.CV_8UC1);
        Point center = new Point(bubbleRegion.cols() / 2.0, bubbleRegion.rows() / 2.0);
        Imgproc.circle(mask, center, Math.max(1, radius - 2), new Scalar(255), -1);

        // Count white (filled) pixels within the circular mask
        // After THRESH_BINARY_INV, filled areas are white (255)
        int totalPixels = Core.countNonZero(mask);
        Mat masked = new Mat();
        Core.bitwise_and(bubbleRegion, mask, masked);
        int filledPixels = Core.countNonZero(masked);

        double ratio = totalPixels > 0 ? (double) filledPixels / totalPixels : 0.0;

        bubbleRegion.release();
        mask.release();
        masked.release();

        return ratio;
    }

    /**
     * Classify the answer for a single question based on dark-pixel ratios.
     */
    private static QuestionDetection classifyAnswer(double[] ratios) {
        int filledCount = 0;
        int bestIdx = 0;
        double bestRatio = 0;
        int uncertainCount = 0;

        for (int i = 0; i < ratios.length; i++) {
            if (ratios[i] >= FILL_THRESHOLD) {
                filledCount++;
            } else if (ratios[i] >= UNCERTAIN_THRESHOLD) {
                uncertainCount++;
            }
            if (ratios[i] > bestRatio) {
                bestRatio = ratios[i];
                bestIdx = i;
            }
        }

        if (filledCount == 1) {
            // Exactly one bubble clearly filled — confident detection
            double confidence = Math.min(1.0, bestRatio / FILL_THRESHOLD);
            return new QuestionDetection(OPTIONS[bestIdx], confidence, ratios);
        } else if (filledCount > 1) {
            // Multiple bubbles filled
            return new QuestionDetection("MULTIPLE", 0.8, ratios);
        } else if (uncertainCount > 0) {
            // Some bubbles are partially filled — uncertain
            return new QuestionDetection("UNCERTAIN", 0.3, ratios);
        } else {
            // Nothing filled — blank
            return new QuestionDetection("BLANK", 0.9, ratios);
        }
    }

    /**
     * Convenience method to extract just the answer strings from detection results.
     */
    public static Map<Integer, String> extractAnswers(Map<Integer, QuestionDetection> detections) {
        Map<Integer, String> answers = new HashMap<>();
        for (var entry : detections.entrySet()) {
            answers.put(entry.getKey(), entry.getValue().answer);
        }
        return answers;
    }

    /**
     * Convenience method to extract confidence scores from detection results.
     */
    public static Map<Integer, Double> extractConfidence(Map<Integer, QuestionDetection> detections) {
        Map<Integer, Double> scores = new HashMap<>();
        for (var entry : detections.entrySet()) {
            scores.put(entry.getKey(), entry.getValue().confidence);
        }
        return scores;
    }
}
