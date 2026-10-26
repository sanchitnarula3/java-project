package com.omr.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Detects the 10-digit student Roll Number from the OMR sheet roll-number grid.
 *
 * <p>Layout: 10 columns (digits 1 to 10), each having 10 bubble rows (0 to 9).</p>
 */
public class RollNumberDetector {

    public static final int ROLL_GRID_LEFT = 310;
    public static final int ROLL_COL_SPACING = 26;
    public static final int ROLL_GRID_TOP = 245;
    public static final int ROLL_ROW_SPACING = 18;
    public static final int ROLL_BUBBLE_RADIUS = 7;
    public static final int ROLL_DIGITS_COUNT = 10;

    private static final int[] PAPER_PAGE_X = {353, 376, 400, 424, 448, 471, 495, 518, 542, 565};
    private static final int[] PAPER_PANEL_X = {359, 383, 409, 434, 459, 483, 509, 533, 558, 583};
    private static final int[] PAPER_PAGE_Y = {162, 172, 182, 192, 202, 212, 222, 232, 243, 254};
    private static final int[] PAPER_PANEL_Y = {24, 35, 45, 56, 66, 77, 87, 98, 108, 119};

    private static final double FILL_THRESHOLD = 0.30;
    private static final double MIN_DETECTION_RATIO = 0.18;

    public static class RollNumberResult {
        public final String rollNumber;
        public final double confidence;
        public final boolean isValid;
        public final int validDigitCount;

        public RollNumberResult(String rollNumber, double confidence, boolean isValid, int validDigitCount) {
            this.rollNumber = rollNumber;
            this.confidence = confidence;
            this.isValid = isValid;
            this.validDigitCount = validDigitCount;
        }
    }

    /**
     * Detect the 10-digit roll number from the binary sheet image.
     *
     * @param binary Preprocessed binary sheet image (600x800)
     * @return RollNumberResult containing the detected 10-digit string
     */
    public static RollNumberResult detectRollNumber(Mat binary) {
        if (binary.rows() >= 820 || binary.rows() <= 300) {
            return detectUploadedPaperRoll(binary, binary.rows() <= 300);
        }
        StringBuilder sb = new StringBuilder();
        int validDigits = 0;
        double totalConfidence = 0.0;

        for (int col = 0; col < ROLL_DIGITS_COUNT; col++) {
            int cx = ROLL_GRID_LEFT + (col * ROLL_COL_SPACING);
            int bestDigit = -1;
            double bestRatio = 0.0;
            int filledCount = 0;

            for (int digit = 0; digit <= 9; digit++) {
                int cy = ROLL_GRID_TOP + (digit * ROLL_ROW_SPACING);
                double fillRatio = measureBubbleFill(binary, cx, cy, ROLL_BUBBLE_RADIUS);

                if (fillRatio >= FILL_THRESHOLD) {
                    filledCount++;
                }
                if (fillRatio > bestRatio) {
                    bestRatio = fillRatio;
                    bestDigit = digit;
                }
            }

            if (filledCount == 1 && bestDigit >= 0) {
                sb.append(bestDigit);
                validDigits++;
                totalConfidence += Math.min(1.0, bestRatio / FILL_THRESHOLD);
            } else if (filledCount > 1) {
                // Ambiguous / multiple marked in same column
                sb.append(bestDigit >= 0 ? bestDigit : "X");
                totalConfidence += 0.4;
            } else if (bestRatio >= MIN_DETECTION_RATIO && bestDigit >= 0) {
                // Slightly lighter bubble fill
                sb.append(bestDigit);
                validDigits++;
                totalConfidence += (bestRatio / FILL_THRESHOLD);
            } else {
                // No marked digit in this column
                sb.append("0");
            }
        }

        String roll = sb.toString();
        double avgConfidence = ROLL_DIGITS_COUNT > 0 ? totalConfidence / ROLL_DIGITS_COUNT : 0.0;
        boolean isValid = validDigits >= 6 && !roll.contains("X");

        return new RollNumberResult(roll, avgConfidence, isValid, validDigits);
    }

    private static RollNumberResult detectUploadedPaperRoll(Mat binary, boolean panel) {
        int[] xPositions = panel ? PAPER_PANEL_X : PAPER_PAGE_X;
        int[] yPositions = panel ? PAPER_PANEL_Y : PAPER_PAGE_Y;
        StringBuilder roll = new StringBuilder(ROLL_DIGITS_COUNT);
        int validDigits = 0;
        double totalConfidence = 0.0;
        for (int col = 0; col < ROLL_DIGITS_COUNT; col++) {
            int markedDigit = -1;
            double bestRatio = 0.0;
            int filledCount = 0;
            for (int digit = 0; digit <= 9; digit++) {
                double ratio = measureBubbleFill(binary, xPositions[col], yPositions[digit], 5);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    markedDigit = digit;
                }
                if (ratio >= FILL_THRESHOLD) filledCount++;
            }
            if (filledCount == 1 && markedDigit >= 0) {
                roll.append(markedDigit);
                validDigits++;
                totalConfidence += Math.min(1.0, bestRatio / FILL_THRESHOLD);
            } else {
                roll.append(filledCount > 1 ? 'X' : '?');
            }
        }
        return new RollNumberResult(roll.toString(), totalConfidence / ROLL_DIGITS_COUNT,
                validDigits == ROLL_DIGITS_COUNT, validDigits);
    }

    /**
     * Measure dark pixel fill ratio for a circle of given radius.
     */
    private static double measureBubbleFill(Mat binary, int cx, int cy, int radius) {
        int x1 = Math.max(0, cx - radius);
        int y1 = Math.max(0, cy - radius);
        int x2 = Math.min(binary.cols(), cx + radius);
        int y2 = Math.min(binary.rows(), cy + radius);

        if (x1 >= x2 || y1 >= y2) return 0.0;

        Rect roi = new Rect(x1, y1, x2 - x1, y2 - y1);
        Mat bubbleRegion = new Mat(binary, roi);

        Mat mask = Mat.zeros(bubbleRegion.size(), CvType.CV_8UC1);
        Point center = new Point(bubbleRegion.cols() / 2.0, bubbleRegion.rows() / 2.0);
        Imgproc.circle(mask, center, Math.max(1, radius - 1), new Scalar(255), -1);

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
}
