package qupath.ext.classvisibility.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.ext.classvisibility.core.VisibilitySnapshot;
import qupath.ext.classvisibility.ui.ClassVisibilityPane.RestoreOutcome;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The panel is a session: closing it puts QuPath back where the user left it.
 *
 * <p>Opening the panel hides every object, which means it also discards whatever class rules the
 * user already had. Through 0.1.0 that was a one-way door -- the close guard put the <i>mode</i>
 * back and nothing else, so a view the user had built before opening the panel was recoverable
 * only through a menu item they had to know existed. From 0.1.1 the snapshot taken at open is
 * replayed in full on close (user, 2026-08-28).</p>
 *
 * <p>What is pinned here is the replay itself -- every field, the failure behaviour, and the
 * interaction with the close guard. Which code paths reach it is a wiring question that no unit
 * test can express without a toolkit and a live QuPath, and is pinned in
 * {@code SourceDisciplineTest} instead.</p>
 *
 * <p>No JavaFX toolkit: {@link OverlayOptions} has a public constructor and everything touched
 * here is plain {@code javafx.base} state. The shared instance is deliberately not used -- it
 * binds to persistent preferences.</p>
 */
class SessionRestoreTest {

    private static final PathClass TUMOR = PathClass.fromString("Tumor");
    private static final PathClass CD3_CD8 = PathClass.fromString("CD3: CD8");

