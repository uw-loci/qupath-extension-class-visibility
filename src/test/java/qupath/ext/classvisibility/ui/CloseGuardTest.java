package qupath.ext.classvisibility.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.objects.classes.PathClass;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The R2 restart guard: the single state this extension exists to prevent.
 *
 * <p>"Show only checked classes" is a persistent preference; the class set it acts on is not
 * (`OverlayOptions.createSharedInstance` binds the mode and the exact flag to `PathPrefs` and
 * leaves the set alone). Quitting in that pair therefore comes back at the next launch as an
 * empty viewer, with no panel open and no visible cause.</p>
 *
 * <p>The guard fires from two places -- the panel closing, and QuPath quitting -- and Phase 4
 * found the shutdown half had never run at all, because it was registered on an event QuPath's
 * main stage never fires (finding B1). {@code SourceDisciplineTest} pins the registration; this
 * pins what the guard does when it runs. No toolkit: the method is static and touches nothing
 * but {@code OverlayOptions}.</p>
 */
class CloseGuardTest {

    private OverlayOptions options;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
    }

    @Test
    @DisplayName("Show-only with nothing checked is put back to hide-checked")
    void guardFiresOnTheEverythingHiddenState() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        options.selectedClassesProperty().clear();

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isTrue();
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
    }

    @Test
    @DisplayName("A show-only view with rules in it is the user's, and is left alone")
    void guardLeavesADeliberateShowOnlyViewAlone() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        options.selectedClassesProperty().add(PathClass.fromString("CD3: CD8"));

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);
    }

    @Test
    @DisplayName("Hide-checked is never touched, with rules or without")
    void guardNeverTouchesHideChecked() {
        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();

        options.selectedClassesProperty().add(PathClass.fromString("Tumor"));
        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(options.selectedClassesProperty()).hasSize(1);
    }

    @Test
    @DisplayName("The guard is idempotent, so running it from both the panel and the quit is safe")
    void guardIsIdempotent() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isTrue();
        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();
    }
}
