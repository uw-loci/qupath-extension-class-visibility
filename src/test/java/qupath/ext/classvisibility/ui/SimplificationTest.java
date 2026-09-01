package qupath.ext.classvisibility.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.core.ClassCensus;
import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.lib.gui.viewer.OverlayOptions.ClassVisibilityMode;
import qupath.lib.objects.classes.PathClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 0.2.0 simplification pass, and the two things it had to stop the panel asserting.
 *
 * <p>An external tester ran 0.1.2 on her own multiplexed data and photographed the rules table
 * saying <b>"No rules are active. Every object is visible."</b> directly above a status strip
 * saying every object was hidden -- both true renderings of the same state, because the first was
 * a static {@code Label} and the second was not. She separately reported <b>"1 rule active -- only
 * objects matching it are shown. 1 rule has no class in this image."</b> over an image she knew
 * carried the cells that rule was for.</p>
 *
 * <p>Both are decisions, not renderings, so both are pinned here against the statics they now go
 * through. No JavaFX toolkit is started: these are static methods on a {@code Pane} subclass,
 * which is loading a class, not instantiating a {@code Control}.</p>
 */
class SimplificationTest {

    private static PathClass pc(String... tokens) {
        return PathClass.fromCollection(List.of(tokens));
    }

    private static ClassCensus census(Object... pairs) {
        Map<PathClass, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((PathClass) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return ClassCensus.of(map);
    }

    // ---------------------------------------------------------------------------------------
    // 1a. The placeholder that said the opposite of the mode
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the empty rules table names the mode, because the opening state hides everything")
    void theEmptyRulesPlaceholderFollowsTheMode() {
        // The state the panel OPENS in. "Every object is visible" here was the exact inverse.
        assertThat(ClassVisibilityPane.rulesPlaceholderText(ClassVisibilityMode.SHOW_SELECTED, 0))
                .isEqualTo(Strings.get("placeholder.rules.empty.allHidden"));
        assertThat(ClassVisibilityPane.rulesPlaceholderText(ClassVisibilityMode.HIDE_SELECTED, 0))
                .isEqualTo(Strings.get("placeholder.rules.empty"));
    }

    @Test
    @DisplayName("the placeholder and the everything-hidden warning come off one predicate")
    void thePlaceholderCannotDisagreeWithTheStatusStrip() {
        // The two contradicted each other on screen because they were computed in different
        // places from different things. One static now decides both.
        assertThat(ClassVisibilityPane.isEverythingHidden(ClassVisibilityMode.SHOW_SELECTED, 0)).isTrue();
        assertThat(ClassVisibilityPane.isEverythingHidden(ClassVisibilityMode.SHOW_SELECTED, 1)).isFalse();
        assertThat(ClassVisibilityPane.isEverythingHidden(ClassVisibilityMode.HIDE_SELECTED, 0)).isFalse();
        assertThat(ClassVisibilityPane.rulesPlaceholderText(ClassVisibilityMode.SHOW_SELECTED, 0))
                .isEqualTo(Strings.get("placeholder.rules.empty.allHidden"));
    }

    // ---------------------------------------------------------------------------------------
    // 1b. "1 rule has no class in this image", over an image that had the cells
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a rule that reaches objects only through derived classes is not an orphan")
    void aRuleMatchingThroughDerivedClassesIsNotReportedAsAbsent() {
        // The reported case. Every object carries a combinatorial class; no object carries the
        // bare component the rule is for. Set membership says "not in this image"; the objects
        // on screen say otherwise, and so does the Affects column three inches away.
        ClassCensus c = census(
                pc("CD8+", "GzB+"), 2400,
                pc("CD8+", "PD1+"), 800,
                pc("CD4+"), 1500);
        assertThat(c.classes()).doesNotContain(pc("CD8+"));
        assertThat(c.matchedObjectsForClass(pc("CD8+"), false))
                .as("the rule reaches 3,200 objects, so it is not an orphan")
                .isEqualTo(3200);
        assertThat(ClassVisibilityPane.ruleStatusText(false, VisibilityRuleModel.RuleSource.CLASS,
                false, c.matchedObjectsForClass(pc("CD8+"), false) > 0))
                .isEqualTo(Strings.get("rules.status.derived"));
    }

    @Test
    @DisplayName("an All composite is judged by what it reaches, not by whether it is a class")
    void anAllCompositeIsNotReportedAsAbsentWhileItIsHidingThings() {
        // The other half of the same report, and the one that made the two clauses contradict
        // each other: the rules table called this entry a composite while the status strip
        // counted it as a rule with no class in the image. A composite is NEVER a class in
        // anybody's image -- that is what the Components list is for.
        ClassCensus c = census(
                pc("CD8+", "GzB+"), 2400,
                pc("CD8+", "PD1+"), 800);
        PathClass composite = pc("CD8+", "GzB+");
        assertThat(c.matchedObjectsForClass(composite, false)).isEqualTo(2400);
        assertThat(ClassVisibilityPane.ruleStatusText(false,
                VisibilityRuleModel.RuleSource.COMPONENTS_ALL, true, true))
                .as("this one IS a class in the image, so the row above is the better answer")
                .isEqualTo(Strings.get("rules.status.listed"));
        assertThat(ClassVisibilityPane.ruleStatusText(false,
                VisibilityRuleModel.RuleSource.COMPONENTS_ALL, false, true))
                .isEqualTo(Strings.get("rules.status.derived"));
    }

    @Test
    @DisplayName("a rule that really reaches nothing still says so")
    void aRuleThatReachesNothingIsStillReported() {
        ClassCensus c = census(pc("CD8+", "GzB+"), 2400);
        assertThat(c.matchedObjectsForClass(pc("FoxP3+"), false)).isZero();
        assertThat(ClassVisibilityPane.ruleStatusText(false, VisibilityRuleModel.RuleSource.CLASS,
                false, false))
                .isEqualTo(Strings.get("rules.status.noMatch"));
    }

    @Test
    @DisplayName("Exact matches only outranks everything, because it is why nothing matches")
    void theExactMatchesOnlyStatusStillWins() {
        assertThat(ClassVisibilityPane.ruleStatusText(true,
                VisibilityRuleModel.RuleSource.COMPONENTS_ANY, false, false))
                .isEqualTo(Strings.get("rules.status.exactOnly"));
        assertThat(ClassVisibilityPane.ruleStatusText(true, VisibilityRuleModel.RuleSource.CLASS,
                true, true))
                .as("a class rule is not limited by it, so it reports normally")
                .isEqualTo(Strings.get("rules.status.listed"));
    }

    // ---------------------------------------------------------------------------------------
    // 5. One check control in the column header
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the header check control is tri-state over the rows the filter is showing")
    void theHeaderCheckControlReportsPartialSelection() {
        assertThat(ClassVisibilityPane.checkAllStateFor(0, 0))
                .isEqualTo(ClassVisibilityPane.CheckAllState.NOTHING_LISTED);
        assertThat(ClassVisibilityPane.checkAllStateFor(12, 0))
                .isEqualTo(ClassVisibilityPane.CheckAllState.NONE);
        assertThat(ClassVisibilityPane.checkAllStateFor(12, 5))
                .isEqualTo(ClassVisibilityPane.CheckAllState.SOME);
        assertThat(ClassVisibilityPane.checkAllStateFor(12, 12))
                .isEqualTo(ClassVisibilityPane.CheckAllState.ALL);
    }

    @Test
    @DisplayName("nothing listed is not the same as nothing checked")
    void nothingListedIsItsOwnState() {
        // A filter matching no class leaves the control with nothing to act on, which is a
        // disabled control, not an unchecked one. The two buttons this replaced made the same
        // distinction; losing it would make a click on an empty list look ignored.
        assertThat(ClassVisibilityPane.checkAllStateFor(0, 0))
                .isNotEqualTo(ClassVisibilityPane.CheckAllState.NONE);
    }

    // ---------------------------------------------------------------------------------------
    // 6. What stays on the always-visible strip
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("R2: the everything-hidden warning never moves inside the expander")
    void theWarningStaysOnTheAlwaysVisibleStrip() {
        String warning = Strings.get("status.s2");
        assertThat(ClassVisibilityPane.stripText(warning, true, null)).isEqualTo(warning);
    }

    @Test
    @DisplayName("the routine N-rules-active sentence leaves the strip")
    void theRoutineMessageMovesInside() {
        assertThat(ClassVisibilityPane.stripText(Strings.get("status.s1"), false, null)).isEmpty();
        assertThat(ClassVisibilityPane.stripText(Strings.get("status.s3.one"), false, null)).isEmpty();
        assertThat(ClassVisibilityPane.stripText(Strings.get("status.s4.one"), false, null)).isEmpty();
    }

    @Test
    @DisplayName("the coverage note stays visible, because it answers the click just made")
    void theCoverageNoteStaysOnTheStrip() {
        String note = Strings.format("status.s9", "positive", 21, 22, 30000, 31000);
        assertThat(ClassVisibilityPane.stripText(Strings.get("status.s4.one"), false, note))
                .isEqualTo(note);
        assertThat(ClassVisibilityPane.stripText(Strings.get("status.s2"), true, note))
                .contains(Strings.get("status.s2"))
                .contains(note);
    }
}
