package qupath.ext.classvisibility.core;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A restorable copy of the whole "why can't I see anything" surface on
 * {@link OverlayOptions}.
 *
 * <p>Deliberately wider than this panel's own three properties. A user who reaches for
 * <i>Restore visibility state</i> is not asking "undo my class rules", they are asking "put the
 * viewer back the way it was" -- and the thing that blanked their screen may have been the
 * opacity slider, a hidden object type, or a leftover object predicate rather than a class rule.
 * Restoring only what we wrote would answer a question nobody asked.</p>
 *
 * <p>This is <b>not</b> a snapshot of the object data. Nothing here reads or writes a
 * {@link PathObject}, a classification, or the project's class list.</p>
 *
 * <h2>Extending this</h2>
 * <p>A future per-group opacity feature edits {@code PathClass.setColor(Integer)}, whose alpha
 * byte reaches the painter -- and because {@code PathClass} instances are interned and global,
 * that is an edit to the user's colour scheme rather than a view setting, so it must be
 * snapshotted here to be recoverable. Add it as one more field plus a paired line in
 * {@link #capture(OverlayOptions)} and {@link #restore(OverlayOptions)}; {@code restore} already
 * tolerates a field that a snapshot taken by an older version left null. This is a class rather
 * than a record for exactly that reason -- adding a component to a record changes its canonical
 * constructor, and these snapshots are held across a session.</p>
 */
public final class VisibilitySnapshot {

    private Set<PathClass> selectedClasses;
    private OverlayOptions.ClassVisibilityMode visibilityMode;
    private boolean useExactSelectedClasses;
    private Predicate<PathObject> showObjectPredicate;
    private float opacity;
    private OverlayOptions.DetectionDisplayMode detectionDisplayMode;

    private boolean showDetections;
    private boolean showAnnotations;
    private boolean showTMAGrid;
    private boolean showConnections;
    private boolean fillDetections;
    private boolean fillAnnotations;
    private boolean showTMACoreLabels;
    private boolean showGrid;
    private boolean showPixelClassification;

    private VisibilitySnapshot() {
        // Built through capture().
    }

    /**
     * Take a snapshot of the supplied options.
     *
     * @param options the options to read; must not be null
     * @return the snapshot
     */
    public static VisibilitySnapshot capture(OverlayOptions options) {
        VisibilitySnapshot snapshot = new VisibilitySnapshot();
        snapshot.selectedClasses = new LinkedHashSet<>(options.selectedClassesProperty());
        snapshot.visibilityMode = options.getSelectedClassVisibilityMode();
        snapshot.useExactSelectedClasses = options.getUseExactSelectedClasses();
        snapshot.showObjectPredicate = options.getShowObjectPredicate();
        snapshot.opacity = options.getOpacity();
        snapshot.detectionDisplayMode = options.getDetectionDisplayMode();
        snapshot.showDetections = options.getShowDetections();
        snapshot.showAnnotations = options.getShowAnnotations();
        snapshot.showTMAGrid = options.getShowTMAGrid();
        snapshot.showConnections = options.getShowConnections();
        snapshot.fillDetections = options.getFillDetections();
        snapshot.fillAnnotations = options.getFillAnnotations();
        snapshot.showTMACoreLabels = options.getShowTMACoreLabels();
        snapshot.showGrid = options.getShowGrid();
        snapshot.showPixelClassification = options.getShowPixelClassification();
        return snapshot;
    }

    /**
     * Write this snapshot back. The class-rule set is applied as a minimal delta so entries that
     * are already correct are not removed and re-added -- each element change runs one
     * uncoalesced overlay-cache clear, and QuPath's own Classes pane shares the set.
     *
     * @param options the options to write; must not be null
     */
    public void restore(OverlayOptions options) {
        Set<PathClass> current = options.selectedClassesProperty();
        List<PathClass> toRemove = new ArrayList<>();
        for (PathClass entry : current) {
            if (!selectedClasses.contains(entry)) {
                toRemove.add(entry);
            }
        }
        List<PathClass> toAdd = new ArrayList<>();
        for (PathClass entry : selectedClasses) {
            if (!current.contains(entry)) {
                toAdd.add(entry);
            }
        }
        current.removeAll(toRemove);
        current.addAll(toAdd);

        options.setSelectedClassVisibilityMode(visibilityMode);
        options.setUseExactSelectedClasses(useExactSelectedClasses);
        options.setShowObjectPredicate(showObjectPredicate);
        options.setOpacity(opacity);
        if (detectionDisplayMode != null) {
            options.setDetectionDisplayMode(detectionDisplayMode);
        }
        options.setShowDetections(showDetections);
        options.setShowAnnotations(showAnnotations);
        options.setShowTMAGrid(showTMAGrid);
        options.setShowConnections(showConnections);
        options.setFillDetections(fillDetections);
        options.setFillAnnotations(fillAnnotations);
        options.setShowTMACoreLabels(showTMACoreLabels);
        options.setShowGrid(showGrid);
        options.setShowPixelClassification(showPixelClassification);
    }

    /** @return the class rules this snapshot holds, for display or diagnostics. */
    public Set<PathClass> selectedClasses() {
        // Not Set.copyOf: QuPath permits a null entry in this set as an Unclassified sentinel,
        // and Set.copyOf would throw on it.
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedClasses));
    }

    /** @return the class visibility mode this snapshot holds. */
    public OverlayOptions.ClassVisibilityMode visibilityMode() {
        return visibilityMode;
    }
}
