package qupath.ext.classvisibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.ClassVisibilityExtension.CloseMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a close tells the user, now that closing restores.
 *
 * <p>The "N class rules are still in force" notification (finding C1) was written when closing
 * the panel left our rules behind. From 0.1.1 it cannot: the close replays the state the panel
 * opened onto. Firing it anyway would announce the user's own pre-existing rules back at them on
 * every close, which is how a user learns to dismiss the one notification that matters. It is
 * suppressed exactly when the rules in force are the ones we just put back -- and no further,
 * because a restore that failed or did not land really can leave rules of ours behind, and that
 * is what the notification was built for.</p>
 *
 * <p>Pure decision, no toolkit: the method takes the facts and returns the message.</p>
 */
class CloseMessageTest {

    @Test
    @DisplayName("An ordinary close says nothing: the user is back where they were")
    void anOrdinaryCloseIsSilent() {
        assertThat(ClassVisibilityExtension.closeMessage(true, false, false, true, 3))
                .isEqualTo(CloseMessage.NONE);
        assertThat(ClassVisibilityExtension.closeMessage(true, false, false, false, 0))
                .isEqualTo(CloseMessage.NONE);
    }

    @Test
    @DisplayName("Rules that are not the ones we restored are still worth reporting")
    void rulesLeftBehindAreStillAnnounced() {
        // The restore did not land -- the rules in force are not the snapshot's, so they may be
        // ours, and the user is entitled to know why objects are still hidden.
        assertThat(ClassVisibilityExtension.closeMessage(false, false, false, true, 2))
                .isEqualTo(CloseMessage.RULES_ACTIVE);
    }

    @Test
    @DisplayName("No rules in force is nothing to report, restored or not")
    void noRulesMeansNoMessage() {
        assertThat(ClassVisibilityExtension.closeMessage(false, false, false, true, 0))
                .isEqualTo(CloseMessage.NONE);
    }

    @Test
    @DisplayName("A failed restore is always said, because the user is somewhere they did not choose")
    void aFailedRestoreIsAlwaysReported() {
        assertThat(ClassVisibilityExtension.closeMessage(false, true, false, false, 0))
                .isEqualTo(CloseMessage.RESTORE_FAILED);
        // It wins over the guard message: two notifications for one close teaches the user to
        // dismiss both, and "we could not put your view back" is the bigger fact.
        assertThat(ClassVisibilityExtension.closeMessage(false, true, true, true, 5))
                .isEqualTo(CloseMessage.RESTORE_FAILED);
    }

    @Test
    @DisplayName("After a restore the guard is always announced: it is undoing the user's own state")
    void theGuardIsAnnouncedAfterARestore() {
        // Reachable only when the snapshot itself was "show only checked classes" with nothing
        // checked -- a state the user had before opening the panel, set from QuPath's own class
        // list. The guard undoes it, so the guard has to say so, changed rule or not.
        assertThat(ClassVisibilityExtension.closeMessage(true, false, true, false, 0))
                .isEqualTo(CloseMessage.GUARD);
    }

    @Test
    @DisplayName("Without a restore the old gate holds: announce the guard only over a user setting")
    void theGuardIsQuietOverOurOwnOpeningDefault() {
        assertThat(ClassVisibilityExtension.closeMessage(false, false, true, false, 0))
                .isEqualTo(CloseMessage.NONE);
        assertThat(ClassVisibilityExtension.closeMessage(false, false, true, true, 0))
                .isEqualTo(CloseMessage.GUARD);
    }
}
