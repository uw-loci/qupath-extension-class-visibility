package qupath.ext.classvisibility.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the {@code Find} filter matched, so a cell can bold exactly that much of a name.
 *
 * <p>Split out from the cells for one reason: the ranges are the part that can be wrong, and
 * they are the part that can be tested without a JavaFX toolkit. Rendering them is arithmetic
 * over pixel widths; deciding <i>what matched</i> is not.</p>
 *
 * <p>Two rules the naive version gets wrong, both of which show up immediately at the design
 * centre of thirty near-identical class names:</p>
 * <ul>
 *   <li>The match is <b>case-insensitive</b>, so the bolded range is the range that matched in
 *       the name, never a re-search for the case the user typed. Typing {@code cd3} bolds
 *       {@code CD3}.</li>
 *   <li>A name can contain the filter <b>more than once</b> ({@code CD3: CD3 negative}), and
 *       every occurrence is bolded, not just the first.</li>
 * </ul>
 */
public final class MatchHighlighter {

    private MatchHighlighter() {
        // Utility class.
    }

    /**
     * One piece of a name: either matched by the filter or not.
     *
     * @param text the piece, in the name's own case
     * @param match whether the filter matched it
     */
    public record Run(String text, boolean match) {
    }

    /**
     * @param filterText the raw contents of the Find field
     * @return whether it filters anything. The filter itself trims and ignores an all-blank
     *         entry, so highlighting has to agree with it or a name would bold on a filter that
     *         listed every row.
     */
    public static boolean isActive(String filterText) {
        return filterText != null && !filterText.trim().isEmpty();
    }

    /**
     * Split a name into alternating unmatched and matched runs.
     *
     * <p>Matching mirrors {@code ClassVisibilityPane.applyFilter} exactly -- trimmed, lowercased
     * with {@link Locale#ROOT}, {@code contains} -- because a row is only ever highlighted if
     * that predicate already listed it. Any divergence would show as a listed row with nothing
     * bold in it, which reads as a bug in the filter rather than in the highlighter.</p>
     *
     * @param name the name to split, in its own case
     * @param filterText the raw contents of the Find field
     * @return the runs, in order, concatenating back to {@code name}. A single unmatched run when
     *         the filter is inactive or does not match.
     */
    public static List<Run> runs(String name, String filterText) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }
        if (!isActive(filterText)) {
            return List.of(new Run(name, false));
        }
        String needle = filterText.trim().toLowerCase(Locale.ROOT);
        String haystack = name.toLowerCase(Locale.ROOT);
        // Case folding is not always one character for one character: a handful of characters
        // lowercase to two. Every index past such a character would then point at the wrong
        // place in the original name, so we decline to highlight rather than bold the wrong
        // range. The row is still listed and still readable; only the emphasis is dropped.
        if (haystack.length() != name.length()) {
            return List.of(new Run(name, false));
        }
        List<Run> runs = new ArrayList<>();
        int cursor = 0;
        int found = haystack.indexOf(needle, cursor);
        while (found >= 0) {
            if (found > cursor) {
                runs.add(new Run(name.substring(cursor, found), false));
            }
            int end = found + needle.length();
            runs.add(new Run(name.substring(found, end), true));
            cursor = end;
            found = haystack.indexOf(needle, cursor);
        }
        if (runs.isEmpty()) {
            return List.of(new Run(name, false));
        }
        if (cursor < name.length()) {
            runs.add(new Run(name.substring(cursor), false));
        }
        return runs;
    }
}
