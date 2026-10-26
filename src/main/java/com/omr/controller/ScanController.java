package com.omr.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import com.omr.model.Quiz;
import com.omr.model.ScanResult;
import com.omr.repository.QuizRepository;
import com.omr.repository.SubmissionRepository;
import com.omr.service.EvaluationService;
import com.omr.service.ScanService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scanning, result retrieval, local submission records, and CSV export.
 */
@WebServlet("/api/scan/*")
@MultipartConfig(maxFileSize = 10 * 1024 * 1024, maxRequestSize = 12 * 1024 * 1024)
public class ScanController extends HttpServlet {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> context.serialize(value.toString()))
            .create();

    private final Map<String, ScanResult> latestResults = new ConcurrentHashMap<>();
    private QuizRepository quizzes;
    private SubmissionRepository submissions;
    private ScanService scanner;

    @Override
    public void init() throws ServletException {
        quizzes = new QuizRepository();
        submissions = new SubmissionRepository();
        scanner = new ScanService(new EvaluationService());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        Part image = req.getPart("image");
        String quizId = req.getParameter("quizId");
        String overrideRoll = req.getParameter("rollNumber");

        if (image == null || image.getSize() == 0) {
            json(resp, 400, Map.of("error", "No image uploaded"));
            return;
        }

        Quiz quiz = quizzes.findById(quizId == null ? "" : quizId).orElse(null);
        if (quiz == null) {
            json(resp, 400, Map.of("error", "Quiz not found"));
            return;
        }
        if (quiz.getAnswerKey() == null || quiz.getAnswerKey().isEmpty()) {
            json(resp, 400, Map.of("error", "Set the answer key before scanning"));
            return;
        }

        ScanResult result = scanner.processFrame(image.getInputStream().readAllBytes(), quiz);

        if (overrideRoll != null && !overrideRoll.isBlank()) {
            result.setRollNumber(overrideRoll.trim());
        }

        if (result.getStatus() == ScanResult.ScanStatus.SUCCESS) {
            submissions.save(result);
        }

        latestResults.put(quizId, result);
        json(resp, 200, result);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = Optional.ofNullable(req.getPathInfo()).orElse("");
        String[] parts = Arrays.stream(pathInfo.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (parts.length != 2 || !"submissions".equals(parts[0])) {
            json(resp, 404, Map.of("error", "Unknown scan endpoint"));
            return;
        }
        JsonObject body = JSON.fromJson(req.getReader(), JsonObject.class);
        String oldRoll = body != null && body.has("oldRollNumber") ? body.get("oldRollNumber").getAsString() : "";
        String newRoll = body != null && body.has("newRollNumber") ? body.get("newRollNumber").getAsString().trim() : "";
        String timestamp = body != null && body.has("timestamp") ? body.get("timestamp").getAsString() : null;
        if (!newRoll.matches("[0-9]{10}")) {
            json(resp, 400, Map.of("error", "Roll number must contain exactly 10 digits"));
            return;
        }
        Optional<ScanResult> updated = submissions.updateRollNumber(parts[1], oldRoll, newRoll, timestamp);
        if (updated.isEmpty()) {
            json(resp, 409, Map.of("error", "Could not update roll number. Check for an existing record with that roll number."));
            return;
        }
        latestResults.put(parts[1], updated.get());
        json(resp, 200, updated.get());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = Optional.ofNullable(req.getPathInfo()).orElse("");
        String[] parts = Arrays.stream(pathInfo.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);

        // GET /api/scan/results/{quizId}
        if (parts.length == 2 && "results".equals(parts[0])) {
            ScanResult result = latestResults.get(parts[1]);
            if (result == null) {
                resp.setStatus(404);
                return;
            }
            json(resp, 200, result);
            return;
        }

        // GET /api/scan/submissions/{quizId} or query parameter ?quizId=...
        if ((parts.length == 2 && "submissions".equals(parts[0])) || (parts.length == 1 && "submissions".equals(parts[0]))) {
            String quizId = parts.length == 2 ? parts[1] : req.getParameter("quizId");
            if (quizId == null || quizId.isBlank()) {
                json(resp, 400, Map.of("error", "quizId required"));
                return;
            }
            List<ScanResult> list = submissions.findByQuizId(quizId);
            json(resp, 200, list);
            return;
        }

        // GET /api/scan/csv/{quizId} or /api/scan/export-csv?quizId=...
        if ((parts.length == 2 && ("csv".equals(parts[0]) || "export-csv".equals(parts[0])))
                || (parts.length == 1 && "export-csv".equals(parts[0]))) {
            String quizId = parts.length == 2 ? parts[1] : req.getParameter("quizId");
            if (quizId == null || quizId.isBlank()) {
                json(resp, 400, Map.of("error", "quizId required"));
                return;
            }

            Quiz quiz = quizzes.findById(quizId).orElse(null);
            if (quiz == null) {
                json(resp, 404, Map.of("error", "Quiz not found"));
                return;
            }

            String csvData = submissions.generateCsv(quizId, quiz);
            String safeTitle = quiz.getTitle() != null ? quiz.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_") : "quiz";
            String filename = safeTitle + "_" + quizId + "_scores.csv";

            resp.setStatus(200);
            resp.setContentType("text/csv; charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            resp.getOutputStream().write(csvData.getBytes(StandardCharsets.UTF_8));
            return;
        }

        json(resp, 404, Map.of("error", "Unknown scan endpoint"));
    }

    private static void json(HttpServletResponse r, int status, Object value) throws IOException {
        r.setStatus(status);
        r.setContentType("application/json;charset=UTF-8");
        JSON.toJson(value, r.getWriter());
    }
}
