package qupath.ext.classvisibility.core;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks an object hierarchy and counts the classes it carries, producing an immutable
 * {@link ClassCensus}.
 *
 * <p><b>Runs off the JavaFX application thread.</b> Two constraints follow from that, both
 * verified against QuPath 0.7 source:</p>
 * <ul>
 *   <li>Only the <i>unsynchronized</i> accessors are used --
 *       {@link PathObjectHierarchy#getDetectionObjects()},
 *       {@link PathObjectHierarchy#getCellObjects()},
 *       {@link PathObjectHierarchy#getAnnotationObjects()} and
 *       {@link PathObjectHierarchy#getObjects(Collection, Class)}. Never
 *       {@code getAllObjects(boolean)} or {@code getFlattenedObjectList(List)}: those are
 *       {@code synchronized} on the hierarchy, so a million-object walk would stall a running
 *       classifier for its whole duration rather than merely running alongside it.</li>
 *   <li>The walk descends through {@code PathObject.getChildObjectsAsArray()}, which takes a
 *       per-node atomic snapshot of a synchronized child set. So the walk cannot throw
 *       {@code ConcurrentModificationException}; it can return a torn view, which for a class
 *       census is harmless because the next debounced harvest supersedes it.</li>
 * </ul>
 *
 * <p>JavaFX-free by design, so it is unit-testable without a toolkit.</p>
 */
public final class ClassHarvester {

    /**
     * Which objects are listed and counted. This is a <b>list scope</b> only: it never changes
     * what is hidden. Hiding is driven by {@code OverlayOptions.selectedClasses}, one global
     * type-blind set, so a hidden class is hidden for cells, detections and annotations alike.
     */
    public enum Scope {

        /** Every detection, including cells and tiles (detection subclasses). The default. */
        DETECTIONS("Detections", "detections"),

        /** Cell objects only. */
        CELLS("Cells", "cells"),

        /** Annotation objects only. */
        ANNOTATIONS("Annotations", "annotations"),

        /** Everything in the hierarchy except the root object. */
        ALL_OBJECTS("All objects", "objects");

        private final String displayName;
        private final String lowerCaseNoun;

        Scope(String displayName, String lowerCaseNoun) {
            this.displayName = displayName;
            this.lowerCaseNoun = lowerCaseNoun;
        }

        /** @return the name shown in the List combo, e.g. {@code All objects}. */
        public String displayName() {
            return displayName;
        }

        /** @return the plural noun for use inside a sentence, e.g. {@code No detections in this image.} */
        public String lowerCaseNoun() {
            return lowerCaseNoun;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private ClassHarvester() {
        // Utility class.
    }

    /**
     * Collect the objects a scope covers. Separated from {@link #harvest(Collection)} so the
     * hierarchy access and the counting can be reasoned about (and tested) independently.
     *
     * @param hierarchy the hierarchy to walk; may be null
     * @param scope which objects to collect
     * @return the objects in scope, never null
     */
    public static Collection<PathObject> objectsInScope(PathObjectHierarchy hierarchy, Scope scope) {
        if (hierarchy == null) {
            return List.of();
        }
        switch (scope) {
            case CELLS:
                return hierarchy.getCellObjects();
            case ANNOTATIONS:
                return hierarchy.getAnnotationObjects();
            case ALL_OBJECTS:
                // getObjects(coll, null) adds the root object; the root is not a listable object
                // for our purposes, so it is filtered out here rather than counted as Unclassified.
                List<PathObject> all = new ArrayList<>();
                hierarchy.getObjects(all, PathObject.class);
                all.removeIf(PathObject::isRootObject);
                return all;
            case DETECTIONS:
            default:
                return hierarchy.getDetectionObjects();
        }
    }

    /**
     * Count classes over a hierarchy.
     *
     * @param hierarchy the hierarchy to walk; may be null, in which case the census is empty
     * @param scope which objects to count
     * @return the census
     */
    public static ClassCensus harvest(PathObjectHierarchy hierarchy, Scope scope) {
        return harvest(objectsInScope(hierarchy, scope));
    }

    /**
     * Count classes over an arbitrary object collection. Pure: no hierarchy, no threading, no
     * JavaFX.
     *
     * <p>The key is the object's own {@link PathClass} instance, which is what an exact rule
     * must later write back into {@code selectedClasses} -- {@code PathClass} is interned, and
     * reconstructing one from its token set is not guaranteed to return the same instance.
     * Components are derived from that same instance via {@code PathClass.toSet()} inside
     * {@link ClassCensus}, which is exactly what {@code PathObject.getClassifications()}
     * returns.</p>
     *
     * @param objects the objects to count; null is treated as empty
     * @return the census
     */
    public static ClassCensus harvest(Collection<? extends PathObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return ClassCensus.EMPTY;
        }
        Map<PathClass, Long> counts = new LinkedHashMap<>();
        for (PathObject pathObject : objects) {
            if (pathObject == null) {
                continue;
            }
            PathClass pathClass = pathObject.getPathClass();
            // Fold null and NULL_CLASS onto one Unclassified key. QuPath's own matcher checks
            // both sentinels, so either is a valid thing to write back into selectedClasses.
            PathClass key = pathClass == null ? PathClass.NULL_CLASS : pathClass;
            counts.merge(key, 1L, Long::sum);
        }
        return ClassCensus.of(counts);
    }
}
