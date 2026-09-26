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

    // --- Any / All ---------------------------------------------------------------------------

    /** The classes in the image, which a component rule expands to. */
    private static final List<PathClass> KNOWN = List.of(
            pc("CD3"), pc("CD8"), pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"),
            pc("CD4", "CD8"), pc("CD4"), pc("PanCK"));

    @Test
    void aComponentIsWrittenAsEveryKnownClassContainingIt() {
        // Under exact matching the bare entry CD3 reaches only objects classed exactly CD3, so
        // the rule is the list of classes it covers -- wherever CD3 sits in the name.
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD3", true);
        assertThat(selected).containsExactlyInAnyOrder(
                pc("CD3"), pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"));
    }

    @Test
    void anyWritesTheUnionOfTheCheckedComponents() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);
        assertThat(selected).containsExactlyInAnyOrder(
                pc("CD3"), pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"), pc("CD4", "CD8"), pc("CD4"));
    }

    @Test
    void allWritesOnlyClassesCarryingEveryCheckedComponent() {
        model.setKnownClasses(KNOWN);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD8", true);
        model.setComponentSelected("CD3", true);
        assertThat(selected).containsExactlyInAnyOrder(pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"));

        model.setComponentSelected("CD3", false);
        assertThat(selected).containsExactlyInAnyOrder(
                pc("CD8"), pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"), pc("CD4", "CD8"));
    }

    @Test
    void switchingBetweenAnyAndAllLeavesNothingStaleBehind() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);
        Set<PathClass> underAny = new LinkedHashSet<>(selected);

        model.setCombination(VisibilityRuleModel.Combination.ALL);
        assertThat(selected).as("no known class carries both").isEmpty();

        model.setCombination(VisibilityRuleModel.Combination.ANY);
        assertThat(selected).isEqualTo(underAny);
    }

    @Test
    void anyAndAllAreIdenticalBelowTwoCheckedComponents() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD4", true);
        Set<PathClass> underAny = new LinkedHashSet<>(selected);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        assertThat(selected).isEqualTo(underAny).containsExactlyInAnyOrder(pc("CD4"), pc("CD4", "CD8"));
    }

    @Test
    void aClassThatAppearsLaterIsPickedUpWithoutAClick() {
        model.setKnownClasses(List.of(pc("PanCK")));
        model.setComponentSelected("PanCK", true);
        assertThat(selected).containsExactly(pc("PanCK"));

        model.setKnownClasses(List.of(pc("PanCK"), pc("PanCK", "Ki67")));
        assertThat(selected).containsExactlyInAnyOrder(pc("PanCK"), pc("PanCK", "Ki67"));

        model.setKnownClasses(List.of(pc("PanCK", "Ki67")));
        assertThat(selected).containsExactly(pc("PanCK", "Ki67"));
    }

    @Test
    void aCheckedClassAndACoveringComponentShareAnEntryWithoutLosingIt() {
        model.setKnownClasses(KNOWN);
        model.setClassSelected(pc("CD3", "CD8"), true);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD3", false);
        assertThat(selected).as("the class tick outlives the component").containsExactly(pc("CD3", "CD8"));
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
        model.setKnownClasses(KNOWN);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.soloComponent("CD4");
        assertThat(selected).containsExactlyInAnyOrder(pc("CD4"), pc("CD4", "CD8"));
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
        model.setKnownClasses(KNOWN);
        model.setClassSelected(pc("CD3"), true);
        model.setComponentSelected("CD8", true);

        assertThat(model.sourceOf(pc("CD3"))).isEqualTo(VisibilityRuleModel.RuleSource.CLASS);
        assertThat(model.sourceOf(pc("CD4", "CD8"))).isEqualTo(VisibilityRuleModel.RuleSource.COMPONENTS_ANY);
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
    void externalRemovalUnchecksAComponentOnlyOnceEverythingItWroteHasGone() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD4", true);
        selected.remove(pc("CD4"));
        model.onExternalChange();
        assertThat(model.isComponentSelected("CD4")).as("CD4: CD8 is still in force").isTrue();
        selected.remove(pc("CD4", "CD8"));
        model.onExternalChange();
        assertThat(model.isComponentSelected("CD4")).isFalse();
    }

    @Test
    void externalRemovalOfEveryAllClassClearsTheWholeComponentRule() {
        model.setKnownClasses(KNOWN);
        model.setCombination(VisibilityRuleModel.Combination.ALL);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD8", true);
        selected.remove(pc("CD3", "CD8"));
        selected.remove(pc("PanCK", "CD3", "CD8"));
        model.onExternalChange();
        assertThat(model.getSelectedComponents()).isEmpty();
    }

    @Test
    void removingOneComponentClassFromTheRulesTableDropsTheComponentsBehindIt() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD3", true);
        model.setComponentSelected("CD4", true);
        model.removeRule(pc("CD4", "CD8"));
        assertThat(model.getSelectedComponents()).containsExactly("CD3");
        assertThat(selected).containsExactlyInAnyOrder(
                pc("CD3"), pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"));
    }

    @Test
    void captureAndRestoreRoundTripsRulesAndCombination() {
        model.setKnownClasses(KNOWN);
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
        assertThat(model.sourceOf(pc("CD4", "CD8"))).isEqualTo(VisibilityRuleModel.RuleSource.COMPONENTS_ALL);
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

    @Test
    void componentEntriesForEitherCombination() {
        model.setKnownClasses(KNOWN);
        model.setComponentSelected("CD8", true);
        model.setComponentSelected("CD3", true);
        Set<PathClass> before = new LinkedHashSet<>(selected);

        assertThat(model.componentEntriesFor(VisibilityRuleModel.Combination.ANY))
                .containsExactlyInAnyOrder(pc("CD3"), pc("CD8"), pc("CD3", "CD8"),
                        pc("PanCK", "CD3", "CD8"), pc("CD4", "CD8"));
        assertThat(model.componentEntriesFor(VisibilityRuleModel.Combination.ALL))
                .as("the classes the model writes under All, whatever the check order")
                .containsExactlyInAnyOrder(pc("CD3", "CD8"), pc("PanCK", "CD3", "CD8"));
        assertThat(selected).isEqualTo(before);
        assertThat(model.getCombination()).isEqualTo(VisibilityRuleModel.Combination.ANY);
    }
}
