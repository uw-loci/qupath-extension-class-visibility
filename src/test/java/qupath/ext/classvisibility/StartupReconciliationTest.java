package qupath.ext.classvisibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The crash path, which is the one route the close guard cannot cover.
 *
 * <p>The panel hides every object as it opens and the close guard puts the mode back as it
 * closes. Neither runs if QuPath is killed, force-quit or crashes while the panel is open -- and
 * QuPath persists {@code selectedClassVisibilityMode} while not persisting the class set
 * ({@code OverlayOptions.java:131-141}), so the next launch reads back "show only checked
 * classes" with nothing checked: every object in every image invisible, no panel open, nothing
 * on screen saying why.</p>
 *
 * <p>The rule the user gave is that nothing this panel does may persist unless they pressed the
 * button. A mode left behind by a session that never closed is exactly that, so it is reconciled
 * once at install time, before any UI exists.</p>
 */
class StartupReconciliationTest {

    private OverlayOptions options;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
    }

    private boolean hidden(PathClass pathClass) {
        PathObject object = PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()), pathClass);
        return options.isHidden(object);
    }

    @Test
    @DisplayName("A launch into show-only with nothing checked is reset, and objects are visible")
    void aCrashWhileThePanelWasOpenIsReconciledAtStartup() {
        // Exactly what the preferences give back after a force quit with the panel open: the
        // mode persisted, the set did not.
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);

        assertThat(ClassVisibilityExtension.reconcileStartupVisibility(options)).isTrue();

        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(hidden(PathClass.fromString("CD3: CD8"))).isFalse();
        assertThat(hidden(null)).isFalse();
    }

    @Test
    @DisplayName("A launch with rules already in the set is left alone")
    void aStateThatHidesNothingUniversalIsLeftAlone() {
        // Not reachable from QuPath's own preferences today, since the set is not persisted --
        // but the reconciliation must never be the thing that discards a rule someone else put
        // there, whether that is a startup script or a future QuPath that does persist the set.
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        options.selectedClassesProperty().add(PathClass.fromString("Tumor"));

        assertThat(ClassVisibilityExtension.reconcileStartupVisibility(options)).isFalse();

        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);
        assertThat(options.selectedClassesProperty()).hasSize(1);
    }

    @Test
    @DisplayName("An ordinary launch is untouched and reports no change")
    void anOrdinaryLaunchIsUntouched() {
        assertThat(ClassVisibilityExtension.reconcileStartupVisibility(options)).isFalse();
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(options.selectedClassesProperty()).isEmpty();
    }

    @Test
    @DisplayName("Running it twice changes nothing the second time")
    void reconciliationIsIdempotent() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);

        assertThat(ClassVisibilityExtension.reconcileStartupVisibility(options)).isTrue();
        assertThat(ClassVisibilityExtension.reconcileStartupVisibility(options)).isFalse();
    }
}
