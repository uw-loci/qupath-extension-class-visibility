package qupath.ext.classvisibility.core;

import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule state machine: checked classes plus a component rule, reconciled against
 * QuPath's shared {@code selectedClasses} set as a <b>minimal delta</b>.
 *
 * <p>Three rules, all load-bearing, all verified against QuPath 0.7 source:</p>
 * <ol>
 *   <li><b>Never hand-roll a {@link PathClass}.</b> Exact rules write back the harvested
 *       instance; component rules go through {@link PathClass#fromCollection(Collection)},
 *       which interns (and, for one element, delegates to {@code getInstance}). QuPath's
 *       {@code isSelectedClass} is an identity/equality lookup on the set, so a reconstructed
 *       instance would silently fail to match.</li>
 *   <li><b>{@code Any} is N entries; {@code All} is ONE composite.</b> {@code selectedClasses}
 *       is evaluated as an OR over its elements, so N separate entries give OR. AND requires
 *       collapsing the checked components into a single composite via {@code fromCollection}.
 *       {@code fromCollection} builds in <i>iterator order</i>, so {@code ["CD3","CD4"]} and
 *       {@code ["CD4","CD3"]} intern to two different instances -- the token list is therefore
 *       sorted before every build, and the previously-added instance is kept in
 *       {@code ownedEntries} so the stale composite is removed rather than orphaned.</li>
 *   <li><b>Minimal delta, always.</b> Only entries this model owns are ever removed. QuPath's
 *       own Classes pane writes the same set; a {@code clear()} plus {@code addAll()} would
 *       destroy its entries, and each element change also runs one uncoalesced overlay-cache
 *       clear, so touching fewer entries is a correctness rule and an efficiency rule at once.</li>
 * </ol>
 *
 * <p>JavaFX-free by design. The only contact with the outside world is
 * {@link SelectedClassSet}, a single-method view of the live set, so unit tests supply a plain
 * {@link LinkedHashSet} and no {@code OverlayOptions} is required.</p>
 */
public final class VisibilityRuleModel {

    /**
     * One-method view of the live {@code selectedClasses} set. In production this is
     * {@code OverlayOptions.getSharedInstance()::selectedClassesProperty}; in tests it is a
     * plain mutable set.
     */
    @FunctionalInterface
    public interface SelectedClassSet {
        /** @return the live, mutable set of selected classes. */
        Set<PathClass> selectedClasses();
    }

    /** How two or more checked components combine into rules. */
    public enum Combination {
        /** OR: each checked component becomes its own entry. First-run default. */
        ANY,
        /** AND: the checked components collapse into one composite entry. */
        ALL
    }

    /** Where an entry in {@code selectedClasses} came from. */
    public enum RuleSource {
        /** Checked in the class list. */
        CLASS,
        /** Produced by the component list under {@code Any}. */
        COMPONENTS_ANY,
        /** Produced by the component list under {@code All}. */
        COMPONENTS_ALL,
        /** Written by something other than this panel -- in practice, QuPath's Classes pane. */
        ELSEWHERE
    }

    /**
     * A complete snapshot of this model plus the entries it was reconciled against. Used for the
     * one-deep undo slot and for the session snapshot's rule half.
     *
     * @param entries the full contents of {@code selectedClasses} at capture time
     * @param exact the classes checked in the class list
     * @param components the checked component names
     * @param combination the {@code Any} / {@code All} setting
     */
    public record ModelState(Set<PathClass> entries,
                             Set<PathClass> exact,
                             Set<String> components,
                             Combination combination) {
    }

    private final SelectedClassSet target;

    /** Classes checked in the class list. Written back as-is (harvested instances). */
    private final Set<PathClass> exactSelections = new LinkedHashSet<>();

    /** Component names checked in the component list. */
    private final Set<String> componentSelections = new LinkedHashSet<>();

    /** Every entry this model last wrote, so a later delta can remove exactly those. */
    private final Set<PathClass> ownedEntries = new LinkedHashSet<>();

    /** Entries the user explicitly unchecked this pass, even if this model did not add them. */
    private final Set<PathClass> pendingDrops = new LinkedHashSet<>();

    private Combination combination = Combination.ANY;

    /** True while this model is writing to the set, so its own change listener can stand down. */
    private boolean applying = false;

    private Runnable changeListener = () -> { };

    /**
     * @param target the live selected-class set to reconcile against
     */
    public VisibilityRuleModel(SelectedClassSet target) {
        this.target = target;
    }

    /**
     * Set a callback invoked after every state change, so the UI can re-render. Called on
     * whichever thread mutated the model, which in production is always the FX thread.
     *
     * @param listener the callback; null installs a no-op
     */
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    /** @return true while this model is mid-write; the set listener must ignore events then. */
    public boolean isApplying() {
        return applying;
    }

    /** @return the {@code Any} / {@code All} setting. */
    public Combination getCombination() {
        return combination;
    }

    /**
     * Switch between {@code Any} and {@code All}. At zero or one checked component the two are
     * identical and no delta results; from two components up, this swaps N entries for one
     * composite or the reverse.
     *
     * @param value the new combination
     */
    public void setCombination(Combination value) {
        if (value == null || value == combination) {
            return;
        }
        combination = value;
        apply();
    }

    /** @return the checked component names, in check order. */
    public Set<String> getSelectedComponents() {
        return Collections.unmodifiableSet(componentSelections);
    }

    /**
     * @param component the component name
     * @return whether that component is checked
     */
    public boolean isComponentSelected(String component) {
        return componentSelections.contains(component);
    }

    /**
     * @param pathClass a class, or null for Unclassified
     * @return whether an entry for that class is currently in the set. Derived from the set
     *         itself, never stored on a row, so image switches and third-party writes are
     *         correct for free.
     */
    public boolean isClassSelected(PathClass pathClass) {
        PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
        return target.selectedClasses().contains(key);
    }

    /**
     * Check or uncheck one class.
     *
     * @param pathClass the class; null is folded to {@link PathClass#NULL_CLASS}
     * @param selected whether it should be a rule
     */
    public void setClassSelected(PathClass pathClass, boolean selected) {
        PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
        if (selected) {
            exactSelections.add(key);
            pendingDrops.remove(key);
        } else {
            exactSelections.remove(key);
            // Unchecking a row must also clear an identical entry written elsewhere -- the row
            // showed as checked, so leaving it would make the click look ignored.
            pendingDrops.add(key);
        }
        apply();
    }

    /**
     * Check or uncheck one component.
     *
     * @param component the component name; ignored when null or blank
     * @param selected whether it should contribute to the component rule
     */
    public void setComponentSelected(String component, boolean selected) {
        if (component == null || component.isBlank()) {
            return;
        }
        boolean changed = selected ? componentSelections.add(component) : componentSelections.remove(component);
        if (changed) {
            apply();
        }
    }

    /**
     * Check every supplied class, leaving all other rules alone. Applied as one delta.
     *
     * @param classes the classes to check
     */
    public void checkClasses(Collection<PathClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        for (PathClass pathClass : classes) {
            PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
            exactSelections.add(key);
            pendingDrops.remove(key);
        }
        apply();
    }

    /**
     * Uncheck every supplied class, leaving all other rules alone. Applied as one delta.
     *
     * @param classes the classes to uncheck
     */
    public void uncheckClasses(Collection<PathClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        for (PathClass pathClass : classes) {
            PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
            exactSelections.remove(key);
            pendingDrops.add(key);
        }
        apply();
    }

    /**
     * Leave exactly one class as the only rule. The mode change that makes this "show only" is
     * the caller's job -- this model owns the set, not the mode.
     *
     * @param pathClass the class to isolate
     */
    public void soloClass(PathClass pathClass) {
        PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
        exactSelections.clear();
        componentSelections.clear();
        exactSelections.add(key);
        dropEverythingNotDesired();
        apply();
    }

    /**
     * Leave exactly one component as the only rule. One component is one entry under either
     * combination, so {@code Any} / {@code All} is irrelevant here.
     *
     * @param component the component to isolate
     */
    public void soloComponent(String component) {
        if (component == null || component.isBlank()) {
            return;
        }
        exactSelections.clear();
        componentSelections.clear();
        componentSelections.add(component);
        dropEverythingNotDesired();
        apply();
    }

    /**
     * Remove one entry, whatever produced it. Used by the {@code Active rules} table's
     * {@code Remove} button, which can address entries with no row in either list.
     *
     * @param entry the entry to remove
     */
    public void removeRule(PathClass entry) {
        if (entry == null) {
            return;
        }
        RuleSource source = sourceOf(entry);
        exactSelections.remove(entry);
        if (source == RuleSource.COMPONENTS_ALL) {
            componentSelections.clear();
        } else if (source == RuleSource.COMPONENTS_ANY) {
            componentSelections.removeIf(token -> PathClass.fromCollection(List.of(token)) == entry);
        }
        pendingDrops.add(entry);
        apply();
    }

    /**
     * Remove every rule, including entries written elsewhere. Mirrors QuPath's
     * "Reset selected classes"; the mode and the exact flag are left alone, which is why this
     * on its own can walk a user into the all-hidden state -- the caller is responsible for the
     * status strip that says so.
     */
    public void clearAllRules() {
        exactSelections.clear();
        componentSelections.clear();
        dropEverythingNotDesired();
        apply();
    }

    /**
     * @return every entry currently in the set, in iteration order
     */
    public List<PathClass> activeRules() {
        return new ArrayList<>(target.selectedClasses());
    }

    /**
     * @return the number of entries currently in the set. The status strip counts entries, not
     *         checked rows: a rule whose class is absent from the current image has no row, and
     *         a row-counting indicator would read "0 rules active" while objects were hidden.
     */
    public int activeRuleCount() {
        return target.selectedClasses().size();
    }

    /**
     * @param entry an entry in the set
     * @return what produced it
     */
    public RuleSource sourceOf(PathClass entry) {
        if (entry == null) {
            return RuleSource.ELSEWHERE;
        }
        if (exactSelections.contains(entry)) {
            return RuleSource.CLASS;
        }
        if (componentEntries().contains(entry)) {
            return combination == Combination.ALL ? RuleSource.COMPONENTS_ALL : RuleSource.COMPONENTS_ANY;
        }
        return RuleSource.ELSEWHERE;
    }

    /**
     * @return the entries produced by the component rule. A class row whose class is one of
     *         these renders checked with its checkbox disabled, so an uncheck in one list cannot
     *         silently destroy a rule built in the other.
     */
    public Set<PathClass> componentDerivedEntries() {
        return componentEntries();
    }

    /**
     * Reconcile bookkeeping after somebody else wrote the set -- QuPath's Classes pane is the
     * only other live writer. Never writes back: it drops our claim on entries that have gone,
     * so the corresponding checkbox unticks, and leaves everything else alone. Calling this
     * while this model is mid-write is a no-op.
     */
    public void onExternalChange() {
        if (applying) {
            return;
        }
        Set<PathClass> current = target.selectedClasses();
        exactSelections.retainAll(current);
        if (!componentSelections.isEmpty() && !current.containsAll(componentEntries())) {
            if (combination == Combination.ALL) {
                // The composite is one indivisible entry: if it has gone, the rule has gone.
                componentSelections.clear();
            } else {
                componentSelections.removeIf(
                        token -> !current.contains(PathClass.fromCollection(List.of(token))));
            }
        }
        ownedEntries.retainAll(current);
        // The rule count and every row's derived check state may have moved even when our own
        // bookkeeping did not, so the UI is always told to re-render.
        changeListener.run();
    }

    /**
     * @return a snapshot of this model and the set it was reconciled against
     */
    public ModelState captureState() {
        return new ModelState(
                new LinkedHashSet<>(target.selectedClasses()),
                new LinkedHashSet<>(exactSelections),
                new LinkedHashSet<>(componentSelections),
                combination);
    }

    /**
     * Restore a previously captured state, as a minimal delta against whatever is in the set now.
     *
     * @param state the state to restore; null is ignored
     */
    public void restoreState(ModelState state) {
        if (state == null) {
            return;
        }
        exactSelections.clear();
        exactSelections.addAll(state.exact());
        componentSelections.clear();
        componentSelections.addAll(state.components());
        combination = state.combination();

        Set<PathClass> current = target.selectedClasses();
        List<PathClass> toRemove = new ArrayList<>();
        for (PathClass entry : current) {
            if (!state.entries().contains(entry)) {
                toRemove.add(entry);
            }
        }
        List<PathClass> toAdd = new ArrayList<>();
        for (PathClass entry : state.entries()) {
            if (!current.contains(entry)) {
                toAdd.add(entry);
            }
        }
        ownedEntries.clear();
        ownedEntries.addAll(state.entries());
        pendingDrops.clear();
        writeDelta(toRemove, toAdd);
        changeListener.run();
    }

    /**
     * The entries this model wants in the set, given the current checks.
     *
     * @return the desired entry set, in a deterministic order
     */
    private Set<PathClass> computeDesired() {
        Set<PathClass> desired = new LinkedHashSet<>(exactSelections);
        desired.addAll(componentEntries());
        return desired;
    }

    /**
     * @return the entries the component rule contributes: one per component under {@code Any},
     *         a single sorted composite under {@code All}
     */
    private Set<PathClass> componentEntries() {
        if (componentSelections.isEmpty()) {
            return Set.of();
        }
        if (combination == Combination.ALL && componentSelections.size() > 1) {
            PathClass composite = compositeEntry();
            return composite == null ? Set.of() : Set.of(composite);
        }
        Set<PathClass> entries = new LinkedHashSet<>();
        for (String token : componentSelections) {
            entries.add(PathClass.fromCollection(List.of(token)));
        }
        return entries;
    }

    /**
     * @return the interned {@code All} composite for the current component selection, built from
     *         a deterministically sorted token list; null when fewer than two components are
     *         checked
     */
    private PathClass compositeEntry() {
        if (componentSelections.size() < 2) {
            return null;
        }
        // fromCollection builds in iterator order, so the order must be fixed before the call or
        // two different instances result for the same component set.
        return PathClass.fromCollection(new ArrayList<>(new TreeSet<>(componentSelections)));
    }

    /** Mark every current entry for removal, so the next apply() leaves nothing behind. */
    private void dropEverythingNotDesired() {
        pendingDrops.addAll(target.selectedClasses());
    }

    /**
     * Compute and write the minimal delta. Everything happens inside the caller's FX event, so
     * the viewer repaint stays coalesced.
     */
    private void apply() {
        Set<PathClass> desired = computeDesired();
        Set<PathClass> current = target.selectedClasses();

        Set<PathClass> removeSet = new LinkedHashSet<>();
        for (PathClass entry : ownedEntries) {
            if (!desired.contains(entry) && current.contains(entry)) {
                removeSet.add(entry);
            }
        }
        for (PathClass entry : pendingDrops) {
            if (!desired.contains(entry) && current.contains(entry)) {
                removeSet.add(entry);
            }
        }
        List<PathClass> toAdd = new ArrayList<>();
        for (PathClass entry : desired) {
            if (!current.contains(entry)) {
                toAdd.add(entry);
            }
        }

        pendingDrops.clear();
        ownedEntries.clear();
        ownedEntries.addAll(desired);

        writeDelta(new ArrayList<>(removeSet), toAdd);
        changeListener.run();
    }

    /**
     * The single write point into the shared set. The re-entrancy guard is here so that every
     * path -- apply, restore, undo -- is covered by it.
     *
     * @param toRemove entries to remove
     * @param toAdd entries to add
     */
    private void writeDelta(List<PathClass> toRemove, List<PathClass> toAdd) {
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return;
        }
        applying = true;
        try {
            Set<PathClass> current = target.selectedClasses();
            current.removeAll(toRemove);
            current.addAll(toAdd);
        } finally {
            applying = false;
        }
    }
}
