package qupath.ext.classvisibility.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A named visibility state, saved into the project.
 *
 * <p>The same idea as the Brightness &amp; Contrast dialog's <i>Settings</i>: a view worth
 * returning to, given a name, stored with the project so it is there tomorrow and there for
 * whoever else opens the project. Written as JSON through the project's own
 * {@code ResourceManager}, so nothing here invents a file format or a location.</p>
 *
 * <h2>Classes are stored as strings, never as objects</h2>
 * <p>Every class is stored as {@code PathClass.toString()} and rebuilt with
 * {@link PathClass#fromString(String)}. {@code PathClass} instances are interned and global,
 * carry a colour, and have no stable JSON form worth relying on -- and the set-based
 * reconstruction route has a documented caveat ({@code PathClass.java:390-397}), so
 * reconstruction has to go through the string form regardless. The payoff is that a preset is
 * portable: any project whose classes are named the same way can use it, which is most of the
 * value of naming a view in the first place.</p>
 *
 * <p><b>Unclassified is stored as JSON {@code null}</b>, not as a sentinel word.
 * {@code PathClass.fromString(null)} is documented to return {@code NULL_CLASS}, and a null
 * cannot collide with the name of a real class the way {@code "Unclassified"} could.</p>
 *
 * <h2>What a preset captures, and what it does not</h2>
 * <p>It captures the <b>viewer state</b> -- the class rule set, the visibility mode, the exact
 * flag, the cell display mode, overlay opacity and the per-type show/fill booleans -- and the
 * <b>panel state</b>: which classes and which components were checked, and whether components
 * combined as Any or All.</p>
 *
 * <p>Both, because either alone restores something the user did not save. The class set alone
 * makes a preset named "T cells" come back with an empty Components list, so the user cannot see
 * where the rules came from and cannot adjust them. The checks alone would silently drop any rule
 * set from QuPath's own Classes pane, and would re-derive a set that may differ if the image's
 * classes have changed since. <b>The class set is authoritative on restore</b>; the checks are
 * restored alongside it so the panel explains itself.</p>
 *
 * <p>It does <b>not</b> capture {@code showObjectPredicate}. It is a {@link java.util.function
 * Predicate}, i.e. code, and there is no honest way to write one to JSON. A preset therefore
 * leaves any object predicate exactly as it found it rather than pretending to restore one.</p>
 *
 * <p>A plain class with a no-argument constructor and no final fields, because Gson builds it
 * that way, and {@code version} is written so a future field can be added without a preset saved
 * today becoming unreadable.</p>
 */
public final class VisibilityPreset {

    private static final Logger logger = LoggerFactory.getLogger(VisibilityPreset.class);

    /** Bumped only when the meaning of an existing field changes, never for an added one. */
    private static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private String name;

    /** The authoritative rule set: exactly what was in {@code selectedClasses}. */
    private List<String> classRules = new ArrayList<>();

    /** Which class rows were checked in the panel. */
    private List<String> checkedClasses = new ArrayList<>();

    /** Which component rows were checked in the panel. */
    private List<String> checkedComponents = new ArrayList<>();

    private VisibilityRuleModel.Combination combination = VisibilityRuleModel.Combination.ANY;
    private OverlayOptions.ClassVisibilityMode visibilityMode =
            OverlayOptions.ClassVisibilityMode.HIDE_SELECTED;
    private boolean useExactSelectedClasses;
    private OverlayOptions.DetectionDisplayMode cellDisplayMode;
    private float opacity = 1f;

    private boolean showDetections = true;
    private boolean showAnnotations = true;
    private boolean showTMAGrid = true;
    private boolean showConnections;
    private boolean fillDetections;
    private boolean fillAnnotations = true;
    private boolean showTMACoreLabels = true;
    private boolean showGrid;
    private boolean showPixelClassification = true;

    /** For Gson, and for {@link #capture}. */
    public VisibilityPreset() {
        // Fields are populated by capture() or by deserialization.
    }

    /**
     * Capture the current state under a name.
     *
     * @param name the preset name, as the user typed it
     * @param options the options to read
     * @param model the panel's rule model, for the checks behind the rules
     * @return the preset
     */
    public static VisibilityPreset capture(String name, OverlayOptions options,
                                           VisibilityRuleModel model) {
        VisibilityPreset preset = new VisibilityPreset();
        preset.version = CURRENT_VERSION;
        preset.name = name;
        VisibilityRuleModel.ModelState state = model.captureState();
        preset.classRules = toTokens(state.entries());
        preset.checkedClasses = toTokens(state.exact());
        preset.checkedComponents = new ArrayList<>(state.components());
        preset.combination = state.combination();
        preset.visibilityMode = options.getSelectedClassVisibilityMode();
        preset.useExactSelectedClasses = options.getUseExactSelectedClasses();
        preset.cellDisplayMode = options.getDetectionDisplayMode();
        preset.opacity = options.getOpacity();
        preset.showDetections = options.getShowDetections();
        preset.showAnnotations = options.getShowAnnotations();
        preset.showTMAGrid = options.getShowTMAGrid();
        preset.showConnections = options.getShowConnections();
        preset.fillDetections = options.getFillDetections();
        preset.fillAnnotations = options.getFillAnnotations();
        preset.showTMACoreLabels = options.getShowTMACoreLabels();
        preset.showGrid = options.getShowGrid();
        preset.showPixelClassification = options.getShowPixelClassification();
        return preset;
    }

    /**
     * Apply this preset.
     *
     * <p>Viewer state first, rules last. The mode and the exact flag change what a given rule set
     * means, so writing them after the rules would repaint once through a combination the preset
     * does not describe.</p>
     *
     * @param options the options to write
     * @param model the panel's rule model, which owns the delta against the live set
     */
    public void restore(OverlayOptions options, VisibilityRuleModel model) {
        options.setSelectedClassVisibilityMode(visibilityMode == null
                ? OverlayOptions.ClassVisibilityMode.HIDE_SELECTED
                : visibilityMode);
        options.setUseExactSelectedClasses(useExactSelectedClasses);
        if (cellDisplayMode != null) {
            options.setDetectionDisplayMode(cellDisplayMode);
        }
        options.setOpacity(opacity);
        options.setShowDetections(showDetections);
        options.setShowAnnotations(showAnnotations);
        options.setShowTMAGrid(showTMAGrid);
        options.setShowConnections(showConnections);
        options.setFillDetections(fillDetections);
        options.setFillAnnotations(fillAnnotations);
        options.setShowTMACoreLabels(showTMACoreLabels);
        options.setShowGrid(showGrid);
        options.setShowPixelClassification(showPixelClassification);
        model.restoreState(new VisibilityRuleModel.ModelState(
                fromTokens(classRules),
                fromTokens(checkedClasses),
                new LinkedHashSet<>(checkedComponents == null ? List.of() : checkedComponents),
                combination == null ? VisibilityRuleModel.Combination.ANY : combination));
    }

    /** @return the preset name. */
    public String getName() {
        return name;
    }

    /**
     * @param classes classes to write
     * @return their string tokens, with null (Unclassified) preserved as a null element
     */
    private static List<String> toTokens(Set<PathClass> classes) {
        List<String> tokens = new ArrayList<>();
        for (PathClass pathClass : classes) {
            tokens.add(pathClass == null || pathClass == PathClass.NULL_CLASS
                    ? null
                    : pathClass.toString());
        }
        return tokens;
    }

    /**
     * @param tokens tokens read back from JSON
     * @return the classes, in order. A blank token is dropped rather than passed to
     *         {@code fromString}, which throws on one; a preset hand-edited into that state
     *         should lose one rule, not fail to open.
     */
    private static Set<PathClass> fromTokens(List<String> tokens) {
        Set<PathClass> classes = new LinkedHashSet<>();
        if (tokens == null) {
            return classes;
        }
        for (String token : tokens) {
            if (token == null) {
                classes.add(PathClass.NULL_CLASS);
            } else if (token.isBlank()) {
                logger.warn("Ignoring a blank class name in a visibility preset");
            } else {
                classes.add(PathClass.fromString(token));
            }
        }
        return classes;
    }
}
