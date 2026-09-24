package qupath.ext.classvisibility.core;

import qupath.lib.objects.classes.PathClass;

import java.util.Collection;
import java.util.Set;

/**
 * Which class rows the checked components cover, and when to pulse them.
 *
 * <p>The component list is the panel's headline feature -- one row there instead of twenty-six
 * in the class list -- but until 0.3.0 nothing connected a checked component back to the class
 * rows it acts on. A row that is on because it was ticked and a row that is on because a
 * component rule reaches it looked the same (user, 2026-09-24).</p>
 *
 * <p><b>Not {@link CombinationHint}, and deliberately a separate object.</b> That one is a
 * teaching aid and fires once per session; this is ongoing feedback and fires every time the
 * component rule changes. The two have opposite repetition rules, so folding them together
 * would put a latch where none belongs.</p>
 *
 * <p>JavaFX-free, so the matching and the repetition rule are testable without a toolkit.</p>
 */
public final class ComponentMatchHighlight {

    /** What the panel should do with the pulse right now. */
    public enum Cue {
        /** Start the pulse: the component rule just changed and reaches at least one listed row. */
        PULSE,
        /** Stop any running pulse: it is pointing at rows the rule no longer describes. */
        STOP,
        /** Leave the pulse alone. */
        NONE
    }

    /** The component entries at the previous call, so the cue fires on a change, not a state. */
    private Set<PathClass> lastEntries = Set.of();

    /**
     * Report the component rule's current entries and get the cue.
     *
     * <p>Called on every rule-dependent refresh. Only a change to the entries fires: a new
     * census, a Find filter or a mode switch re-renders the rows but is not something the user
     * did to the component rule. Entries rather than checked names, because {@code Any} and
     * {@code All} over the same names are different rules -- and over one name they are the
     * same rule, so switching there correctly fires nothing.</p>
     *
     * @param componentEntries the entries the component rule has written into the rule set
     * @param coveredRows how many listed class rows those entries reach
     * @param mayFire whether a pulse is allowed right now -- the user's preference, and the panel
     *                being on screen. Nothing is held back for later when false: a change nobody
     *                saw is not worth replaying once the panel reappears
     * @return what to do with the pulse
     */
    public Cue onComponentEntries(Set<PathClass> componentEntries, int coveredRows, boolean mayFire) {
        Set<PathClass> current = componentEntries == null ? Set.of() : Set.copyOf(componentEntries);
        boolean changed = !current.equals(lastEntries);
        lastEntries = current;
        if (current.isEmpty()) {
            return Cue.STOP;
        }
        if (!changed) {
            return Cue.NONE;
        }
        return mayFire && coveredRows > 0 ? Cue.PULSE : Cue.STOP;
    }

    /**
     * @param componentEntries the entries the component rule has written into the rule set
     * @param candidate a listed class; {@code null} is treated as Unclassified
     * @param exactMatchesOnly the current value of QuPath's {@code Exact matches only} setting
     * @return whether the component rule reaches objects of that class. The same predicate as
     *         the {@code Affects} column and the {@code Active rules} statuses, so what is marked
     *         is exactly what the rule acts on.
     */
    public static boolean covers(Collection<PathClass> componentEntries, PathClass candidate,
                                 boolean exactMatchesOnly) {
        for (PathClass entry : componentEntries) {
            if (ClassCensus.ruleMatches(entry, candidate, exactMatchesOnly)) {
                return true;
            }
        }
        return false;
    }
}
