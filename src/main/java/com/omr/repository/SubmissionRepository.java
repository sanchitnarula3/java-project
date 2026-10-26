package com.omr.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.omr.model.Quiz;
import com.omr.model.ScanResult;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** File-backed submission store that survives application restarts. */
public class SubmissionRepository {
    private static final ConcurrentMap<String, ConcurrentMap<String, ScanResult>> SUBMISSIONS = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static final Path STORE = Path.of(System.getProperty("user.home"), ".omr-mcq-system", "submissions.json");
    private static final Type STORE_TYPE = new TypeToken<ConcurrentHashMap<String, ConcurrentHashMap<String, ScanResult>>>() {}.getType();
    private static final Gson JSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> context.serialize(value.toString()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (value, type, context) -> Instant.parse(value.getAsString()))
            .create();

    static {
        synchronized (LOCK) {
            if (Files.exists(STORE)) {
                try (var reader = Files.newBufferedReader(STORE)) {
                    ConcurrentHashMap<String, ConcurrentHashMap<String, ScanResult>> loaded = JSON.fromJson(reader, STORE_TYPE);
                    if (loaded != null) {
                        loaded.values().forEach(records -> records.values().forEach(result -> {
                            if (result.getMaximumMarks() <= 0) result.setMaximumMarks(Math.max(1, result.getTotalQuestions()));
                            if (result.getMarksAwarded() == 0 && result.getCorrectCount() > 0 && result.getTotalQuestions() > 0) {
                                result.setMarksAwarded(Math.round((double) result.getCorrectCount() * result.getMaximumMarks()
                                        / result.getTotalQuestions() * 100.0) / 100.0);
                            }
                        }));
                        SUBMISSIONS.putAll(loaded);
                    }
                } catch (IOException | RuntimeException error) {
                    throw new ExceptionInInitializerError(new IllegalStateException("Could not read saved scan records at " + STORE, error));
                }
            }
        }
    }

    /** Save or replace a student's latest scan for this quiz. */
    public ScanResult save(ScanResult result) {
        if (result == null || result.getQuizId() == null) return result;
        synchronized (LOCK) {
            String quizId = result.getQuizId();
            SUBMISSIONS.putIfAbsent(quizId, new ConcurrentHashMap<>());
            String roll = result.getRollNumber();
            String key = roll != null && !roll.isBlank() && !"Unknown".equalsIgnoreCase(roll)
                    ? roll.trim() : "UNKNOWN_" + UUID.randomUUID().toString().substring(0, 8);
            SUBMISSIONS.get(quizId).put(key, result);
            persist();
        }
        return result;
    }

