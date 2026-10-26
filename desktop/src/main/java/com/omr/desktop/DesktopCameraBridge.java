package com.omr.desktop;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.Base64;
import java.util.Arrays;
import java.nio.file.Path;
import java.nio.file.Files;

/** Captures camera frames off the JavaFX/WebView thread and exposes cached images to JavaScript. */
public final class DesktopCameraBridge {
    private static final int PREVIEW_WIDTH = 960;
    private static final int PREVIEW_HEIGHT = 720;
    private static final long PREVIEW_INTERVAL_NANOS = 80_000_000L;
    private static final long STARTUP_TIMEOUT_NANOS = 30_000_000_000L;

    private final Object frameLock = new Object();
    private volatile boolean running;
    private volatile String status = "STOPPED";
    private volatile String previewDataUrl = "";
    private volatile String capturedDataUrl = "";
    private volatile String startupStage = "camera startup";
    private volatile boolean startupTimedOut;
    private volatile String cameraHelperDiagnostics = "";
    private Mat latestFrame;
    private Thread captureThread;
    private volatile Process cameraProcess;

    /** Starts capture asynchronously so a permission dialog or camera startup cannot freeze the UI. */
    public synchronized String start() {
        if (running || (captureThread != null && captureThread.isAlive())) return status;
        running = true;
        startupTimedOut = false;
        cameraHelperDiagnostics = "";
        status = "STARTING";
        startupStage = "loading OpenCV";
        previewDataUrl = "";
        capturedDataUrl = "";
        captureThread = new Thread(this::captureLoop, "omr-camera-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(STARTUP_TIMEOUT_NANOS / 1_000_000L);
                if (running && !"READY".equals(status)) {
                    startupTimedOut = true;
                    status = startupTimeoutMessage();
                    running = false;
                    Thread activeCapture = captureThread;
                    if (activeCapture != null) activeCapture.interrupt();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "omr-camera-startup-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return "STARTING";
    }

    public String status() {
        return status;
    }

    /** Returns the latest low-resolution preview without touching the camera device. */
    public String frameDataUrl() {
        return previewDataUrl;
    }

    /** Encodes a full-resolution still only when the user presses Capture. */
    public String captureDataUrl() {
        if (!capturedDataUrl.isEmpty()) return capturedDataUrl;
        synchronized (frameLock) {
            if (latestFrame == null || latestFrame.empty()) return "";
            MatOfByte encoded = new MatOfByte();
            MatOfInt quality = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 90);
            try {
                if (!Imgcodecs.imencode(".jpg", latestFrame, encoded, quality)) return "";
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(encoded.toArray());
            } finally {
                encoded.release();
                quality.release();
            }
        }
    }

    private void captureLoop() {
        String cameraHelper = System.getProperty("omr.camera.helper");
        if (cameraHelper != null && !cameraHelper.isBlank() && Files.isExecutable(Path.of(cameraHelper))) {
            captureWithAvFoundation(cameraHelper);
            running = false;
            return;
        }

        VideoCapture device = null;
        Mat frame = new Mat();
        Mat preview = new Mat();
        MatOfByte encoded = new MatOfByte();
        MatOfInt quality = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 68);
        long startupDeadline = System.nanoTime() + STARTUP_TIMEOUT_NANOS;
        try {
            startupStage = "checking the bundled OpenCV library path";
            loadOpenCvNativeLibrary();
            checkStartupTimeout(startupDeadline);

            startupStage = "opening the camera";
            status = "OPENING_CAMERA";
            boolean gotFirstFrame = false;
            int[] backends = { Videoio.CAP_AVFOUNDATION, Videoio.CAP_ANY };
            int[] cameraIndices = { 0, 1, 2, 3 };
            for (int backend : backends) {
                for (int cameraIndex : cameraIndices) {
                    checkStartupTimeout(startupDeadline);
                    if (!running) return;
                    device = new VideoCapture();
                    boolean opened = backend == Videoio.CAP_ANY
                            ? device.open(cameraIndex)
                            : device.open(cameraIndex, backend);
                    if (opened && device.isOpened()) {
                        startupStage = "waiting for a frame from camera " + cameraIndex
                                + (backend == Videoio.CAP_AVFOUNDATION ? " via AVFoundation" : " via the default backend");
                        status = "WAITING";
                        long candidateDeadline = Math.min(startupDeadline, System.nanoTime() + 2_000_000_000L);
                        while (running && System.nanoTime() < candidateDeadline) {
                            if (device.read(frame) && !frame.empty()) {
                                gotFirstFrame = true;
                                break;
                            }
                            Thread.sleep(10);
                        }
                    }
                    if (gotFirstFrame) break;
                    device.release();
                    device = null;
                }
                if (gotFirstFrame) break;
            }
            if (!gotFirstFrame) {
                status = "Camera opened but no frames arrived from camera indices 0–3 using AVFoundation or the default backend. Check macOS Camera access and whether the camera works in another app.";
                return;
            }

            long nextPreviewAt = 0;
            status = "READY";
            publishFrame(frame, preview, encoded, quality);
            nextPreviewAt = System.nanoTime() + PREVIEW_INTERVAL_NANOS;
            while (running) {
                if (device.read(frame) && !frame.empty()) {
                    long now = System.nanoTime();
                    status = "READY";
                    if (now >= nextPreviewAt) {
                        publishFrame(frame, preview, encoded, quality);
                        nextPreviewAt = now + PREVIEW_INTERVAL_NANOS;
                    }
                } else Thread.sleep(10);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            if (!startupTimedOut) {
                String message = error.getMessage();
                status = "Camera startup failed" + (message == null ? "." : ": " + message);
            }
        } finally {
            if (device != null) device.release();
            frame.release();
            preview.release();
            encoded.release();
            quality.release();
            synchronized (frameLock) {
                if (latestFrame != null) latestFrame.release();
                latestFrame = null;
                previewDataUrl = "";
            }
            running = false;
            if ("READY".equals(status)) status = "STOPPED";
        }
    }

    /** Uses Apple's native capture session without calling OpenCV's blocking camera backend. */
    private void captureWithAvFoundation(String helperPath) {
        Path framePath = null;
        Path logPath = null;
        Process process = null;
        try {
            framePath = Files.createTempFile("omr-camera-frame-", ".jpg");
            logPath = Files.createTempFile("omr-camera-helper-", ".log");
            Files.deleteIfExists(framePath);
            long deadline = System.nanoTime() + STARTUP_TIMEOUT_NANOS;
            startupStage = "waiting for a frame from the AVFoundation capture helper";
            status = "WAITING";
            process = new ProcessBuilder(helperPath, framePath.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(logPath.toFile())
                    .start();
            cameraProcess = process;
            byte[] previousBytes = null;
            while (running && System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    String details = Files.readString(logPath).trim();
                    throw new IllegalStateException(details.isEmpty()
                            ? "The AVFoundation camera helper exited before delivering a frame (" + process.exitValue() + ")."
                            : details);
                }
                if (Files.isRegularFile(framePath)) {
                    byte[] jpeg = Files.readAllBytes(framePath);
                    if (jpeg.length > 0 && !Arrays.equals(jpeg, previousBytes)) {
                        previousBytes = jpeg;
                        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
                        capturedDataUrl = dataUrl;
                        previewDataUrl = dataUrl;
                        status = "READY";
                    }
                }
                cameraHelperDiagnostics = Files.readString(logPath).trim();
                Thread.sleep(30);
            }
            if (running && System.nanoTime() >= deadline) status = startupTimeoutMessage();
        } catch (Throwable error) {
            if (!startupTimedOut) {
                String message = error.getMessage();
                status = "Camera startup failed" + (message == null ? "." : ": " + message);
            }
        } finally {
            cameraProcess = null;
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) process.destroyForcibly();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
            try {
                if (framePath != null) Files.deleteIfExists(framePath);
                if (logPath != null) Files.deleteIfExists(logPath);
            } catch (Exception ignored) { }
        }
    }

