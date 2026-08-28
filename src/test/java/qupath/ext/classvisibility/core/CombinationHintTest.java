package qupath.ext.classvisibility.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import qupath.ext.classvisibility.core.CombinationHint.Cue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the Any / All hint fires, and when it stops.
 *
 * <p>The control is inert below two checked components, so the crossing to two is the only moment
 * at which a user can connect it to an effect they just caused (user, 2026-08-28). Everything
 * about that decision -- the crossing, the once-per-session latch, the preference gate and the
 * cancel -- is here, away from the animation, so it can be tested with no JavaFX toolkit.</p>
 *
 * <p>The session latch is static, because it has to outlive a panel that is closed and reopened
 * routinely. {@link CombinationHint#resetSession()} exists for these tests and nothing else, and
 * every test here starts by calling it: without that they would pass or fail on the order JUnit
 * happened to run them in.</p>
 */
class CombinationHintTest {

    private CombinationHint hint;

    @BeforeEach
    void setUp() {
        CombinationHint.resetSession();
        hint = new CombinationHint();
    }

    @Test
    @DisplayName("One to two is the crossing that fires it")
    void oneToTwoPulses() {
        assertThat(hint.onComponentCount(0, true)).isEqualTo(Cue.STOP);
        assertThat(hint.onComponentCount(1, true)).isEqualTo(Cue.STOP);
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("Zero straight to several fires it too -- Check all listed is one action")
    void zeroToManyPulses() {
        assertThat(hint.onComponentCount(7, true)).isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("Staying above two does not re-fire on every added component")
    void climbingAboveTwoIsNotACrossing() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
        assertThat(hint.onComponentCount(3, true)).isEqualTo(Cue.NONE);
        assertThat(hint.onComponentCount(4, true)).isEqualTo(Cue.NONE);
    }

    @Test
    @DisplayName("Repeated refreshes at an unchanged count do nothing")
    void refreshingAtTheSameCountIsQuiet() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.NONE);
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.NONE);
    }

    @Test
    @DisplayName("Dropping back below two cancels: it is pointing at a disabled control")
    void droppingBelowTwoStops() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
        assertThat(hint.onComponentCount(1, true)).isEqualTo(Cue.STOP);
        assertThat(hint.onComponentCount(0, true)).isEqualTo(Cue.STOP);
    }

    @Test
    @DisplayName("Once per session: crossing again in the same panel teaches nothing new")
    void theSameInstanceOnlyPulsesOnce() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
        assertThat(hint.onComponentCount(1, true)).isEqualTo(Cue.STOP);
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.NONE);
    }

    @Test
    @DisplayName("Once per session: closing and reopening the panel does not teach it again")
    void aSecondPanelInTheSameSessionDoesNotPulse() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);

        // Closing the panel disposes the Pane, so the next open builds a new CombinationHint.
        // The latch is static precisely so that this one stays quiet.
        CombinationHint reopened = new CombinationHint();
        assertThat(reopened.onComponentCount(2, true)).isEqualTo(Cue.NONE);
    }

    @Test
    @DisplayName("A new QuPath session teaches it again -- nothing about this is persisted")
    void aNewSessionPulsesAgain() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);

        CombinationHint.resetSession();
        assertThat(new CombinationHint().onComponentCount(2, true)).isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("Switched off, it never fires and never spends the session")
    void thePreferenceGatesItWithoutConsumingTheLatch() {
        assertThat(hint.onComponentCount(2, false)).isEqualTo(Cue.NONE);
        assertThat(hint.onComponentCount(5, false)).isEqualTo(Cue.NONE);

        // Switching it back on mid-session still teaches, because the disabled crossings did not
        // spend the one showing.
        assertThat(hint.onComponentCount(1, false)).isEqualTo(Cue.STOP);
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
    }

    @Test
    @DisplayName("Switched off, dropping below two still cancels anything already running")
    void theCancelIsNotGatedByThePreference() {
        assertThat(hint.onComponentCount(2, true)).isEqualTo(Cue.PULSE);
        assertThat(hint.onComponentCount(1, false)).isEqualTo(Cue.STOP);
    }
}
