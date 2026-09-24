package qupath.ext.classvisibility.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.core.ComponentMatchHighlight.Cue;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which class rows the checked components cover, and when the pulse over them fires.
 *
 * <p>The coverage half is pinned against {@link OverlayOptions#isHidden} rather than against
 * {@link ClassCensus#ruleMatches}: a marked row that the viewer does not act on would be the
 * panel asserting something the code does not do.</p>
 */
class ComponentMatchHighlightTest {

    private static final PathClass CD3 = PathClass.fromString("CD3");
    private static final PathClass CD8 = PathClass.fromString("CD8");
    private static final PathClass CD31 = PathClass.fromString("CD31");
    private static final PathClass CD3_CD8 = PathClass.fromString("CD3: CD8");
    private static final PathClass CD8_CD3 = PathClass.fromString("CD8: CD3");
    private static final PathClass CD3_CD4 = PathClass.fromString("CD3: CD4");
    private static final PathClass CD3_CD8_CD4_CD45 = PathClass.fromString("CD3: CD8: CD4: CD45");
    private static final PathClass CD31_CD8 = PathClass.fromString("CD31: CD8");

    private static final List<PathClass> LISTED = List.of(CD3, CD8, CD31, CD3_CD8, CD8_CD3,
            CD3_CD4, CD3_CD8_CD4_CD45, CD31_CD8, PathClass.NULL_CLASS);

    private ComponentMatchHighlight highlight;

    @BeforeEach
    void setUp() {
        highlight = new ComponentMatchHighlight();
    }

    @Test
    @DisplayName("Covered rows are exactly the rows the viewer acts on, for Any, All and exact")
    void coverageAgreesWithWhatQuPathHides() {
        for (VisibilityRuleModel.Combination combination : VisibilityRuleModel.Combination.values()) {
            for (boolean exact : new boolean[] {false, true}) {
                OverlayOptions options = new OverlayOptions();
                options.setUseExactSelectedClasses(exact);
                VisibilityRuleModel model = new VisibilityRuleModel(options::selectedClassesProperty,
                        showOnly -> options.setSelectedClassVisibilityMode(showOnly
                                ? ClassVisibilityMode.SHOW_SELECTED
                                : ClassVisibilityMode.HIDE_SELECTED));
                model.setCombination(combination);
                model.setComponentSelected("CD3", true);
                model.setComponentSelected("CD8", true);

                for (PathClass listed : LISTED) {
                    boolean hidden = options.isHidden(PathObjects.createDetectionObject(
                            ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()),
                            listed == PathClass.NULL_CLASS ? null : listed));
                    assertThat(ComponentMatchHighlight.covers(
                            model.componentDerivedEntries(), listed, exact))
                            .as("%s, %s, exact=%s", listed, combination, exact)
                            .isEqualTo(hidden);
                }
            }
        }
    }

    @Test
    @DisplayName("All covers only rows carrying every checked component")
    void allFollowsTheCombination() {
        Set<PathClass> all = Set.of(PathClass.fromCollection(List.of("CD3", "CD8")));

        assertThat(ComponentMatchHighlight.covers(all, CD3_CD8_CD4_CD45, false)).isTrue();
        assertThat(ComponentMatchHighlight.covers(all, CD8_CD3, false)).isTrue();
        assertThat(ComponentMatchHighlight.covers(all, CD3_CD4, false)).isFalse();
        assertThat(ComponentMatchHighlight.covers(all, CD3, false)).isFalse();
        assertThat(ComponentMatchHighlight.covers(Set.of(CD3, CD8), CD3_CD4, false))
                .as("the same two names under Any")
                .isTrue();
    }

    @Test
    @DisplayName("A component never covers Unclassified, and CD3 never covers CD31")
    void noFalseCoverage() {
        assertThat(ComponentMatchHighlight.covers(Set.of(CD3), PathClass.NULL_CLASS, false)).isFalse();
        assertThat(ComponentMatchHighlight.covers(Set.of(CD3), null, false)).isFalse();
        assertThat(ComponentMatchHighlight.covers(Set.of(CD3), CD31_CD8, false)).isFalse();
        assertThat(ComponentMatchHighlight.covers(Set.of(), CD3, false)).isFalse();
    }

    @Test
    @DisplayName("Every change to the component rule pulses -- no session latch")
    void everyChangePulses() {
        assertThat(highlight.onComponentEntries(Set.of(CD3), 4, true)).isEqualTo(Cue.PULSE);
        assertThat(highlight.onComponentEntries(Set.of(CD3, CD8), 6, true)).isEqualTo(Cue.PULSE);
        assertThat(highlight.onComponentEntries(Set.of(CD8), 3, true))
                .as("unchecking one of two is a change too")
                .isEqualTo(Cue.PULSE);
        assertThat(highlight.onComponentEntries(Set.of(), 0, true)).isEqualTo(Cue.STOP);
        assertThat(highlight.onComponentEntries(Set.of(CD8), 3, true))
                .as("and checking it again after clearing pulses again")
                .isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("A refresh with the rule unchanged -- new counts, a Find filter -- does nothing")
    void refreshingWithTheSameRuleIsQuiet() {
        assertThat(highlight.onComponentEntries(Set.of(CD3), 4, true)).isEqualTo(Cue.PULSE);
        assertThat(highlight.onComponentEntries(Set.of(CD3), 4, true)).isEqualTo(Cue.NONE);
        assertThat(highlight.onComponentEntries(Set.of(CD3), 2, true)).isEqualTo(Cue.NONE);
    }

    @Test
    @DisplayName("Any to All over two components is a different rule, so it pulses")
    void switchingTheCombinationPulses() {
        PathClass composite = PathClass.fromCollection(List.of("CD3", "CD8"));
        assertThat(highlight.onComponentEntries(Set.of(CD3, CD8), 6, true)).isEqualTo(Cue.PULSE);
        assertThat(highlight.onComponentEntries(Set.of(composite), 3, true)).isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("Nothing covered, nothing to pulse")
    void noCoveredRowsDoesNotPulse() {
        assertThat(highlight.onComponentEntries(Set.of(CD3), 0, true)).isEqualTo(Cue.STOP);
    }

    @Test
    @DisplayName("Switched off or off screen: no pulse, and nothing saved up for later")
    void theGateIsNotALatch() {
        assertThat(highlight.onComponentEntries(Set.of(CD3), 4, false)).isEqualTo(Cue.STOP);
        assertThat(highlight.onComponentEntries(Set.of(CD3), 4, true))
                .as("the change was already seen, so becoming visible does not replay it")
                .isEqualTo(Cue.NONE);
    }
}
