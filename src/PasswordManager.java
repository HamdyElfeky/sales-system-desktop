import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Properties;

public final class PasswordManager {
    private static final String DEFAULT_PASSWORD = "6666";
    private static final String MASTER_PASSWORD = "01021700887";
    private static final Path PASSWORD_FILE = Paths.get("stock-password.properties");
    private static final String PASSWORD_HASH_KEY = "passwordHash";

    private PasswordManager() {
    }

    public static boolean isValidPassword(String password) {
        String safePassword = password == null ? "" : password.trim();
        if (safePassword.isEmpty()) {
            return false;
        }

        return MASTER_PASSWORD.equals(safePassword) || hash(safePassword).equals(loadStoredPasswordHash());
    }

    public static boolean changePassword(String currentPassword, String newPassword) {
        if (!isValidPassword(currentPassword)) {
            return false;
        }

        saveStoredPasswordHash(hash(newPassword));
        return true;
    }

    public static void resetToDefaultPassword() {
        saveStoredPasswordHash(hash(DEFAULT_PASSWORD));
    }

    private static String loadStoredPasswordHash() {
        if (!Files.exists(PASSWORD_FILE)) {
            String defaultHash = hash(DEFAULT_PASSWORD);
            saveStoredPasswordHash(defaultHash);
            return defaultHash;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(PASSWORD_FILE)) {
            properties.load(inputStream);
            return properties.getProperty(PASSWORD_HASH_KEY, hash(DEFAULT_PASSWORD));
        } catch (IOException ex) {
            return hash(DEFAULT_PASSWORD);
        }
    }

    private static void saveStoredPasswordHash(String hashValue) {
        Properties properties = new Properties();
        properties.setProperty(PASSWORD_HASH_KEY, hashValue);

        try (OutputStream outputStream = Files.newOutputStream(PASSWORD_FILE)) {
            properties.store(outputStream, "Stock form password hash");
        } catch (IOException ex) {
            throw new RuntimeException("Unable to save password", ex);
        }
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Unable to hash password", ex);
        }
    }
}
