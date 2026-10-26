package com.omr.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the OMR answer sheet in a camera frame, finds its four corners,
 * and applies a perspective transform to produce a top-down, aligned view.
 *
 * The sheet is expected to be the largest quadrilateral contour in the image.
 * Corner markers or dark borders help make detection more robust.
 */
public class SheetDetector {

    private static final int TARGET_WIDTH = 600;
    private static final int LEGACY_TARGET_HEIGHT = 800;
    private static final int THAPAR_PAGE_HEIGHT = 849;
    private static final int THAPAR_PANEL_HEIGHT = 247;

    /**
     * Result of sheet detection: the warped (aligned) image and a success flag.
     */
    public static class DetectionResult {
        public final boolean found;
        public final Mat warpedImage;
        public final Point[] corners;

        public DetectionResult(boolean found, Mat warpedImage, Point[] corners) {
            this.found = found;
            this.warpedImage = warpedImage;
            this.corners = corners;
        }

        public static DetectionResult notFound() {
            return new DetectionResult(false, null, null);
        }
    }

    /**
     * Detect the answer sheet and return a perspective-corrected image.
     *
     * @param src the raw camera frame (BGR)
     * @return DetectionResult with warped image if successful
     */
    public static DetectionResult detectAndWarp(Mat src) {
        // Step 1: Preprocess for contour detection
        Mat gray = ImagePreprocessor.toGrayscale(src);
        Mat blurred = ImagePreprocessor.applyGaussianBlur(gray);

        // Edge detection to find sheet boundaries
        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, 50, 150);

        // Dilate to close gaps in edges
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Mat dilated = new Mat();
        Imgproc.dilate(edges, dilated, kernel);

        // Step 2: Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Step 3: Find the largest quadrilateral contour
        Point[] sheetCorners = findLargestQuad(contours, src.size());

        // Clean up
        gray.release();
        blurred.release();
        edges.release();
        kernel.release();
        dilated.release();
        hierarchy.release();
        for (MatOfPoint c : contours)
            c.release();

        if (sheetCorners == null) {
            return DetectionResult.notFound();
        }

        // Step 4: Perspective warp to a standard rectangle
        double width = (distance(sheetCorners[0], sheetCorners[1])
                + distance(sheetCorners[3], sheetCorners[2])) / 2.0;
        double height = (distance(sheetCorners[0], sheetCorners[3])
                + distance(sheetCorners[1], sheetCorners[2])) / 2.0;
        double aspect = height == 0 ? 0 : width / height;
        int targetHeight = aspect >= 1.8 && aspect <= 3.2
                ? THAPAR_PANEL_HEIGHT
                : aspect >= 1.25 ? LEGACY_TARGET_HEIGHT
                : aspect < 0.72 ? THAPAR_PAGE_HEIGHT : LEGACY_TARGET_HEIGHT;

        Mat warped = perspectiveTransform(src, sheetCorners, targetHeight);

        return new DetectionResult(true, warped, sheetCorners);
    }

    /**
     * Find the largest 4-sided contour that occupies a significant fraction
     * of the image area. The lower bound also allows the bordered answer panel
     * on the Thapar paper to be found when the page itself blends into the background.
     */
    private static Point[] findLargestQuad(List<MatOfPoint> contours, Size imageSize) {
        double imageArea = imageSize.width * imageSize.height;
        double minArea = imageArea * 0.06;

        Point[] bestQuad = null;
        double bestArea = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < minArea)
                continue;

            MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(curve, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true);

            if (approx.rows() == 4 && area > bestArea) {
                bestArea = area;
                bestQuad = approx.toArray();
            }

            curve.release();
            approx.release();
        }

        if (bestQuad != null) {
            bestQuad = orderCorners(bestQuad);
        }

        return bestQuad;
    }

    /**
     * Order four corners as: top-left, top-right, bottom-right, bottom-left.
     * This is required for a consistent perspective transform.
     */
    private static Point[] orderCorners(Point[] pts) {
        // Sort by sum (x+y): smallest = top-left, largest = bottom-right
        // Sort by difference (y-x): smallest = top-right, largest = bottom-left
        Point[] ordered = new Point[4];

        double[] sums = new double[4];
        double[] diffs = new double[4];
        for (int i = 0; i < 4; i++) {
            sums[i] = pts[i].x + pts[i].y;
            diffs[i] = pts[i].y - pts[i].x;
        }

        int topLeftIdx = indexOfMin(sums);
        int bottomRightIdx = indexOfMax(sums);
        int topRightIdx = indexOfMin(diffs);
        int bottomLeftIdx = indexOfMax(diffs);

        ordered[0] = pts[topLeftIdx];
        ordered[1] = pts[topRightIdx];
        ordered[2] = pts[bottomRightIdx];
        ordered[3] = pts[bottomLeftIdx];

        return ordered;
    }

    /**
     * Apply a perspective transform to produce a top-down view of the sheet.
     */
    private static Mat perspectiveTransform(Mat src, Point[] corners, int targetHeight) {
        MatOfPoint2f srcPts = new MatOfPoint2f(corners);
        MatOfPoint2f dstPts = new MatOfPoint2f(
                new Point(0, 0),
                new Point(TARGET_WIDTH - 1, 0),
                new Point(TARGET_WIDTH - 1, targetHeight - 1),
                new Point(0, targetHeight - 1));

        Mat transform = Imgproc.getPerspectiveTransform(srcPts, dstPts);
        Mat warped = new Mat();
        Imgproc.warpPerspective(src, warped, transform, new Size(TARGET_WIDTH, targetHeight));

        srcPts.release();
        dstPts.release();
        transform.release();

        return warped;
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx])
                idx = i;
        }
        return idx;
    }

    private static int indexOfMax(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[idx])
                idx = i;
        }
        return idx;
    }
}