    private void publishFrame(Mat frame, Mat preview, MatOfByte encoded, MatOfInt quality) {
        Imgproc.resize(frame, preview, new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT));
        if (!Imgcodecs.imencode(".jpg", preview, encoded, quality)) return;
        String nextPreview = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(encoded.toArray());
        synchronized (frameLock) {
            if (latestFrame != null) latestFrame.release();
            latestFrame = frame.clone();
            previewDataUrl = nextPreview;
        }
    }

    private void checkStartupTimeout(long deadline) {
        if (System.nanoTime() >= deadline) {
            throw new IllegalStateException(startupTimeoutMessage());
        }
    }

    private String startupTimeoutMessage() {
        if (startupStage.startsWith("waiting for a frame")) {
            if (cameraHelperDiagnostics.contains("No camera device is currently available")) {
                return "macOS currently reports no available camera device. Check the camera in macOS System Information and test FaceTime or Photo Booth.";
            }
            if (cameraHelperDiagnostics.contains("Video devices reported by macOS:")) {
                return "macOS found a camera but its capture session delivered no frames. Restart the Mac, then test the camera in FaceTime or Photo Booth. Details: " + cameraHelperDiagnostics;
            }
            return "macOS opened the camera but did not deliver a frame. Restart your Mac, then confirm the camera works in FaceTime or Photo Booth before retrying.";
        }
        return switch (startupStage) {
            case "checking the bundled OpenCV library path" -> "Camera driver initialization timed out while checking the bundled OpenCV library path.";
            case "extracting OpenCV from its JAR" -> "Camera driver initialization timed out while extracting OpenCV from its JAR.";
            case "loading the bundled OpenCV native library" -> "Camera driver initialization timed out while loading the bundled OpenCV native library.";
            case "opening the camera" -> "Camera access timed out while opening the device. Check macOS Camera access and quit other camera apps, then restart OMR Scanner.";
            default -> "No camera frames arrived. Check macOS Camera access and restart your Mac if other camera apps also cannot connect.";
        };
    }

    private void loadOpenCvNativeLibrary() {
        String bundledLibrary = System.getProperty("omr.opencv.library");
        if (bundledLibrary == null || bundledLibrary.isBlank()) {
            startupStage = "extracting OpenCV from its JAR";
            OpenCV.loadLocally();
            return;
        }
        Path libraryPath = Path.of(bundledLibrary).toAbsolutePath().normalize();
        if (!Files.isRegularFile(libraryPath)) {
            throw new IllegalStateException("Bundled OpenCV library not found at " + libraryPath);
        }
        startupStage = "loading the bundled OpenCV native library";
        System.load(libraryPath.toString());
    }

    public synchronized void stop() {
        running = false;
        if (captureThread != null) captureThread.interrupt();
        Process process = cameraProcess;
        if (process != null) process.destroyForcibly();
        captureThread = null;
        status = "STOPPED";
    }
}
