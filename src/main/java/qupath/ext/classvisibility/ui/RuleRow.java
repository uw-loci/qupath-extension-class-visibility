package qupath.ext.classvisibility.ui;

import qupath.lib.objects.classes.PathClass;

/**
 * One row of the {@code Active rules} table.
 *
 * <p>This table exists because the panel's real state is a set of entries that may have no row in
 * either list: an {@code All} composite is generally not in the project's class list and so
 * appears nowhere in QuPath's built-in Classes pane; an entry for a class absent from the current
 * image is invisible but live; and QuPath's own Classes pane writes the same set, so some entries
 * are not ours at all.</p>
 *
 * @param entry the entry in {@code selectedClasses}
 * @param rule the entry's displayed name
 * @param source where it came from
 * @param status what it is doing right now
 */
public record RuleRow(PathClass entry, String rule, String source, String status) {
}
