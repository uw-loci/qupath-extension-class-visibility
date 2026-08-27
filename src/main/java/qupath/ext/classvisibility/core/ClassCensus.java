package qupath.ext.classvisibility.core;

import qupath.lib.objects.classes.PathClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable result of one class harvest over a QuPath object hierarchy.
 *
 * <p>Two views of the same objects:</p>
 * <ul>
 *   <li><b>Classes</b> -- each distinct {@link PathClass} carried by an object in scope,
 *       mapped to the number of objects carrying it. Objects with no classification are
 *       folded into a single {@link PathClass#NULL_CLASS} key, so {@code null} and
 *       {@code NULL_CLASS} can never produce two rows.</li>
 *   <li><b>Components</b> -- each single name part of those classes (QuPath's own
 *       splitting, via {@code PathObject.getClassifications()} / {@code PathClass.toSet()}),
 *       mapped both to an object count and to a <i>spread</i>: how many distinct classes
 *       in this census contain that component.</li>
 * </ul>
 *
 * <p><b>Spread is over classes, not objects</b> (design handoff note 11). A near-universal
 * component such as {@code positive} has the highest object count by construction, so an
 * object-count-based signal ranks the degenerate component first, which is exactly backwards.
 * The denominator reported alongside a spread is {@link #classCount()} -- the same number the
 * class-list header shows, Unclassified included. Unclassified carries no components, so a
 * spread of 100% is unreachable in an image that has unclassified objects; that is preferred
 * over a denominator that disagrees with the number on screen one line above.</p>
 *
 * <p>JavaFX-free by design, so it is unit-testable without a toolkit.</p>
 */
public final class ClassCensus {

    /** Census of nothing: no image, no objects in scope, or a harvest that has not run yet. */
    public static final ClassCensus EMPTY = new ClassCensus(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0L);

    private final Map<PathClass, Long> classCounts;
    private final Map<String, Long> componentObjectCounts;
    private final Map<String, Integer> componentClassSpread;
    private final long totalObjects;

    private ClassCensus(Map<PathClass, Long> classCounts,
                        Map<String, Long> componentObjectCounts,
                        Map<String, Integer> componentClassSpread,
                        long totalObjects) {
        this.classCounts = classCounts;
        this.componentObjectCounts = componentObjectCounts;
        this.componentClassSpread = componentClassSpread;
        this.totalObjects = totalObjects;
    }

    /**
     * Build a census from a class-to-object-count map. Component statistics are derived here,
     * once, rather than recomputed per row.
     *
     * @param counts class to object count. A {@code null} key is folded onto
     *               {@link PathClass#NULL_CLASS}; a caller may pass either sentinel.
     * @return the immutable census
     */
    public static ClassCensus of(Map<PathClass, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return EMPTY;
        }
        Map<PathClass, Long> classCounts = new LinkedHashMap<>();
        long total = 0L;
        for (Map.Entry<PathClass, Long> entry : counts.entrySet()) {
            PathClass key = entry.getKey() == null ? PathClass.NULL_CLASS : entry.getKey();
            long n = entry.getValue() == null ? 0L : entry.getValue();
            classCounts.merge(key, n, Long::sum);
            total += n;
        }
        Map<String, Long> componentObjects = new LinkedHashMap<>();
        Map<String, Integer> componentSpread = new LinkedHashMap<>();
        for (Map.Entry<PathClass, Long> entry : classCounts.entrySet()) {
            PathClass pathClass = entry.getKey();
            if (pathClass == PathClass.NULL_CLASS) {
                continue;
            }
            Set<String> components = pathClass.toSet();
            for (String component : components) {
                componentObjects.merge(component, entry.getValue(), Long::sum);
                componentSpread.merge(component, 1, Integer::sum);
            }
        }
        return new ClassCensus(
                Collections.unmodifiableMap(classCounts),
                Collections.unmodifiableMap(componentObjects),
                Collections.unmodifiableMap(componentSpread),
                total);
    }

    /** @return every class in this census, Unclassified present as {@link PathClass#NULL_CLASS}. */
    public Set<PathClass> classes() {
        return classCounts.keySet();
    }

    /** @return every component name found across the classes in this census. */
    public Set<String> components() {
        return componentObjectCounts.keySet();
    }

    /**
     * @param pathClass the class to count; {@code null} is treated as Unclassified
     * @return the number of objects in scope carrying that class, or 0
     */
    public long countForClass(PathClass pathClass) {
        PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
        return classCounts.getOrDefault(key, 0L);
    }

    /**
     * @param component the component name
     * @return the number of objects in scope whose class contains that component, or 0
     */
    public long countForComponent(String component) {
        return componentObjectCounts.getOrDefault(component, 0L);
    }

    /**
     * @param component the component name
     * @return how many distinct classes in this census contain that component
     */
    public int spreadForComponent(String component) {
        return componentClassSpread.getOrDefault(component, 0);
    }

    /** @return the number of distinct classes, Unclassified included -- the spread denominator. */
    public int classCount() {
        return classCounts.size();
    }

    /** @return the total number of objects in scope. */
    public long totalObjects() {
        return totalObjects;
    }

    /** @return true when no object in scope was seen. */
    public boolean isEmpty() {
        return classCounts.isEmpty();
    }

    /**
     * Fraction of this image's classes containing a component, in {@code [0, 1]}.
     *
     * @param component the component name
     * @return the coverage fraction, or 0 when the census is empty
     */
    public double coverageFraction(String component) {
        int denominator = classCount();
        if (denominator <= 0) {
            return 0.0;
        }
        return spreadForComponent(component) / (double) denominator;
    }
}
