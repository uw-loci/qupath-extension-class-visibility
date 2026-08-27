package qupath.ext.classvisibility.ui;

import qupath.lib.objects.classes.PathClass;

/**
 * One row of the class list.
 *
 * <p><b>No check state.</b> Whether the row's checkbox is ticked is derived from the live
 * {@code selectedClasses} set at render time, which is what makes image switches, QuPath's own
 * Classes-pane writes, and the undo path all correct without any synchronisation of ours.</p>
 *
 * @param pathClass the harvested {@link PathClass} instance -- the exact instance an exact rule
 *                  writes back, never a reconstruction
 * @param displayName the name to show; {@code Unclassified} for the null class
 * @param count how many objects in scope carry this class
 * @param present whether this class is actually used in the current image, as opposed to being
 *                listed from the project's class list with a count of zero
 */
public record ClassRow(PathClass pathClass, String displayName, long count, boolean present) {

    /** @return true when this is the folded null / NULL_CLASS row. */
    public boolean isUnclassified() {
        return pathClass == null || pathClass == PathClass.NULL_CLASS;
    }
}
