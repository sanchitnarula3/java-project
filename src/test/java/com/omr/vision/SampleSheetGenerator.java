package com.omr.vision;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Map;

/**
 * Utility to generate standard 600x800 OMR sheets for testing and evaluation.
 * Faithfully produces the Thapar Institute OMR format:
 * - Institutional Header & Course Details Table
 * - Student Info (Left) & 10-Digit Roll Number Grid (Right)
 * - 3 Columns of Questions (Q1-Q5, Q6-Q10, Q11-Q15) with A, B, C, D Bubbles
 */
public class SampleSheetGenerator {

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Throwable ignored) {
            // Already loaded or initialized by ApplicationListener
        }
    }

    public static final int MAX_QUESTIONS = 15;
    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    // Roll Number Layout Constants
    private static final int ROLL_GRID_LEFT = 310;
    private static final int ROLL_COL_SPACING = 26;
    private static final int ROLL_GRID_TOP = 245;
    private static final int ROLL_ROW_SPACING = 18;
    private static final int ROLL_BUBBLE_RADIUS = 7;
    private static final int ROLL_DIGITS_COUNT = 10;

    // 3-Column Question Layout Constants
    private static final int COL1_LEFT = 40;
    private static final int COL2_LEFT = 230;
    private static final int COL3_LEFT = 420;
    private static final int OPT_SPACING = 35;
    private static final int Q_GRID_TOP = 525;
    private static final int Q_ROW_SPACING = 48;
    private static final int Q_BUBBLE_RADIUS = 10;

    private static final String[] OPTIONS = {"A", "B", "C", "D"};

    /**
     * Generate sheet with default sample roll number.
     */
    public static byte[] generateSheetBytes(String quizTitle, int numQuestions, Map<Integer, String> studentAnswers) {
        return generateSheetBytes(quizTitle, "1021034567", numQuestions, studentAnswers);
    }

    /**
     * Generate a blank OMR sheet ready for printing.
     */
    public static byte[] generateBlankSheetBytes(String quizTitle, int numQuestions) {
        return generateSheetBytes(quizTitle, null, numQuestions, null);
    }

    /**
     * Generate synthetic OMR sheet image with specified roll number and marked answers.
     *
     * @param quizTitle Title/Course of the quiz
     * @param rollNumber 10-digit roll number (e.g. "1021034567" or null for blank)
     * @param numQuestions Number of questions (clamped to 0..15)
     * @param studentAnswers Marked answers (e.g. {1: "A", 2: "C", ...})
     * @return PNG encoded bytes of the sheet
     */
    public static byte[] generateSheetBytes(String quizTitle, String rollNumber, int numQuestions, Map<Integer, String> studentAnswers) {
        int questionsToDraw = Math.min(Math.max(0, numQuestions), MAX_QUESTIONS);
        Mat sheet = new Mat(new Size(WIDTH, HEIGHT), org.opencv.core.CvType.CV_8UC3, new Scalar(255, 255, 255));
        MatOfByte buf = null;

        try {
            // 1. Outer Border
            Imgproc.rectangle(sheet, new Point(15, 15), new Point(WIDTH - 15, HEIGHT - 15), new Scalar(0, 0, 0), 2);
            Imgproc.rectangle(sheet, new Point(18, 18), new Point(WIDTH - 18, HEIGHT - 18), new Scalar(60, 60, 60), 1);

            // 2. Institutional Header
            Imgproc.putText(sheet, "Thapar Institute of Engineering & Technology, Patiala",
                    new Point(65, 45), Imgproc.FONT_HERSHEY_SIMPLEX, 0.55, new Scalar(0, 0, 0), 2);
            Imgproc.putText(sheet, "Department of Computer Science and Engineering",
                    new Point(115, 68), Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Sessional Quiz — OMR Evaluation Sheet",
                    new Point(150, 90), Imgproc.FONT_HERSHEY_SIMPLEX, 0.48, new Scalar(0, 0, 0), 2);

            // 3. Course Details Table (Point(20, 102) to Point(580, 175))
            Imgproc.rectangle(sheet, new Point(20, 102), new Point(WIDTH - 20, 175), new Scalar(0, 0, 0), 1);
            Imgproc.line(sheet, new Point(20, 126), new Point(WIDTH - 20, 126), new Scalar(0, 0, 0), 1);
            Imgproc.line(sheet, new Point(20, 150), new Point(WIDTH - 20, 150), new Scalar(0, 0, 0), 1);
            Imgproc.line(sheet, new Point(280, 102), new Point(280, 175), new Scalar(0, 0, 0), 1);

            String courseName = (quizTitle != null && !quizTitle.isBlank()) ? quizTitle : "UCS420, Cognitive Computing";
            Imgproc.putText(sheet, "B.E. (4th Sem)", new Point(25, 119), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Course: " + courseName, new Point(285, 119), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);

            Imgproc.putText(sheet, "Date: February 2025", new Point(25, 143), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Time: 1:15 PM - 1:25 PM", new Point(285, 143), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);

            Imgproc.putText(sheet, "Duration: 10 Min., M.Marks: " + questionsToDraw, new Point(25, 167), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Evaluation: Real-Time OMR", new Point(285, 167), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);

            // 4. Student Details Box (Left) (Point(20, 188) to Point(280, 445))
            Imgproc.rectangle(sheet, new Point(20, 188), new Point(280, 445), new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Name: _______________________________", new Point(28, 225), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Batch: ______________________________", new Point(28, 265), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Signature: __________________________", new Point(28, 305), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Instructions:", new Point(28, 355), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(0, 0, 0), 2);
            Imgproc.putText(sheet, "* Darken one bubble per digit/question", new Point(28, 375), Imgproc.FONT_HERSHEY_SIMPLEX, 0.32, new Scalar(80, 80, 80), 1);
            Imgproc.putText(sheet, "* Use black/blue ballpoint pen only", new Point(28, 395), Imgproc.FONT_HERSHEY_SIMPLEX, 0.32, new Scalar(80, 80, 80), 1);
            Imgproc.putText(sheet, "* Fill bubble completely without stray marks", new Point(28, 415), Imgproc.FONT_HERSHEY_SIMPLEX, 0.32, new Scalar(80, 80, 80), 1);

            // 5. Roll Number Box & Grid (Right) (Point(285, 188) to Point(580, 445))
            Imgproc.rectangle(sheet, new Point(285, 188), new Point(WIDTH - 20, 445), new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Roll Number:", new Point(295, 208), Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, new Scalar(0, 0, 0), 2);

            String cleanRoll = rollNumber != null ? rollNumber.replaceAll("[^0-9]", "") : "";

            // Column Headers (1 to 10)
            for (int col = 0; col < ROLL_DIGITS_COUNT; col++) {
                int colX = ROLL_GRID_LEFT + (col * ROLL_COL_SPACING);
                String colLabel = String.valueOf(col + 1);
                Imgproc.putText(sheet, colLabel, new Point(colX - 4, 226), Imgproc.FONT_HERSHEY_SIMPLEX, 0.32, new Scalar(0, 0, 0), 1);
            }

            // 10 Rows of Bubbles for Digits 0 to 9
            for (int col = 0; col < ROLL_DIGITS_COUNT; col++) {
                int colX = ROLL_GRID_LEFT + (col * ROLL_COL_SPACING);
                int markedDigit = -1;
                if (col < cleanRoll.length()) {
                    markedDigit = Character.getNumericValue(cleanRoll.charAt(col));
                }

                for (int digit = 0; digit <= 9; digit++) {
                    int rowY = ROLL_GRID_TOP + (digit * ROLL_ROW_SPACING);
                    Point center = new Point(colX, rowY);
                    boolean isFilled = (digit == markedDigit);

                    if (isFilled) {
                        Imgproc.circle(sheet, center, ROLL_BUBBLE_RADIUS, new Scalar(20, 20, 20), -1);
                    } else {
                        Imgproc.circle(sheet, center, ROLL_BUBBLE_RADIUS, new Scalar(70, 70, 70), 1);
                        Imgproc.putText(sheet, String.valueOf(digit), new Point(colX - 3, rowY + 3),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.28, new Scalar(120, 120, 120), 1);
                    }
                }
            }

            // 6. Questions Box (Point(20, 458) to Point(580, 775))
            Imgproc.rectangle(sheet, new Point(20, 458), new Point(WIDTH - 20, 775), new Scalar(0, 0, 0), 1);
            Imgproc.putText(sheet, "Answer the following questions by darkening the correct option (A, B, C or D).",
                    new Point(30, 482), Imgproc.FONT_HERSHEY_SIMPLEX, 0.44, new Scalar(0, 0, 0), 2);
            Imgproc.line(sheet, new Point(20, 498), new Point(WIDTH - 20, 498), new Scalar(0, 0, 0), 1);

            // Column divider lines
            Imgproc.line(sheet, new Point(215, 498), new Point(215, 775), new Scalar(180, 180, 180), 1);
            Imgproc.line(sheet, new Point(405, 498), new Point(405, 775), new Scalar(180, 180, 180), 1);

            // Draw Questions (3 columns of 5)
            for (int q = 0; q < questionsToDraw; q++) {
                int qNum = q + 1;
                int colIdx = q / 5;
                int rowIdx = q % 5;
                int baseColX = colIdx == 0 ? COL1_LEFT : (colIdx == 1 ? COL2_LEFT : COL3_LEFT);
                int rowY = Q_GRID_TOP + (rowIdx * Q_ROW_SPACING);

                // Question number label
                String qLabel = String.format("%2d.", qNum);
                Imgproc.putText(sheet, qLabel, new Point(baseColX - 12, rowY + 4), Imgproc.FONT_HERSHEY_SIMPLEX, 0.42, new Scalar(0, 0, 0), 1);

                String markedOption = studentAnswers != null ? studentAnswers.get(qNum) : null;

                for (int c = 0; c < 4; c++) {
                    int colX = baseColX + 30 + (c * OPT_SPACING);
                    Point center = new Point(colX, rowY);
                    boolean isMarked = isOptionMarked(markedOption, OPTIONS[c]);

                    if (isMarked) {
                        Imgproc.circle(sheet, center, Q_BUBBLE_RADIUS, new Scalar(20, 20, 20), -1);
                    } else {
                        Imgproc.circle(sheet, center, Q_BUBBLE_RADIUS, new Scalar(60, 60, 60), 1);
                    }
                    // Option letter printed beside the bubble circle (as on the official Thapar OMR sheet)
                    Imgproc.putText(sheet, OPTIONS[c], new Point(colX + Q_BUBBLE_RADIUS + 3, rowY + 4),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, new Scalar(40, 40, 40), 1);
                }
            }

            // Encode to PNG bytes
            buf = new MatOfByte();
            Imgcodecs.imencode(".png", sheet, buf);
            return buf.toArray();
        } finally {
            sheet.release();
            if (buf != null) {
                buf.release();
            }
        }
    }

    private static boolean isOptionMarked(String markedOption, String option) {
        if (markedOption == null || markedOption.isBlank()) {
            return false;
        }
        String trimmed = markedOption.trim();
        if (trimmed.equalsIgnoreCase("BLANK")) {
            return false;
        }
        if (option.equalsIgnoreCase(trimmed)) {
            return true;
        }
        String upper = trimmed.toUpperCase();
        if (upper.contains(",")) {
            for (String part : upper.split(",")) {
                if (part.trim().equals(option)) {
                    return true;
                }
            }
        } else if (upper.matches("^[A-D]+$")) {
            return upper.contains(option);
        }
        return false;
    }
}