    public List<ScanResult> findByQuizId(String quizId) {
        if (quizId == null) return Collections.emptyList();
        ConcurrentMap<String, ScanResult> quizMap = SUBMISSIONS.get(quizId);
        if (quizMap == null) return Collections.emptyList();
        List<ScanResult> list = new ArrayList<>(quizMap.values());
        list.sort(Comparator.comparing(r -> r.getRollNumber() == null ? "ZZZ" : r.getRollNumber(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Optional<ScanResult> findByQuizIdAndRollNumber(String quizId, String rollNumber) {
        if (quizId == null || rollNumber == null) return Optional.empty();
        ConcurrentMap<String, ScanResult> quizMap = SUBMISSIONS.get(quizId);
        return quizMap == null ? Optional.empty() : Optional.ofNullable(quizMap.get(rollNumber.trim()));
    }

    public boolean delete(String quizId, String rollNumber) {
        if (quizId == null || rollNumber == null) return false;
        synchronized (LOCK) {
            ConcurrentMap<String, ScanResult> quizMap = SUBMISSIONS.get(quizId);
            boolean removed = quizMap != null && quizMap.remove(rollNumber.trim()) != null;
            if (removed) persist();
            return removed;
        }
    }

    public void clearQuiz(String quizId) {
        if (quizId == null) return;
        synchronized (LOCK) {
            SUBMISSIONS.remove(quizId);
            persist();
        }
    }

    /** Update an auto-read roll number while keeping the result associated with the student. */
    public Optional<ScanResult> updateRollNumber(String quizId, String oldRollNumber, String newRollNumber) {
        return updateRollNumber(quizId, oldRollNumber, newRollNumber, null);
    }

    /** Prefer the scan timestamp to distinguish multiple records whose roll number is Unknown. */
    public Optional<ScanResult> updateRollNumber(String quizId, String oldRollNumber, String newRollNumber, String timestamp) {
        if (quizId == null || oldRollNumber == null || newRollNumber == null || newRollNumber.isBlank()) return Optional.empty();
        synchronized (LOCK) {
            ConcurrentMap<String, ScanResult> quizMap = SUBMISSIONS.get(quizId);
            if (quizMap == null) return Optional.empty();
            String oldKey = quizMap.containsKey(oldRollNumber.trim()) ? oldRollNumber.trim() : null;
            if (oldKey == null) {
                for (var entry : quizMap.entrySet()) {
                    boolean sameRoll = oldRollNumber.trim().equalsIgnoreCase(entry.getValue().getRollNumber());
                    boolean sameTimestamp = timestamp != null && entry.getValue().getTimestamp() != null
                            && timestamp.equals(entry.getValue().getTimestamp().toString());
                    if (sameRoll && (timestamp == null || sameTimestamp)) {
                        oldKey = entry.getKey();
                        break;
                    }
                }
            }
            if (oldKey == null) return Optional.empty();
            String newKey = newRollNumber.trim();
            ScanResult existing = quizMap.get(newKey);
            if (existing != null && existing != quizMap.get(oldKey)) return Optional.empty();
            ScanResult result = quizMap.remove(oldKey);
            result.setRollNumber(newKey);
            quizMap.put(newKey, result);
            persist();
            return Optional.of(result);
        }
    }

    public String generateCsv(String quizId, Quiz quiz) {
        List<ScanResult> submissions = findByQuizId(quizId);
        String subject = quiz != null && quiz.getTitle() != null ? quiz.getTitle() : "OMR Quiz";
        int numQuestions = quiz != null ? quiz.getNumberOfQuestions() : 15;
        StringBuilder sb = new StringBuilder("Roll Number,Subject,Score,Total Marks,Percentage,Correct,Wrong,Blank,Ambiguous,Timestamp,Status");
        for (int q = 1; q <= numQuestions; q++) sb.append(",Q").append(q);
        sb.append("\n");
        for (ScanResult res : submissions) {
            sb.append(escapeCsv(res.getRollNumber())).append(",")
                    .append(escapeCsv(subject)).append(",")
                    .append(String.format(Locale.ROOT, "%.2f", res.getMarksAwarded())).append(",")
                    .append(String.format(Locale.ROOT, "%.2f", res.getMaximumMarks())).append(",")
                    .append(String.format(Locale.ROOT, "%.2f%%", res.getScorePercentage())).append(",")
                    .append(res.getCorrectCount()).append(",")
                    .append(res.getWrongCount()).append(",")
                    .append(res.getBlankCount()).append(",")
                    .append(res.getAmbiguousCount()).append(",")
                    .append(escapeCsv(res.getTimestamp() != null ? res.getTimestamp().toString() : "")).append(",")
                    .append(escapeCsv(res.getStatus() != null ? res.getStatus().name() : "SUCCESS"));
            Map<Integer, String> answers = res.getDetectedAnswers();
            for (int q = 1; q <= numQuestions; q++) {
                String answer = answers != null ? answers.get(q) : "";
                sb.append(",").append(escapeCsv(answer));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void persist() {
        try {
            Files.createDirectories(STORE.getParent());
            Path temporary = Files.createTempFile(STORE.getParent(), "submissions-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary)) { JSON.toJson(SUBMISSIONS, writer); }
            try { Files.move(temporary, STORE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, STORE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save scan records at " + STORE, error);
        }
    }
}
