package com.omr.repository;

import com.omr.model.Teacher;
import com.omr.security.PasswordUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Local file-backed teacher accounts; passwords are stored only as salted hashes. */
public class TeacherRepository {
    private static final ConcurrentMap<String, Account> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static final Path STORE = Path.of(System.getProperty("user.home"), ".omr-mcq-system", "teachers.properties");

    public TeacherRepository() {
        synchronized (LOCK) {
            load();
            if (!ACCOUNTS.containsKey("abc@thapar.edu")) {
                String salt = PasswordUtil.newSalt();
                ACCOUNTS.put("abc@thapar.edu", new Account("Teacher", salt,
                        PasswordUtil.hash("1234567890", salt)));
                save();
            }
        }
    }

    public boolean create(String name, String email, String rawPassword) {
        String normalizedEmail = normalize(email);
        synchronized (LOCK) {
            if (ACCOUNTS.containsKey(normalizedEmail)) return false;
            String salt = PasswordUtil.newSalt();
            ACCOUNTS.put(normalizedEmail, new Account(name, salt, PasswordUtil.hash(rawPassword, salt)));
            save();
            return true;
        }
    }

    public Optional<Teacher> authenticate(String email, String rawPassword) {
        String normalizedEmail = normalize(email);
        Account account = ACCOUNTS.get(normalizedEmail);
        if (account == null || !PasswordUtil.verify(rawPassword, account.salt(), account.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(new Teacher(account.name(), normalizedEmail));
    }

    private static void load() {
        if (!Files.exists(STORE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(STORE)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("account.") || !key.endsWith(".name")) continue;
                String encodedEmail = key.substring("account.".length(), key.length() - ".name".length());
                String email = new String(Base64.getUrlDecoder().decode(encodedEmail), java.nio.charset.StandardCharsets.UTF_8);
                String prefix = "account." + encodedEmail + ".";
                String salt = properties.getProperty(prefix + "salt");
                String hash = properties.getProperty(prefix + "hash");
                if (salt != null && hash != null) ACCOUNTS.put(normalize(email),
                        new Account(properties.getProperty(key), salt, hash));
            }
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Could not read local teacher accounts at " + STORE, error);
        }
    }

    private static void save() {
        Properties properties = new Properties();
        ACCOUNTS.forEach((email, account) -> {
            String encodedEmail = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String prefix = "account." + encodedEmail + ".";
            properties.setProperty(prefix + "name", account.name());
            properties.setProperty(prefix + "salt", account.salt());
            properties.setProperty(prefix + "hash", account.passwordHash());
        });
        try {
            Files.createDirectories(STORE.getParent());
            Path temporary = Files.createTempFile(STORE.getParent(), "teachers-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "OMR Scanner teacher accounts (salted password hashes)");
            }
            try {
                Files.move(temporary, STORE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, STORE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save local teacher accounts at " + STORE, error);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Account(String name, String salt, String passwordHash) {}
}
