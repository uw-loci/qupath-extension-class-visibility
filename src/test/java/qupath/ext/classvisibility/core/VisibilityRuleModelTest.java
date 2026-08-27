package qupath.ext.classvisibility.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.classes.PathClass;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule state machine, exercised against a plain {@link LinkedHashSet} rather than a live
 * {@code OverlayOptions} -- which is the point of the one-method {@code SelectedClassSet}
 * interface: these tests need no JavaFX toolkit and no QuPath instance.
 */
class VisibilityRuleModelTest {

    private Set<PathClass> selected;
    private VisibilityRuleModel model;

    /** What the model last asked the visibility mode to become, or null if it never asked. */
    private Boolean showSelectedOnly;

    private static PathClass pc(String... tokens) {
        return PathClass.fromCollection(List.of(tokens));
    }

    @BeforeEach
    void setUp() {
        selected = new LinkedHashSet<>();
        showSelectedOnly = null;
        model = new VisibilityRuleModel(() -> selected, value -> showSelectedOnly = value);
    }

    // --- Exact class rules --------------------------------------------------------------------

    @Test
    void checkingAClassWritesBackTheHarvestedInstance() {
        PathClass harvested = pc("CD3", "CD8");
        model.setClassSelected(harvested, true);
        assertThat(selected).containsExactly(harvested);
        // Identity, not merely equality: QuPath's isSelectedClass is a set lookup on interned
        // instances, so a reconstructed PathClass would silently fail to match.
        assertThat(selected.iterator().next()).isSameAs(harvested);
    }

    @Test
    void uncheckingAClassRemovesOnlyThatEntry() {
        model.setClassSelected(pc("CD3"), true);
        model.setClassSelected(pc("CD8"), true);
        model.setClassSelected(pc("CD3"), false);
        assertThat(selected).containsExactly(pc("CD8"));
    }

    @Test
    void nullAndNullClassBothMeanUnclassified() {
        model.setClassSelected(null, true);
        assertThat(selected).containsExactly(PathClass.NULL_CLASS);
        assertThat(model.isClassSelected(null)).isTrue();
        assertThat(model.isClassSelected(PathClass.NULL_CLASS)).isTrue();
        model.setClassSelected(PathClass.NULL_CLASS, false);
        assertThat(selected).isEmpty();
    }

    // --- Any / All ----------------------------------------------------------------------------

    @Test
    void anyGivesOneEntryPerCheckedComponent() {
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        assertThat(selected).containsExactlyInAnyOrder(pc("CD3"), pc("CD8"));
    }

    @Test
    void allGivesOneCompositeEntry() {
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        assertThat(selected).containsExactly(pc("CD3", "CD8"));
        assertThat(selected.iterator().next().isDerivedClass()).isTrue();
    }

    @Test
    void compositeIsBuiltFromASortedTokenListSoCheckOrderDoesNotMatter() {
        // fromCollection builds in iterator order, so ["CD3","CD8"] and ["CD8","CD3"] intern to
        // two different instances. Checking in reverse order must still produce -- and later
        // remove -- the same one.
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD8", true);
        model.setComponentSelected("CD3", true);
        assertThat(selected).containsExactly(pc("CD3", "CD8"));

        model.setComponentSelected("CD3", false);
        assertThat(selected).containsExactly(pc("CD8"));
    }

    @Test
    void switchingBetweenAnyAndAllSwapsTheEntriesAndLeavesNoStaleComposite() {
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        assertThat(selected).containsExactlyInAnyOrder(pc("CD3"), pc("CD8"));

        model.setCombination(VisibilityRuleModel.Combination.ALL);
        assertThat(selected).containsExactly(pc("CD3", "CD8"));

        model.setCombination(VisibilityRuleModel.Combination.ANY);
        assertThat(selected).containsExactlyInAnyOrder(pc("CD3"), pc("CD8"));
        // A stale composite left behind would silently keep hiding objects with no row anywhere.
        assertThat(selected).doesNotContain(pc("CD3", "CD8"));
    }

    @Test
    void anyAndAllAreIdenticalBelowTwoCheckedComponents() {
        model.setComponentSelected("CD8", true);
        Set<PathClass> underAny = new LinkedHashSet<>(selected);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        assertThat(selected).isEqualTo(underAny).containsExactly(pc("CD8"));
    }

    // --- Minimal delta ------------------------------------------------------------------------

    @Test
    void aForeignEntrySurvivesEveryOperationExceptAnExplicitRemoval() {
        // QuPath's own Classes pane writes this same set. A clear-and-rebuild would destroy its
        // entries; only a minimal delta does not.
        PathClass foreign = pc("WrittenByTheClassesPane");
        selected.add(foreign);

        model.setClassSelected(pc("CD3"), true);
        assertThat(selected).contains(foreign, pc("CD3"));

        model.setComponentSelected("CD8", true);
        model.setComponentSelected("CD4", true);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setCombination(VisibilityRuleModel.Combination.ANY);
        model.setClassSelected(pc("CD3"), false);
        assertThat(selected).contains(foreign);

        model.setClassSelected(foreign, false);
        assertThat(selected).doesNotContain(foreign);
    }

    @Test
    void uncheckingARowThatWasSetElsewhereActuallyRemovesIt() {
        PathClass foreign = pc("Tumor");
        selected.add(foreign);
        assertThat(model.isClassSelected(foreign)).isTrue();
        model.setClassSelected(foreign, false);
        assertThat(selected).isEmpty();
    }

