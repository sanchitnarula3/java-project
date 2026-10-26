package com.omr.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Preprocessing pipeline that prepares a raw camera frame for analysis.
 *
 * Steps:
 * 1. Convert to grayscale
 * 2. Apply Gaussian blur to reduce noise
 * 3. Adaptive thresholding for robust binarisation under variable lighting
 */
public class ImagePreprocessor {

    /**
     * Full preprocessing pipeline: grayscale → blur → adaptive threshold.
     *
     * @param src raw BGR image from the camera
     * @return binary image suitable for contour/bubble detection
     */
    public static Mat preprocess(Mat src) {
        Mat gray = toGrayscale(src);
        Mat blurred = applyGaussianBlur(gray);
        Mat binary = adaptiveThreshold(blurred);
        gray.release();
        blurred.release();
        return binary;
    }

    /** Convert a BGR image to grayscale. */
    public static Mat toGrayscale(Mat src) {
        Mat gray = new Mat();
        if (src.channels() == 1) {
            src.copyTo(gray);
        } else {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        }
        return gray;
    }

    /** Apply a Gaussian blur to reduce camera noise. */
    public static Mat applyGaussianBlur(Mat src) {
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(src, blurred, new Size(5, 5), 0);
        return blurred;
    }

    /**
     * Adaptive thresholding handles uneven lighting across the sheet.
     * Returns a binary image where paper is white (255) and ink is black (0).
     */
    public static Mat adaptiveThreshold(Mat src) {
        Mat binary = new Mat();
        Imgproc.adaptiveThreshold(
                src, binary, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                51, 4
        );
        return binary;
    }

    /**
     * Simple Otsu thresholding — useful when lighting is fairly uniform.
     */
    public static Mat otsuThreshold(Mat src) {
        Mat binary = new Mat();
        Imgproc.threshold(src, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        return binary;
    }
}
