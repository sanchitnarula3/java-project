package com.omr.repository;

import com.omr.model.Quiz;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Local file-backed quiz storage shared by the servlets and preserved between app launches. */
public class QuizRepository {
    private static final ConcurrentMap<String, Quiz> QUIZZES = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static final Path STORE = Path.of(System.getProperty("user.home"), ".omr-mcq-system", "quizzes.json");
    private static final Type QUIZ_MAP = new TypeToken<ConcurrentHashMap<String, Quiz>>() {}.getType();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        synchronized (LOCK) {
            if (Files.exists(STORE)) {
                try (var reader = Files.newBufferedReader(STORE)) {
                    ConcurrentHashMap<String, Quiz> loaded = JSON.fromJson(reader, QUIZ_MAP);
                    if (loaded != null) QUIZZES.putAll(loaded);
                } catch (IOException | RuntimeException error) {
                    throw new ExceptionInInitializerError(new IllegalStateException("Could not read saved quizzes at " + STORE, error));
                }
            }
        }
    }

    public Quiz save(Quiz quiz) {
        Quiz stored = copy(quiz);
        synchronized (LOCK) {
            QUIZZES.put(stored.getId(), stored);
            persist();
        }
        return copy(stored);
    }

    public Optional<Quiz> findById(String id) {
        Quiz quiz = QUIZZES.get(id);
        return quiz == null ? Optional.empty() : Optional.of(copy(quiz));
    }

    public List<Quiz> findAll() {
        List<Quiz> quizzes = new ArrayList<>();
        for (Quiz quiz : QUIZZES.values()) quizzes.add(copy(quiz));
        quizzes.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        return quizzes;
    }

    public boolean deleteById(String id) {
        synchronized (LOCK) {
            boolean removed = QUIZZES.remove(id) != null;
            if (removed) persist();
            return removed;
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(STORE.getParent());
            Path temporary = Files.createTempFile(STORE.getParent(), "quizzes-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary)) { JSON.toJson(QUIZZES, writer); }
            try { Files.move(temporary, STORE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, STORE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save quizzes at " + STORE, error);
        }
    }

    private static Quiz copy(Quiz source) {
        Quiz copy = new Quiz();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setNumberOfQuestions(source.getNumberOfQuestions());
        copy.setOptionsPerQuestion(source.getOptionsPerQuestion());
        copy.setMaximumMarks(source.getMaximumMarks() > 0 ? source.getMaximumMarks() : 10.0);
        Map<Integer, String> answerKey = source.getAnswerKey();
        copy.setAnswerKey(answerKey == null ? new LinkedHashMap<>() : new LinkedHashMap<>(answerKey));
        return copy;
    }
}