    @Test
    void clearAllRulesRemovesForeignEntriesToo() {
        selected.add(pc("Tumor"));
        model.setClassSelected(pc("CD3"), true);
        model.clearAllRules();
        assertThat(selected).isEmpty();
    }

    @Test
    void bulkCheckAndUncheckAreScopedToTheSuppliedClasses() {
        model.setClassSelected(pc("Keep"), true);
        model.checkClasses(List.of(pc("A"), pc("B"), pc("C")));
        assertThat(selected).containsExactlyInAnyOrder(pc("Keep"), pc("A"), pc("B"), pc("C"));
        model.uncheckClasses(List.of(pc("A"), pc("B")));
        assertThat(selected).containsExactlyInAnyOrder(pc("Keep"), pc("C"));
    }

    // --- Solo ---------------------------------------------------------------------------------

    @Test
    void soloLeavesExactlyOneEntry() {
        selected.add(pc("Foreign"));
        model.setClassSelected(pc("CD3"), true);
        model.setComponentSelected("CD8", true);
        model.soloClass(pc("Tumor"));
        assertThat(selected).containsExactly(pc("Tumor"));
    }

    @Test
    void soloingAComponentIgnoresTheCombinationSetting() {
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.soloComponent("CD8");
        assertThat(selected).containsExactly(pc("CD8"));
    }

    /**
     * L1: solo is one operation. It used to be two, split across two layers -- the model set the
     * rule contents and the Pane flipped the mode -- so the model half on its own hid exactly the
     * class it had been asked to isolate.
     */
    @Test
    void soloSwitchesTheModeItself() {
        model.soloClass(pc("Tumor"));
        assertThat(showSelectedOnly).as("solo must ask for show-only, not leave hide-checked in force")
                .isTrue();
    }

    @Test
    void soloingAComponentSwitchesTheModeItself() {
        model.soloComponent("CD8");
        assertThat(showSelectedOnly).isTrue();
    }

    @Test
    void ordinaryRuleChangesNeverTouchTheMode() {
        model.setClassSelected(pc("CD3"), true);
        model.setComponentSelected("CD8", true);
        model.clearAllRules();
        assertThat(showSelectedOnly).as("only solo implies a mode").isNull();
    }

    // --- Rule provenance ----------------------------------------------------------------------

    @Test
    void ruleSourceDistinguishesOurEntriesFromEntriesWrittenElsewhere() {
        PathClass foreign = pc("Foreign");
        selected.add(foreign);
        model.setClassSelected(pc("CD3"), true);
        model.setComponentSelected("CD8", true);

        assertThat(model.sourceOf(pc("CD3"))).isEqualTo(VisibilityRuleModel.RuleSource.CLASS);
        assertThat(model.sourceOf(pc("CD8"))).isEqualTo(VisibilityRuleModel.RuleSource.COMPONENTS_ANY);
        assertThat(model.sourceOf(foreign)).isEqualTo(VisibilityRuleModel.RuleSource.ELSEWHERE);

        model.setComponentSelected("CD4", true);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        assertThat(model.sourceOf(pc("CD4", "CD8")))
                .isEqualTo(VisibilityRuleModel.RuleSource.COMPONENTS_ALL);
        assertThat(model.componentDerivedEntries()).containsExactly(pc("CD4", "CD8"));
    }

    @Test
    void ruleCountCountsEntriesNotRows() {
        // A rule whose class is absent from the current image has no row. Counting rows would
        // read "0 rules active" while objects were being hidden.
        selected.add(pc("AbsentFromThisImage"));
        model.setClassSelected(pc("CD3"), true);
        assertThat(model.activeRuleCount()).isEqualTo(2);
        assertThat(model.activeRules()).containsExactlyInAnyOrder(pc("AbsentFromThisImage"), pc("CD3"));
    }

    // --- External writes and snapshots --------------------------------------------------------

    @Test
    void externalRemovalUnchecksTheCorrespondingComponent() {
        model.setComponentSelected("CD8", true);
        assertThat(model.isComponentSelected("CD8")).isTrue();
        selected.remove(pc("CD8"));
        model.onExternalChange();
        assertThat(model.isComponentSelected("CD8")).isFalse();
    }

    @Test
    void externalRemovalOfTheCompositeClearsTheWholeComponentRule() {
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        selected.remove(pc("CD3", "CD8"));
        model.onExternalChange();
        assertThat(model.getSelectedComponents()).isEmpty();
    }

    @Test
    void captureAndRestoreRoundTripsRulesAndCombination() {
        model.setClassSelected(pc("CD3"), true);
        model.setComponentSelected("CD8", true);
        model.setComponentSelected("CD4", true);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        VisibilityRuleModel.ModelState state = model.captureState();

        model.clearAllRules();
        model.setCombination(VisibilityRuleModel.Combination.ANY);
        assertThat(selected).isEmpty();

        model.restoreState(state);
        assertThat(selected).containsExactlyInAnyOrder(pc("CD3"), pc("CD4", "CD8"));
        assertThat(model.getCombination()).isEqualTo(VisibilityRuleModel.Combination.ALL);
        assertThat(model.getSelectedComponents()).containsExactlyInAnyOrder("CD4", "CD8");
    }

    @Test
    void removeRuleDropsOneEntryAndLeavesTheOthers() {
        model.setClassSelected(pc("CD3"), true);
        model.setClassSelected(pc("CD8"), true);
        model.removeRule(pc("CD3"));
        assertThat(selected).containsExactly(pc("CD8"));
    }

    @Test
    void theApplyingGuardIsClearedAfterEveryWrite() {
        model.setClassSelected(pc("CD3"), true);
        assertThat(model.isApplying()).isFalse();
    }
}
