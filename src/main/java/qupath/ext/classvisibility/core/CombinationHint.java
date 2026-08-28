package qupath.ext.classvisibility.core;

/**
 * When to draw the eye to the <i>Checked components combine as:</i> control, and when to stop.
 *
 * <p><b>Any / All is inert below two checked components</b> -- its own label says so
 * ({@code label.combination.disabled}: "check two or more"). The instant a second component is
 * ticked is therefore the only moment at which a user can connect that control to an effect they
 * just caused. Teaching it then costs nothing; teaching it at any other time is noise. So the cue
 * fires on the crossing to two and nowhere else (user, 2026-08-28).</p>
 *
 * <p><b>Once per QuPath session</b>, held in memory and deliberately not persisted. "Session" is
 * the user's word, and a fresh launch should teach it again -- a preference that remembered
 * forever would silently turn a teaching aid into a thing that happened once, months ago, to
 * somebody else on the same machine. The flag is static because it outlives the panel: closing
 * and reopening the panel is a routine move here (opening hides every object), and re-teaching on
 * every reopen is nagging.</p>
 *
 * <p>The <i>decision</i> lives here, away from the animation, so all of it can be tested with no
 * JavaFX toolkit: the latch, the crossing, the preference gate and the cancel.</p>
 */
public final class CombinationHint {

    /** What the panel should do with the pulse right now. */
    public enum Cue {
        /** Start the pulse: the control just became meaningful, for the first time this session. */
        PULSE,
        /** Stop any running pulse: it is no longer pointing at anything usable. */
        STOP,
        /** Leave the pulse alone. */
        NONE
    }

    /** Session-scoped, in memory only. Static so it survives the panel being closed and reopened. */
    private static volatile boolean shownThisSession = false;

    /** The count at the previous call, so the cue fires on the crossing rather than on the state. */
    private int lastCount = 0;

    /**
     * Report the current checked-component count and get the cue.
     *
     * <p>Called on every rule-dependent refresh, so it must be cheap and idempotent: repeated
     * calls at an unchanged count return {@link Cue#NONE}, and {@link Cue#STOP} is returned
     * whenever the control is inert rather than only on the downward crossing, because stopping
     * an already-stopped animation is a no-op and one fewer piece of state to get wrong.</p>
     *
     * @param checkedComponents how many components are checked now
     * @param mayFire whether a pulse is allowed at all right now -- the user's preference, and
     *                the panel being on screen. When false nothing fires and the session latch is
     *                left <b>unconsumed</b>, so a crossing that happened behind a collapsed
     *                analysis pane, or with the preference off, does not spend the one showing
     *                the user gets
     * @return what to do with the pulse
     */
    public Cue onComponentCount(int checkedComponents, boolean mayFire) {
        int previous = lastCount;
        lastCount = checkedComponents;
        if (checkedComponents < 2) {
            // Below two the control is disabled, and a pulse on a disabled control points at a
            // dead end -- the same rule the "Check all listed" halo follows.
            return Cue.STOP;
        }
        if (!mayFire || previous >= 2 || shownThisSession) {
            return Cue.NONE;
        }
        shownThisSession = true;
        return Cue.PULSE;
    }

    /**
     * Forget that the hint has been shown. Package-private: this exists so each test starts from
     * a known session, and there is no user-facing action that should reset it.
     */
    static void resetSession() {
        shownThisSession = false;
    }
}
