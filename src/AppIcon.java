import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public final class AppIcon {
    private static final String ICON_PATH = "/app-icon.png";

    private AppIcon() {
    }

    public static void apply(Stage stage) {
        if (stage == null) {
            return;
        }

        try (InputStream inputStream = AppIcon.class.getResourceAsStream(ICON_PATH)) {
            if (inputStream != null) {
                stage.getIcons().setAll(new Image(inputStream));
            }
        } catch (Exception ignored) {
        }
    }
}
