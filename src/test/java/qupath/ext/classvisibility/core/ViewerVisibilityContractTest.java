package qupath.ext.classvisibility.core;

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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract between our rules and what QuPath actually paints.
 *
 * <p>Every other test in this repo drives {@link VisibilityRuleModel} against a fake
 * {@code SelectedClassSet} and asserts on the set's contents. That verifies we put the right
 * things in the set; it does NOT verify that QuPath then hides the right objects. Phase 3
 * recorded that gap as residual R1: "nobody has yet watched a rule change what the viewer
 * paints".
 *
 * <p>This test closes the headless half of R1. It wires the model to a <b>real</b>
 * {@link OverlayOptions} and asserts on {@link OverlayOptions#isHidden(PathObject)} -- the exact
 * predicate {@code PathObjectPainter} consults when deciding whether to draw an object. It uses
 * real interned {@link PathClass} instances and real {@link PathObject}s, so the interning
 * identity that {@code isSelectedClass} depends on is genuinely exercised rather than simulated.
 *
 * <p>What it does NOT close, and what still needs a GUI: that a change to the set actually
 * triggers a repaint (listener wiring), and anything about layout or appearance. Those remain in
 * {@code not_verified}.
 *
 * <p>{@link OverlayOptions} has a public constructor and its class-visibility state is plain
 * {@code javafx.base} collections and properties, so no JavaFX toolkit is started here. The
 * shared instance is deliberately NOT used -- it binds to persistent preferences.
 */
class ViewerVisibilityContractTest {

    // A realistic highly-multiplexed class set, including the CD3/CD31 collision that was
    // bug B4 in the ported script.
    private static final PathClass CD3 = PathClass.fromString("CD3");
    private static final PathClass CD31 = PathClass.fromString("CD31");
    private static final PathClass CD3_CD8 = PathClass.fromString("CD3: CD8");
    private static final PathClass CD8_CD3 = PathClass.fromString("CD8: CD3");
    private static final PathClass CD3_CD8_CD4_CD45 = PathClass.fromString("CD3: CD8: CD4: CD45");
    private static final PathClass CD31_CD8 = PathClass.fromString("CD31: CD8");
    private static final PathClass CD4 = PathClass.fromString("CD4");

    private OverlayOptions options;
    private VisibilityRuleModel model;

    @BeforeEach
    void setUp() {
        options = new OverlayOptions();
        model = new VisibilityRuleModel(() -> options.selectedClassesProperty());
    }

    private static PathObject objectOf(PathClass pathClass) {
        return PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()), pathClass);
    }

    /** True if QuPath would refuse to paint an object of this class. */
    private boolean hidden(PathClass pathClass) {
        return options.isHidden(objectOf(pathClass));
    }

    @Test
    @DisplayName("Baseline: with no rules, nothing is hidden")
    void noRulesHidesNothing() {
        assertThat(hidden(CD3)).isFalse();
        assertThat(hidden(CD3_CD8_CD4_CD45)).isFalse();
        assertThat(hidden(null)).isFalse();
    }

    @Test
    @DisplayName("A checked component hides every class containing it, in any position")
    void componentMatchesAnywhereInTheClass() {
        model.setComponentSelected("CD3", true);

        assertThat(hidden(CD3)).as("the bare class itself").isTrue();
        assertThat(hidden(CD3_CD8)).as("CD3 first").isTrue();
        assertThat(hidden(CD8_CD3)).as("CD3 last -- order independence").isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45)).as("CD3 among four").isTrue();
    }

    @Test
    @DisplayName("B4 regression: component CD3 must not match CD31")
    void componentDoesNotMatchBySubstring() {
        model.setComponentSelected("CD3", true);

        assertThat(hidden(CD31)).as("CD31 is not CD3").isFalse();
        assertThat(hidden(CD31_CD8)).as("CD31: CD8 contains no CD3 component").isFalse();
    }

    @Test
    @DisplayName("Any (OR): two components hide the union")
    void anyCombinationHidesTheUnion() {
        model.setCombination(VisibilityRuleModel.Combination.ANY);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);

        assertThat(hidden(CD3)).as("has CD3 only").isTrue();
        assertThat(hidden(CD4)).as("has CD4 only").isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45)).as("has both").isTrue();
        assertThat(hidden(CD31)).as("has neither").isFalse();
    }

    @Test
    @DisplayName("All (AND): two components hide only the intersection")
    void allCombinationHidesOnlyTheIntersection() {
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);

        assertThat(hidden(CD3_CD8_CD4_CD45)).as("has both CD3 and CD4").isTrue();
        assertThat(hidden(CD3)).as("has CD3 but not CD4").isFalse();
        assertThat(hidden(CD4)).as("has CD4 but not CD3").isFalse();
        assertThat(hidden(CD3_CD8)).as("has CD3 but not CD4").isFalse();
    }

    @Test
    @DisplayName("All: the composite is order-independent against the image's own class order")
    void allCombinationIgnoresComponentOrder() {
        // The Designer expected trouble here: fromCollection builds in iterator order, and
        // ["CD3","CD4"] interns differently from ["CD4","CD3"]. What must NOT depend on order is
        // the MATCH, because containsSelectedClass uses set containment.
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD4", true);
        model.setComponentSelected("CD3", true); // deliberately reverse-alphabetical

        assertThat(hidden(CD3_CD8_CD4_CD45))
                .as("checked CD4 then CD3; the class lists CD3 first")
                .isTrue();
    }

    @Test
    @DisplayName("Switching Any -> All leaves no stale rule behind")
    void switchingCombinationDoesNotStrandEntries() {
        model.setCombination(VisibilityRuleModel.Combination.ANY);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);
        assertThat(hidden(CD3)).isTrue();

        model.setCombination(VisibilityRuleModel.Combination.ALL);

        // Under ALL, a class with only CD3 must come back into view. If the ANY-era entries were
        // left in the set, this would still read hidden.
        assertThat(hidden(CD3)).as("CD3-only, now under AND").isFalse();
        assertThat(hidden(CD3_CD8_CD4_CD45)).as("has both").isTrue();
    }

    @Test
    @DisplayName("R1 interlock: Exact matches only makes component rules inert, not wrong")
    void exactMatchesOnlyDisablesComponentMatching() {
        model.setComponentSelected("CD3", true);
        assertThat(hidden(CD3_CD8)).isTrue();

        options.setUseExactSelectedClasses(true);

        // This is the persistent preference the panel must surface. With it on, the derived class
        // is no longer matched by the bare component -- the exact bare class still is.
        assertThat(hidden(CD3_CD8)).as("derived class, exact matching on").isFalse();
        assertThat(hidden(CD3)).as("the exact class is still matched").isTrue();
    }

    @Test
    @DisplayName("SHOW_SELECTED inverts the sense of every rule")
    void showSelectedModeInvertsMatching() {
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        model.setComponentSelected("CD3", true);

        assertThat(hidden(CD3_CD8)).as("matches the rule, so shown").isFalse();
        assertThat(hidden(CD31)).as("does not match, so hidden").isTrue();
    }

    @Test
    @DisplayName("R2: SHOW_SELECTED with an empty rule set hides absolutely everything")
    void showSelectedWithNoRulesHidesEverything() {
        // This is the restart footgun the close guard exists to prevent. Asserting it here means
        // the guard is protecting against a real, demonstrated state rather than a theory.
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
        model.clearAllRules();

        assertThat(options.selectedClassesProperty()).isEmpty();
        assertThat(hidden(CD3)).isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45)).isTrue();
        assertThat(hidden(null)).as("unclassified objects vanish too").isTrue();
    }

    @Test
    @DisplayName("Unclassified is hidden only by its own rule, never by a component")
    void unclassifiedIsExactOnly() {
        model.setComponentSelected("CD3", true);
        assertThat(hidden(null)).as("no component can reach the null class").isFalse();

        model.setClassSelected(PathClass.NULL_CLASS, true);
        assertThat(hidden(null)).as("its own rule does reach it").isTrue();
    }

    @Test
    @DisplayName("Solo, driven the way the UI drives it, isolates one class")
    void soloIsolatesOneClass() {
        // NOTE: solo is a TWO-PART operation split across two layers. The model sets the rule
        // contents; ClassVisibilityPane.soloClass (:878-879) then flips the mode. Calling
        // model.soloClass() alone leaves the default HIDE_SELECTED mode in force, which hides
        // exactly the class the caller asked to isolate -- the precise inverse. Reproduced here
        // so the split is documented rather than rediscovered. See Phase 5 finding L1.
        model.soloClass(CD3_CD8);
        options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);

        assertThat(hidden(CD3_CD8)).as("the soloed class").isFalse();
        assertThat(hidden(CD3)).as("bare CD3 lacks CD8").isTrue();
        assertThat(hidden(CD31)).isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45))
                .as("a superset of the soloed class also matches, so it stays visible")
                .isFalse();
    }

    @Test
    @DisplayName("L1 regression: the model half of solo, alone, inverts the intent")
    void soloThroughTheModelAloneInvertsTheIntent() {
        // Locks in the hazard above. If a future caller (scripting API, an accelerator wired
        // somewhere else) invokes the model without the mode flip, this is what they get.
        model.soloClass(CD3_CD8);

        assertThat(hidden(CD3_CD8))
                .as("without the Pane's mode flip, solo HIDES its target")
                .isTrue();
        assertThat(hidden(CD31)).as("and shows everything else").isFalse();
    }

    @Test
    @DisplayName("A class-column rule also hides supersets of that class, by default")
    void classRuleAlsoHidesSupersets() {
        // The class column is labelled for EXACT classifications, but with the default
        // useExactSelectedClasses=false, containsSelectedClass matches by set containment in
        // BOTH directions: selecting "CD3: CD8" hides "CD3: CD8: CD4: CD45" too, because
        // {CD3,CD8,CD4,CD45} containsAll {CD3,CD8}. That is QuPath's semantics, not a defect --
        // but it means the "exact" column is not exact unless the user turns on "Exact matches
        // only". See Phase 5 finding L2.
        model.setClassSelected(CD3_CD8, true);

        assertThat(hidden(CD3_CD8)).as("the class itself").isTrue();
        assertThat(hidden(CD3)).as("bare CD3 is not a superset").isFalse();
        assertThat(hidden(CD8_CD3)).as("same components, other order").isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45)).as("a superset IS matched").isTrue();
    }

    @Test
    @DisplayName("Exact matches only makes the class column behave as its label implies")
    void exactMatchesOnlyNarrowsTheClassColumn() {
        model.setClassSelected(CD3_CD8, true);
        options.setUseExactSelectedClasses(true);

        assertThat(hidden(CD3_CD8)).as("the class itself").isTrue();
        assertThat(hidden(CD3_CD8_CD4_CD45)).as("superset no longer matched").isFalse();
        assertThat(hidden(CD8_CD3)).as("reordered class is a different instance").isFalse();
    }

    @Test
    @DisplayName("Clearing rules restores every object to view")
    void clearingRulesRestoresVisibility() {
        model.setComponentSelected("CD3", true);
        model.setClassSelected(CD31, true);
        assertThat(hidden(CD3_CD8)).isTrue();
        assertThat(hidden(CD31)).isTrue();

        model.clearAllRules();

        assertThat(options.selectedClassesProperty()).isEmpty();
        for (PathClass pc : Set.of(CD3, CD31, CD3_CD8, CD8_CD3, CD3_CD8_CD4_CD45, CD31_CD8, CD4)) {
            assertThat(hidden(pc)).as("%s after clearing", pc).isFalse();
        }
    }
}
