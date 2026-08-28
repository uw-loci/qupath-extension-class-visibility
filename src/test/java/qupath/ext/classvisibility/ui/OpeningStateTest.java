package qupath.ext.classvisibility.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening the panel hides everything.
 *
 * <p>This is the workflow of the script this extension ports, and the one the user asked for
 * back: the panel opens onto an empty viewer and you check your way to the populations you want.
 * The claim being tested is not "the mode was set" but the thing the user sees -- that QuPath
 * then refuses to paint anything -- so, like {@code ViewerVisibilityContractTest}, this asserts
 * on {@link OverlayOptions#isHidden(PathObject)}, the predicate {@code PathObjectPainter}
 * actually consults.</p>
 *
 * <p>It also pins the other half of the contract, which is what keeps the first half safe: the
 * close guard has to undo this. The opening state is now the <b>normal</b> state of a session
 * rather than an edge case, so a guard that failed to cover it would leave every user who opens
 * the panel and closes it again with an invisible slide at the next launch.</p>
 *
 * <p>No JavaFX toolkit: {@link OverlayOptions} has a public constructor and its class-visibility
 * state is plain {@code javafx.base} collections. The shared instance is deliberately not used
 * -- it binds to persistent preferences.</p>
 */
class OpeningStateTest {

    private static final PathClass CD3_CD8 = PathClass.fromString("CD3: CD8");
    private static final PathClass CD4 = PathClass.fromString("CD4");

    private OverlayOptions options;
    private VisibilityRuleModel model;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
        model = new VisibilityRuleModel(() -> options.selectedClassesProperty(),
                showSelectedOnly -> options.setSelectedClassVisibilityMode(showSelectedOnly
                        ? ClassVisibilityMode.SHOW_SELECTED
                        : ClassVisibilityMode.HIDE_SELECTED));
    }

    private boolean hidden(PathClass pathClass) {
        PathObject object = PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()), pathClass);
        return options.isHidden(object);
    }

    @Test
    @DisplayName("Opening the panel hides every object, whatever its class")
    void openingHidesEverything() {
        assertThat(hidden(CD3_CD8)).isFalse();

        ClassVisibilityPane.applyOpeningState(options, model);

        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);
        assertThat(options.selectedClassesProperty()).isEmpty();
        assertThat(hidden(CD3_CD8)).isTrue();
        assertThat(hidden(CD4)).isTrue();
        assertThat(hidden(null)).isTrue();
    }

    @Test
    @DisplayName("Rules already in force when the panel opens are cleared, not inherited")
    void openingClearsWhateverWasAlreadySet() {
        model.setClassSelected(CD3_CD8, true);
        model.setComponentSelected("CD4", true);
        assertThat(options.selectedClassesProperty()).isNotEmpty();

        ClassVisibilityPane.applyOpeningState(options, model);

        assertThat(options.selectedClassesProperty()).isEmpty();
        assertThat(model.activeRuleCount()).isZero();
        assertThat(model.getSelectedComponents()).isEmpty();
        assertThat(model.isClassSelected(CD3_CD8)).isFalse();
        assertThat(hidden(CD3_CD8)).isTrue();
    }

    @Test
    @DisplayName("Checking one class after opening shows that class and nothing else")
    void checkingAClassAfterOpeningShowsOnlyIt() {
        ClassVisibilityPane.applyOpeningState(options, model);

        model.setClassSelected(CD3_CD8, true);

        assertThat(hidden(CD3_CD8)).isFalse();
        assertThat(hidden(CD4)).isTrue();
        assertThat(hidden(null)).isTrue();
    }

    @Test
    @DisplayName("The close guard undoes the opening state, so a panel opened and closed leaves nothing")
    void theCloseGuardUndoesTheOpeningState() {
        ClassVisibilityPane.applyOpeningState(options, model);

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isTrue();

        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(hidden(CD3_CD8)).isFalse();
        assertThat(hidden(null)).isFalse();
    }

    @Test
    @DisplayName("A class checked before closing survives the close; only the empty case is guarded")
    void theCloseGuardLeavesAUsersRulesAlone() {
        ClassVisibilityPane.applyOpeningState(options, model);
        model.setClassSelected(CD3_CD8, true);

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();

        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);
        assertThat(hidden(CD3_CD8)).isFalse();
        assertThat(hidden(CD4)).isTrue();
    }
}
