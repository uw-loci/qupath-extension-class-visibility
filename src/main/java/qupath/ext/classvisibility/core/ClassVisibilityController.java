package qupath.ext.classvisibility.core;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.SetChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifecycle owner for the panel: listener install and removal, image-follow, update gating and
 * the debounced off-thread harvest.
 *
 * <p>Nothing here captures {@code imageData}, {@code hierarchy} or {@code server} in a field at
 * construction. The hierarchy is re-read on every use and the hierarchy listener is moved when
 * the active image changes, so a panel left open across an image switch operates on the image
 * the user is actually looking at.</p>
 */
public final class ClassVisibilityController {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityController.class);

    /**
     * Trailing-edge debounce for hierarchy events. A classifier run fires them continuously; a
     * full class census per event would make the panel the slowest thing on screen during
     * exactly the workflow it exists for.
     */
    private static final long DEBOUNCE_MILLIS = 300;

    /** Callbacks into the UI. Every one is invoked on the JavaFX application thread. */
    public interface View {

        /**
         * A finished census is available.
         *
         * @param census the new census
         */
        void onCensus(ClassCensus census);

        /**
         * A harvest has started and is expected to take long enough to be worth saying so.
         *
         * @param imageName the image being counted
         * @param imageChanged true when the rows on screen belong to a different image, so they
         *                     must be disabled rather than merely marked stale
         */
        void onHarvestStarted(String imageName, boolean imageChanged);

        /**
         * The active image changed.
         *
         * @param imageName the new image's displayable name, or null when no image is open
         */
        void onImageChanged(String imageName);

        /** Something wrote to the shared selected-class set, so every derived state must re-render. */
        void onRulesChanged();
    }

    private final QuPathGUI qupath;
    private final View view;
    private final OverlayOptions options;

    private final ScheduledExecutorService harvester =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "class-visibility-harvest");
                t.setDaemon(true);
                return t;
            });

    /** Bumped on every harvest request, so a superseded result can be dropped on arrival. */
    private final AtomicLong generation = new AtomicLong();

    private ScheduledFuture<?> pending;

    /** Update gating: false when the tab is docked and either unselected or in a collapsed pane. */
    private final BooleanProperty paneVisible = new SimpleBooleanProperty(true);

    /** Whether counts re-harvest automatically. Bound to the preference by the pane. */
    private final BooleanProperty autoRefresh = new SimpleBooleanProperty(true);

    private ClassHarvester.Scope scope = ClassHarvester.Scope.DETECTIONS;

    private PathObjectHierarchy listeningTo;
    private boolean dirty = true;
    private boolean installed = false;

    private final PathObjectHierarchyListener hierarchyListener = event -> {
        if (event.isChanging()) {
            return;
        }
        markDirty();
    };

    private final ChangeListener<ImageData<BufferedImage>> imageListener =
            (obs, oldValue, newValue) -> handleImageChanged(newValue);

    /** Assigned in the constructor because it closes over {@code view}. */
    private final SetChangeListener<PathClass> selectedClassesListener;

    /**
     * @param qupath the running QuPath instance
     * @param view the UI to call back into
     */
    public ClassVisibilityController(QuPathGUI qupath, View view) {
        this.qupath = qupath;
        this.view = view;
        this.options = OverlayOptions.getSharedInstance();
        this.selectedClassesListener = change -> {
            if (Platform.isFxApplicationThread()) {
                view.onRulesChanged();
            } else {
                Platform.runLater(view::onRulesChanged);
            }
        };
    }

    /** @return the shared overlay options this panel reads and writes. */
    public OverlayOptions getOverlayOptions() {
        return options;
    }

    /**
     * @return whether the panel is currently visible enough to be worth updating. Both halves of
     *         QuPath's own gating idiom matter: an undocked tab has no tab pane and must stay
     *         live, and a docked tab in a collapsed analysis pane reads as selected but is not
     *         on screen.
     */
    public BooleanProperty paneVisibleProperty() {
        return paneVisible;
    }

    /** @return whether counts re-harvest automatically when objects change. */
    public BooleanProperty autoRefreshProperty() {
        return autoRefresh;
    }

    /** Attach every listener and take the first census. Idempotent. */
    public void install() {
        if (installed) {
            return;
        }
        installed = true;
        qupath.imageDataProperty().addListener(imageListener);
        options.selectedClassesProperty().addListener(selectedClassesListener);
        paneVisible.addListener((obs, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue) && dirty) {
                requestHarvest(true);
            }
        });
        handleImageChanged(qupath.imageDataProperty().get());
    }

    /** Detach every listener and stop the harvest thread. Idempotent. */
    public void uninstall() {
        if (!installed) {
            return;
        }
        installed = false;
        qupath.imageDataProperty().removeListener(imageListener);
        options.selectedClassesProperty().removeListener(selectedClassesListener);
        detachHierarchyListener();
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        harvester.shutdownNow();
    }

    /**
     * Change which objects are listed and counted, and re-harvest.
     *
     * @param newScope the new list scope
     */
    public void setScope(ClassHarvester.Scope newScope) {
        if (newScope == null || newScope == scope) {
            return;
        }
        scope = newScope;
        requestHarvest(true);
    }

    /** @return the current list scope. */
    public ClassHarvester.Scope getScope() {
        return scope;
    }

    /** @return the displayable name of the active image, or null when none is open. */
    public String currentImageName() {
        ImageData<BufferedImage> imageData = qupath.imageDataProperty().get();
        return imageData == null ? null : ServerTools.getDisplayableImageName(imageData.getServer());
    }

    /**
     * Ask for a fresh census.
     *
     * @param immediate true to skip the debounce (a user action, an image switch, a scope change)
     */
    public void requestHarvest(boolean immediate) {
        if (!installed) {
            return;
        }
        if (!paneVisible.get()) {
            dirty = true;
            return;
        }
        scheduleHarvest(immediate ? 0 : DEBOUNCE_MILLIS, false);
    }

    private void markDirty() {
        if (!autoRefresh.get()) {
            dirty = true;
            return;
        }
        requestHarvest(false);
    }

    private void scheduleHarvest(long delayMillis, boolean imageChanged) {
        if (pending != null) {
            pending.cancel(false);
        }
        dirty = false;
        long gen = generation.incrementAndGet();
        PathObjectHierarchy hierarchy = currentHierarchy();
        String imageName = currentImageName();
        ClassHarvester.Scope harvestScope = scope;
        if (imageChanged || delayMillis == 0) {
            view.onHarvestStarted(imageName, imageChanged);
        }
        try {
            pending = harvester.schedule(() -> {
                ClassCensus census;
                try {
                    census = ClassHarvester.harvest(hierarchy, harvestScope);
                } catch (RuntimeException ex) {
                    logger.warn("Class harvest failed: {}", ex.getMessage(), ex);
                    census = ClassCensus.EMPTY;
                }
                ClassCensus result = census;
                Platform.runLater(() -> {
                    // Drop a result that a later request has already superseded.
                    if (gen == generation.get()) {
                        view.onCensus(result);
                    }
                });
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // The panel was disposed between the request and the schedule; nothing to do.
            logger.debug("Harvest rejected after shutdown");
        }
    }

    private PathObjectHierarchy currentHierarchy() {
        ImageData<BufferedImage> imageData = qupath.imageDataProperty().get();
        return imageData == null ? null : imageData.getHierarchy();
    }

    private void handleImageChanged(ImageData<BufferedImage> imageData) {
        detachHierarchyListener();
        if (imageData != null) {
            listeningTo = imageData.getHierarchy();
            listeningTo.addListener(hierarchyListener);
        }
        String imageName = imageData == null
                ? null
                : ServerTools.getDisplayableImageName(imageData.getServer());
        view.onImageChanged(imageName);
        if (paneVisible.get()) {
            scheduleHarvest(0, true);
        } else {
            dirty = true;
        }
    }

    private void detachHierarchyListener() {
        if (listeningTo != null) {
            listeningTo.removeListener(hierarchyListener);
            listeningTo = null;
        }
    }
}
