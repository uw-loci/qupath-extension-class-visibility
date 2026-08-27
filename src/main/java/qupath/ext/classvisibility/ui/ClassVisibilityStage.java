package qupath.ext.classvisibility.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.preferences.ClassVisibilityPreferences;

/**
 * The floating window that holds {@link ClassVisibilityPane}.
 *
 * <p>This is the panel's <b>first</b> surface: the toolbar button creates this window, and
 * docking into QuPath's analysis pane is a later, explicit user action. Installing the extension
 * must never add a tab to anybody's analysis pane, which is the requirement the whole
 * window-first design follows from.</p>
 *
 * <p>A thin wrapper, deliberately. It owns geometry, modality and the multi-monitor clamp, and
 * nothing else -- the Pane inside it is self-contained and has no idea which surface it is in,
 * which is what makes {@link #releasePane()} a reparenting operation rather than a rebuild. The
 * user's rules, filter text, sort order and scroll position all survive a dock or an undock
 * because the same Pane instance moves.</p>
 *
 * <p>Modality is {@link Modality#NONE} and the owner is QuPath's main stage, following
 * {@code MetadataBrowserWindow}: the panel is a tool the user works alongside, never a dialog
 * that blocks the viewer they are trying to look at.</p>
 */
public final class ClassVisibilityStage {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityStage.class);

    /** Enough for the narrow profile to be usable; below this the tables stop being readable. */
    private static final double MIN_WIDTH = 380;

    /** Two lists plus header and status strip; below this the split collapses one list to nothing. */
    private static final double MIN_HEIGHT = 420;

    private static final double DEFAULT_WIDTH = 760;
    private static final double DEFAULT_HEIGHT = 620;

    private final Stage stage;
    private final Window owner;
    private ClassVisibilityPane pane;

    /**
     * @param owner QuPath's main stage, used as the window owner
     * @param pane the panel to show; the same instance can later be released and re-parented
     */
    public ClassVisibilityStage(Window owner, ClassVisibilityPane pane) {
        this.owner = owner;
        this.pane = pane;
        this.stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.titleProperty().bind(pane.titleProperty());
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(new Scene(pane, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        // Geometry is saved on every hide, including the hide that happens when the user docks
        // the panel -- so undocking later returns the window to where they last had it.
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> saveGeometry());
    }

    /** @return the underlying stage. */
    public Stage getStage() {
        return stage;
    }

    /** @return true when the window is on screen. */
    public boolean isShowing() {
        return stage.isShowing();
    }

    /** Show the window, restoring saved geometry or centring it on QuPath's main window. */
    public void show() {
        if (stage.isShowing()) {
            raise();
            return;
        }
        applyGeometry();
        stage.show();
        if (ClassVisibilityPreferences.isSentinelGeometry()) {
            centerOnOwner();
        }
    }

    /** Bring an already-showing window to the front. */
    public void raise() {
        stage.toFront();
        stage.requestFocus();
    }

    /** Hide the window. Geometry is saved by the {@code WINDOW_HIDDEN} handler. */
    public void hide() {
        stage.hide();
    }

    /**
     * Detach the panel so it can be re-parented into a tab, and hide the window.
     *
     * <p>The scene keeps an empty root rather than being discarded, because a {@code Scene} whose
     * root is still the Pane would keep the Pane attached and a second parent would throw.</p>
     *
     * @return the panel, now with no parent
     */
    public ClassVisibilityPane releasePane() {
        ClassVisibilityPane released = pane;
        stage.titleProperty().unbind();
        if (stage.getScene() != null) {
            stage.getScene().setRoot(new StackPane());
        }
        pane = null;
        hide();
        return released;
    }

    private void applyGeometry() {
        if (ClassVisibilityPreferences.isSentinelGeometry()) {
            return;
        }
        double[] g = ClassVisibilityPreferences.getWindowGeometry();
        Rectangle2D bounds = screenContaining(g[0] + g[2] / 2.0, g[1] + g[3] / 2.0);
        double width = Math.max(MIN_WIDTH, Math.min(g[2], bounds.getWidth()));
        double height = Math.max(MIN_HEIGHT, Math.min(g[3], bounds.getHeight()));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(clamp(g[0], bounds.getMinX(), bounds.getMaxX() - width));
        stage.setY(clamp(g[1], bounds.getMinY(), bounds.getMaxY() - height));
    }

    private void saveGeometry() {
        if (stage.getWidth() <= 0 || stage.getHeight() <= 0) {
            return;
        }
        ClassVisibilityPreferences.saveWindowGeometry(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    /**
     * Centre on QuPath's main window, then clamp to the visual bounds of whichever screen holds
     * the resulting centre point. Without the clamp, a QuPath window left partly off-screen by a
     * previous multi-monitor session places this window at a negative coordinate, where the user
     * cannot reach it.
     */
    private void centerOnOwner() {
        if (owner == null || !owner.isShowing()) {
            return;
        }
        double width = stage.getWidth();
        double height = stage.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() / 2.0;
        Rectangle2D bounds = screenContaining(cx, cy);
        stage.setX(clamp(cx - width / 2.0, bounds.getMinX(), bounds.getMaxX() - width));
        stage.setY(clamp(cy - height / 2.0, bounds.getMinY(), bounds.getMaxY() - height));
        logger.debug("Centred Class visibility window at {}, {}", stage.getX(), stage.getY());
    }

    private static Rectangle2D screenContaining(double x, double y) {
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D visual = screen.getVisualBounds();
            if (visual.contains(x, y)) {
                return visual;
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