    private OverlayOptions options;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
    }

    private boolean hidden(OverlayOptions target, PathClass pathClass) {
        PathObject object = PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()), pathClass);
        return target.isHidden(object);
    }

    /** The state a user might plausibly be in when they reach for the toolbar button. */
    private void givenAViewTheUserBuilt() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.HIDE_SELECTED);
        options.selectedClassesProperty().add(TUMOR);
        options.setUseExactSelectedClasses(true);
        options.setOpacity(0.42f);
        options.setDetectionDisplayMode(DetectionDisplayMode.CENTROIDS);
        options.setShowAnnotations(false);
        options.setFillDetections(true);
        options.setShowTMAGrid(false);
        options.setShowConnections(true);
        options.setShowTMACoreLabels(true);
        options.setShowGrid(true);
        options.setShowPixelClassification(false);
        options.setShowDetections(true);
        options.setFillAnnotations(true);
    }

    @Test
    @DisplayName("Every field the snapshot captures comes back on close")
    void closeRestoresEveryCapturedField() {
        givenAViewTheUserBuilt();
        Predicate<PathObject> predicate = object -> true;
        options.setShowObjectPredicate(predicate);

        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        // Opening the panel: rules cleared, mode flipped to show-only. Then a session's worth of
        // the user's own changes on top.
        options.selectedClassesProperty().clear();
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        options.selectedClassesProperty().add(CD3_CD8);
        options.setUseExactSelectedClasses(false);
        options.setOpacity(1.0f);
        options.setDetectionDisplayMode(DetectionDisplayMode.BOUNDARIES_ONLY);
        options.setShowObjectPredicate(null);
        options.setShowAnnotations(true);
        options.setFillDetections(false);
        options.setShowConnections(false);
        options.setShowTMACoreLabels(false);
        options.setShowGrid(false);
        options.setShowPixelClassification(true);
        options.setShowDetections(false);
        options.setFillAnnotations(false);

        assertThat(ClassVisibilityPane.restoreQuietly(snapshot, options))
                .isEqualTo(RestoreOutcome.RESTORED);

        assertThat(options.selectedClassesProperty()).containsExactly(TUMOR);
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
        assertThat(options.getUseExactSelectedClasses()).isTrue();
        assertThat(options.getShowObjectPredicate()).isSameAs(predicate);
        assertThat(options.getOpacity()).isEqualTo(0.42f);
        assertThat(options.getDetectionDisplayMode()).isEqualTo(DetectionDisplayMode.CENTROIDS);
        assertThat(options.getShowAnnotations()).isFalse();
        assertThat(options.getFillDetections()).isTrue();
        assertThat(options.getShowTMAGrid()).isFalse();
        assertThat(options.getShowConnections()).isTrue();
        assertThat(options.getShowTMACoreLabels()).isTrue();
        assertThat(options.getShowGrid()).isTrue();
        assertThat(options.getShowPixelClassification()).isFalse();
        assertThat(options.getShowDetections()).isTrue();
        assertThat(options.getFillAnnotations()).isTrue();
    }

    @Test
    @DisplayName("The user sees their own view again, not ours")
    void theRestoredViewHidesWhatTheUserWasHiding() {
        options.selectedClassesProperty().add(TUMOR);
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        ClassVisibilityPane.applyOpeningState(options,
                new VisibilityRuleModel(() -> options.selectedClassesProperty(),
                        showSelectedOnly -> options.setSelectedClassVisibilityMode(showSelectedOnly
                                ? ClassVisibilityMode.SHOW_SELECTED
                                : ClassVisibilityMode.HIDE_SELECTED)));
        // The opening state, as the user meets it: nothing on screen at all.
        assertThat(hidden(options, TUMOR)).isTrue();
        assertThat(hidden(options, CD3_CD8)).isTrue();

        ClassVisibilityPane.restoreQuietly(snapshot, options);

        // Their rule is back, and only their rule.
        assertThat(hidden(options, TUMOR)).isTrue();
        assertThat(hidden(options, CD3_CD8)).isFalse();
        assertThat(hidden(options, null)).isFalse();
    }

    @Test
    @DisplayName("A null showObjectPredicate round-trips, because that is the usual case")
    void anAbsentObjectPredicateRoundTrips() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);
        options.setShowObjectPredicate(object -> false);

        assertThat(ClassVisibilityPane.restoreQuietly(snapshot, options))
                .isEqualTo(RestoreOutcome.RESTORED);
        assertThat(options.getShowObjectPredicate()).isNull();
    }

    @Test
    @DisplayName("Restoring an Unclassified rule works, since QuPath allows null in the set")
    void theUnclassifiedSentinelSurvivesTheRoundTrip() {
        options.selectedClassesProperty().add(null);
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        options.selectedClassesProperty().clear();
        ClassVisibilityPane.restoreQuietly(snapshot, options);

        assertThat(options.selectedClassesProperty()).containsExactly((PathClass) null);
    }

    @Test
    @DisplayName("A failing restore is reported, never thrown -- it runs during a quit")
    void aFailedRestoreCannotPropagateOutOfTheClose() {
        OverlayOptions failing = new OverlayOptions() {
            @Override
            public void setOpacity(float opacity) {
                throw new IllegalStateException("simulated failure mid-restore");
            }
        };
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        RestoreOutcome[] outcome = new RestoreOutcome[1];
        assertThatCode(() -> outcome[0] = ClassVisibilityPane.restoreQuietly(snapshot, failing))
                .doesNotThrowAnyException();
        assertThat(outcome[0]).isEqualTo(RestoreOutcome.FAILED);
    }

    @Test
    @DisplayName("No snapshot is reported as such, not silently treated as nothing to do")
    void noSnapshotIsItsOwnOutcome() {
        assertThat(ClassVisibilityPane.restoreQuietly(null, options))
                .isEqualTo(RestoreOutcome.NOTHING_TO_RESTORE);
    }

    @Test
    @DisplayName("matchesRules answers the question the C1 notification asks")
    void matchesRulesComparesTheRuleStateOnly() {
        givenAViewTheUserBuilt();
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);
        assertThat(snapshot.matchesRules(options)).isTrue();

        options.selectedClassesProperty().add(CD3_CD8);
        assertThat(snapshot.matchesRules(options)).isFalse();
        options.selectedClassesProperty().remove(CD3_CD8);

        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        assertThat(snapshot.matchesRules(options)).isFalse();
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.HIDE_SELECTED);

        options.setUseExactSelectedClasses(false);
        assertThat(snapshot.matchesRules(options)).isFalse();
        options.setUseExactSelectedClasses(true);

        // Not a class rule, so not part of the question: the opacity is restored either way.
        options.setOpacity(0.05f);
        assertThat(snapshot.matchesRules(options)).isTrue();
    }

    @Test
    @DisplayName("The close guard still fires when the state being restored is the bad pair")
    void theGuardStillFiresOnASnapshotThatIsItselfTheEverythingHiddenState() {
        // A user who set "show only checked classes" from QuPath's own class list, checked
        // nothing, and then opened this panel. The restore puts that back faithfully -- and it is
        // the one state that must not survive a restart, so the guard has to run after it.
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        options.selectedClassesProperty().add(TUMOR);
        assertThat(ClassVisibilityPane.restoreQuietly(snapshot, options))
                .isEqualTo(RestoreOutcome.RESTORED);
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.SHOW_SELECTED);

        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isTrue();
        assertThat(options.getSelectedClassVisibilityMode()).isEqualTo(ClassVisibilityMode.HIDE_SELECTED);
    }

    @Test
    @DisplayName("An ordinary restore leaves the guard nothing to do")
    void theGuardIsSilentAfterAnOrdinaryRestore() {
        givenAViewTheUserBuilt();
        VisibilitySnapshot snapshot = VisibilitySnapshot.capture(options);

        options.selectedClassesProperty().clear();
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);

        ClassVisibilityPane.restoreQuietly(snapshot, options);
        assertThat(ClassVisibilityPane.applyCloseGuard(options)).isFalse();
    }
}
