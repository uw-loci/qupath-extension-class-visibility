package qupath.ext.classvisibility.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.OverlayOptions;

/**
 * Session-scoped holder for the one automatic snapshot: the state the panel found when it opened.
 *
 * <p><b>This is the recovery route, not a save feature.</b> Named presets stored in the project
 * ({@link VisibilityPreset}) superseded the manual single-slot save, and this deliberately
 * survived them. They serve different people: a preset is a workflow tool that requires the user
 * to have thought ahead, while this exists for the user who has hidden everything and does not
 * know how -- who, by definition, saved no preset (finding C1). There is nothing to press, which
 * is the whole point.</p>
 *
 * <p>Holder for one {@link VisibilitySnapshot}.
 *
 * <p>Static because the snapshot must outlive the panel. The tab can be removed and re-added,
 * and the toolbar button's context menu offers <i>Restore visibility state</i> whether or not
 * the panel is open -- a user whose viewer has gone blank should not have to find and open the
 * panel first.</p>
 *
 * <p>The snapshot is <b>not</b> persisted across restarts, deliberately. It records a live view
 * state including {@code selectedClasses}, which QuPath itself does not persist; writing it back
 * at the next launch would make this extension the only thing in QuPath capable of hiding
 * objects across a restart.</p>
 */
public final class VisibilityStateStore {

    private static final Logger logger = LoggerFactory.getLogger(VisibilityStateStore.class);

    private static VisibilitySnapshot snapshot;

    private VisibilityStateStore() {
        // Utility class.
    }

    /** @return whether a snapshot exists to restore. */
    public static synchronized boolean hasSnapshot() {
        return snapshot != null;
    }

    /**
     * Take a snapshot now, replacing any previous one. Called when the panel opens.
     *
     * <p>Replacing rather than keeping the first is what makes <i>Restore the state from when the
     * panel opened</i> mean what it says. A user who built a view in one panel session, closed
     * the panel and opened it again -- which hides everything -- wants their way back to be that
     * view, not to whatever the session started with an hour earlier.</p>
     *
     * <p>The snapshot is <b>returned</b> as well as stored, because it has two readers with two
     * lifetimes. This store keeps it for the session, for the on-demand <i>Restore the state from
     * when the panel opened</i> actions. The panel keeps the same instance for as long as it is
     * open and replays it when it closes, which is what makes the panel a session the user can
     * back out of. One capture, so the two can never disagree about what "before" was.</p>
     *
     * @param options the options to snapshot
     * @return the snapshot just taken, for a caller that needs to hold on to this one
     */
    public static synchronized VisibilitySnapshot capture(OverlayOptions options) {
        snapshot = VisibilitySnapshot.capture(options);
        logger.info("Captured visibility snapshot at panel open ({} class rules, mode {})",
                snapshot.selectedClasses().size(), snapshot.visibilityMode());
        return snapshot;
    }

    /**
     * Take a snapshot only if none exists yet.
     *
     * <p>The panel replaces the snapshot outright when it opens, so this covers the other
     * route: the recovery actions on the Extensions menu and the toolbar context menu, which work
     * whether or not the panel has ever been opened. That is what makes restore a recovery route
     * rather than a power-user feature -- the person who needs it is the person who did not plan
     * ahead.</p>
     *
     * @param options the options to snapshot
     */
    public static synchronized void captureIfAbsent(OverlayOptions options) {
        if (snapshot == null) {
            snapshot = VisibilitySnapshot.capture(options);
            logger.info("Captured automatic visibility snapshot before first change");
        }
    }

    /**
     * Write the snapshot back.
     *
     * @param options the options to restore into
     * @return true if a snapshot existed and was applied
     */
    public static synchronized boolean restore(OverlayOptions options) {
        if (snapshot == null) {
            return false;
        }
        snapshot.restore(options);
        logger.info("Restored saved visibility state");
        return true;
    }
}
