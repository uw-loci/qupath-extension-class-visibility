package qupath.ext.classvisibility.ui;

/**
 * One row of the component list.
 *
 * <p>{@code spread} is over <b>classes</b>, not objects: how many of this image's classes contain
 * this component. A near-universal component such as {@code positive} has the highest object
 * count by construction, so an object-count signal ranks the least discriminating component
 * first -- exactly backwards.</p>
 *
 * @param name the component name, as produced by QuPath's own class-name splitting
 * @param spread how many classes in this image contain it
 * @param classCount how many classes this image has in total -- the spread denominator
 * @param count how many objects in scope carry a class containing it
 */
public record ComponentRow(String name, int spread, int classCount, long count) {

    /** @return the fraction of this image's classes containing the component, in [0, 1]. */
    public double coverage() {
        return classCount <= 0 ? 0.0 : spread / (double) classCount;
    }
}
