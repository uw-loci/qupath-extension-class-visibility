package qupath.ext.classvisibility.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the Find filter matched, which is the half of the highlighting that can be wrong.
 *
 * <p>Rendering the runs needs a JavaFX toolkit and a font; deciding which characters matched
 * does not, and that is the decision a user sees as bold text on the wrong letters.</p>
 */
class MatchHighlighterTest {

    private static String render(List<MatchHighlighter.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (MatchHighlighter.Run run : runs) {
            sb.append(run.match() ? "[" + run.text() + "]" : run.text());
        }
        return sb.toString();
    }

    @Test
    @DisplayName("An inactive filter leaves the name in one plain run")
    void noFilterMeansNoHighlight() {
        assertThat(MatchHighlighter.isActive(null)).isFalse();
        assertThat(MatchHighlighter.isActive("")).isFalse();
        assertThat(MatchHighlighter.isActive("   ")).isFalse();
        assertThat(render(MatchHighlighter.runs("CD3: CD8", ""))).isEqualTo("CD3: CD8");
        assertThat(render(MatchHighlighter.runs("CD3: CD8", "  "))).isEqualTo("CD3: CD8");
    }

    @Test
    @DisplayName("The bolded range is the one that matched, not a re-search for the typed case")
    void matchingIsCaseInsensitiveAndBoldsTheNamesOwnCase() {
        assertThat(render(MatchHighlighter.runs("CD3: CD8", "cd8"))).isEqualTo("CD3: [CD8]");
        assertThat(render(MatchHighlighter.runs("Tumor", "TUM"))).isEqualTo("[Tum]or");
        assertThat(render(MatchHighlighter.runs("PD1 positive", "Pos")))
                .isEqualTo("PD1 [pos]itive");
    }

    @Test
    @DisplayName("Every occurrence is bolded, not just the first")
    void repeatedMatchesAreAllHighlighted() {
        assertThat(render(MatchHighlighter.runs("CD3: CD3 negative", "cd3")))
                .isEqualTo("[CD3]: [CD3] negative");
        // Adjacent occurrences leave no unmatched run between them.
        assertThat(render(MatchHighlighter.runs("abab", "ab"))).isEqualTo("[ab][ab]");
    }

    @Test
    @DisplayName("A match at either end produces no empty run")
    void matchesAtTheEdgesDoNotProduceEmptyRuns() {
        List<MatchHighlighter.Run> start = MatchHighlighter.runs("CD8: PD1", "cd8");
        assertThat(start).hasSize(2);
        assertThat(render(start)).isEqualTo("[CD8]: PD1");

        List<MatchHighlighter.Run> whole = MatchHighlighter.runs("CD8", "cd8");
        assertThat(whole).hasSize(1);
        assertThat(whole.get(0).match()).isTrue();
    }

    @Test
    @DisplayName("The filter is trimmed, exactly as the list filter trims it")
    void theFilterIsTrimmedTheSameWayTheListFilterTrimsIt() {
        // ClassVisibilityPane.applyFilter trims before matching, so a row listed on "  cd8  "
        // must highlight on it too. Highlighting the untrimmed text would find nothing and show
        // a listed row with nothing bold in it.
        assertThat(render(MatchHighlighter.runs("CD3: CD8", "  cd8  "))).isEqualTo("CD3: [CD8]");
    }

    @Test
    @DisplayName("A filter that does not match leaves the name in one plain run")
    void noMatchMeansOnePlainRun() {
        List<MatchHighlighter.Run> runs = MatchHighlighter.runs("CD3: CD8", "cd4");
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).match()).isFalse();
        assertThat(runs.get(0).text()).isEqualTo("CD3: CD8");
    }

    @Test
    @DisplayName("The runs always concatenate back to the original name")
    void runsAlwaysReconstructTheName() {
        for (String name : List.of("CD3: CD8: CD4: CD45", "Unclassified", "a", "PD1 pos: PD1 neg")) {
            for (String filter : List.of("c", "cd", "pd1", ": ", "x", "PD1 POS")) {
                StringBuilder sb = new StringBuilder();
                MatchHighlighter.runs(name, filter).forEach(run -> sb.append(run.text()));
                assertThat(sb.toString()).as("name %s, filter %s", name, filter).isEqualTo(name);
            }
        }
    }

    @Test
    @DisplayName("A name whose case folding changes its length is left unhighlighted, not misaligned")
    void lengthChangingCaseFoldingDeclinesToHighlightRatherThanBoldTheWrongRange() {
        // U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE lowercases to two characters, so every
        // index after it would point at the wrong character in the original name.
        String name = "\u0130stanbul CD8";
        assertThat(name.toLowerCase(Locale.ROOT).length()).isNotEqualTo(name.length());
        List<MatchHighlighter.Run> runs = MatchHighlighter.runs(name, "cd8");
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).match()).isFalse();
        assertThat(runs.get(0).text()).isEqualTo(name);
    }

    @Test
    @DisplayName("An empty name yields no runs at all")
    void anEmptyNameYieldsNoRuns() {
        assertThat(MatchHighlighter.runs("", "cd8")).isEmpty();
        assertThat(MatchHighlighter.runs(null, "cd8")).isEmpty();
    }
}
