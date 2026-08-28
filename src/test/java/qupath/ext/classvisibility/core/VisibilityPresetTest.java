package qupath.ext.classvisibility.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a named preset stores, and what comes back.
 *
 * <p>The round trip goes through <b>real Gson</b>, not just through the object: the preset's
 * whole reason to exist is that it is written to a file in the project and read back another day,
 * and a field that serializes badly is invisible to a test that only calls capture and restore.
 * QuPath's {@code ResourceManager} serializes with Gson, so that is what is exercised here.</p>
 *
 * <p>No toolkit and no project: {@link OverlayOptions} and {@link VisibilityRuleModel} are both
 * plain objects, and Gson does not need either.</p>
 */
class VisibilityPresetTest {

    private static final PathClass CD3_CD8 = PathClass.fromString("CD3: CD8");
    private static final PathClass CD4 = PathClass.fromString("CD4");

    private OverlayOptions options;
    private VisibilityRuleModel model;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
        model = newModel(options);
    }

    private static VisibilityRuleModel newModel(OverlayOptions target) {
        return new VisibilityRuleModel(target::selectedClassesProperty,
                showSelectedOnly -> target.setSelectedClassVisibilityMode(showSelectedOnly
                        ? ClassVisibilityMode.SHOW_SELECTED
                        : ClassVisibilityMode.HIDE_SELECTED));
    }

    /** Write and read the preset exactly as the project's resource manager would. */
    private static VisibilityPreset throughJson(VisibilityPreset preset) {
        // GsonTools, not a bare Gson: this is the instance ResourceManager writes with, so a
        // field that only survives a plain round trip would still be a bug in the product.
        Gson gson = GsonTools.getInstance();
        return gson.fromJson(gson.toJson(preset), VisibilityPreset.class);
    }

    private static boolean hidden(OverlayOptions options, PathClass pathClass) {
        PathObject object = PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()), pathClass);
        return options.isHidden(object);
    }

    @Test
    @DisplayName("A preset restores the view a different session was left in")
    void aPresetRestoresTheViewIntoAFreshSession() {
        model.setClassSelected(CD3_CD8, true);
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        options.setDetectionDisplayMode(DetectionDisplayMode.CENTROIDS);
        options.setShowAnnotations(false);
        options.setOpacity(0.5f);

        VisibilityPreset preset = throughJson(
                VisibilityPreset.capture("T cells", options, model));

        // A different session: new options, new model, nothing carried over in memory.
        OverlayOptions later = new OverlayOptions();
        VisibilityRuleModel laterModel = newModel(later);
        preset.restore(later, laterModel);

        assertThat(preset.getName()).isEqualTo("T cells");
        assertThat(later.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);
        assertThat(later.getDetectionDisplayMode()).isEqualTo(DetectionDisplayMode.CENTROIDS);
        assertThat(later.getShowAnnotations()).isFalse();
        assertThat(later.getOpacity()).isEqualTo(0.5f);
        assertThat(hidden(later, CD3_CD8)).isFalse();
        assertThat(hidden(later, CD4)).isTrue();
    }

    @Test
    @DisplayName("The panel's own checks come back, not just the class set")
    void thePanelStateComesBackTooSoTheRulesCanStillBeEdited() {
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        model.setCombination(VisibilityRuleModel.Combination.ALL);

        VisibilityPreset preset = throughJson(
                VisibilityPreset.capture("T cells", options, model));

        OverlayOptions later = new OverlayOptions();
        VisibilityRuleModel laterModel = newModel(later);
        preset.restore(later, laterModel);

        // Without this a preset called "T cells" would come back with an empty Components list,
        // and the user could see the effect but not the reason for it.
        assertThat(laterModel.getSelectedComponents()).containsExactlyInAnyOrder("CD3", "CD8");
        assertThat(laterModel.getCombination()).isEqualTo(VisibilityRuleModel.Combination.ALL);
        assertThat(laterModel.isComponentSelected("CD3")).isTrue();
    }

    @Test
    @DisplayName("The stored class set is authoritative, overwriting whatever was in force")
    void theStoredClassSetIsAuthoritativeOnRestore() {
        model.setClassSelected(CD3_CD8, true);
        VisibilityPreset preset = throughJson(VisibilityPreset.capture("just CD3: CD8", options, model));

        OverlayOptions later = new OverlayOptions();
        VisibilityRuleModel laterModel = newModel(later);
        laterModel.setClassSelected(CD4, true);
        laterModel.setComponentSelected("PD1", true);

        preset.restore(later, laterModel);

        assertThat(later.selectedClassesProperty()).containsExactly(CD3_CD8);
        assertThat(laterModel.getSelectedComponents()).isEmpty();
    }

    @Test
    @DisplayName("Unclassified survives the round trip and does not become a class named Unclassified")
    void unclassifiedRoundTripsAsTheNullClass() {
        model.setClassSelected(PathClass.NULL_CLASS, true);

        VisibilityPreset preset = throughJson(VisibilityPreset.capture("no class", options, model));

        OverlayOptions later = new OverlayOptions();
        preset.restore(later, newModel(later));

        assertThat(later.selectedClassesProperty()).containsExactly(PathClass.NULL_CLASS);
        // The trap this avoids: NULL_CLASS.toString() is a display name, and round-tripping it
        // through fromString would invent an ordinary class with that name.
        assertThat(later.selectedClassesProperty())
                .doesNotContain(PathClass.fromString(PathClass.NULL_CLASS.toString()));
    }

    @Test
    @DisplayName("Classes are matched by name, so a preset works in another project")
    void classesAreRebuiltFromTheirNames() {
        model.setClassSelected(PathClass.fromString("CD3: CD8"), true);
        VisibilityPreset preset = throughJson(VisibilityPreset.capture("portable", options, model));

        OverlayOptions later = new OverlayOptions();
        preset.restore(later, newModel(later));

        // Interned, so a class built from the same string in any project IS the same instance,
        // which is what makes a preset portable at all.
        assertThat(later.selectedClassesProperty()).containsExactly(PathClass.fromString("CD3: CD8"));
    }

    @Test
    @DisplayName("A preset written before a field existed still opens")
    void aPresetMissingFieldsStillOpens() {
        // Every field absent except a name: what a preset from an older version looks like once
        // this type has grown. It must apply defaults rather than throw.
        VisibilityPreset preset = GsonTools.getInstance()
                .fromJson("{\"name\":\"old\"}", VisibilityPreset.class);

        OverlayOptions later = new OverlayOptions();
        preset.restore(later, newModel(later));

        assertThat(preset.getName()).isEqualTo("old");
        assertThat(later.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(later.selectedClassesProperty()).isEmpty();
    }

    @Test
    @DisplayName("An empty preset hides nothing, rather than hiding everything")
    void anEmptyPresetIsSafe() {
        VisibilityPreset preset = throughJson(VisibilityPreset.capture("nothing", options, model));

        OverlayOptions later = new OverlayOptions();
        later.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        preset.restore(later, newModel(later));

        // Captured from a default state, so it restores HIDE_SELECTED with no rules -- it must
        // not leave the R2 everything-hidden pair behind.
        assertThat(hidden(later, CD3_CD8)).isFalse();
        assertThat(hidden(later, null)).isFalse();
    }
}
