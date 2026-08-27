package qupath.ext.classvisibility.core;

import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Harvest behaviour over real {@code PathObject}s: class folding, component derivation, and the
 * scope accessors.
 */
class ClassHarvesterTest {

    private static ROI roi() {
        return ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane());
    }

    private static PathObject detection(String... tokens) {
        PathObject object = PathObjects.createDetectionObject(roi());
        if (tokens.length > 0) {
            object.setPathClass(PathClass.fromCollection(List.of(tokens)));
        }
        return object;
    }

    private static PathObject annotation(String... tokens) {
        PathObject object = PathObjects.createAnnotationObject(roi());
        if (tokens.length > 0) {
            object.setPathClass(PathClass.fromCollection(List.of(tokens)));
        }
        return object;
    }

    @Test
    void harvestCountsClassesAndDerivesComponents() {
        ClassCensus census = ClassHarvester.harvest(List.of(
                detection("CD3", "CD8"),
                detection("CD3", "CD8"),
                detection("CD8"),
                detection("CD3")));
        assertThat(census.totalObjects()).isEqualTo(4);
        assertThat(census.classCount()).isEqualTo(3);
        assertThat(census.countForClass(PathClass.fromCollection(List.of("CD3", "CD8")))).isEqualTo(2);
        assertThat(census.countForComponent("CD8")).isEqualTo(3);
        assertThat(census.spreadForComponent("CD8")).isEqualTo(2);
    }

    @Test
    void unclassifiedObjectsFoldOntoOneRow() {
        ClassCensus census = ClassHarvester.harvest(List.of(
                detection(),
                detection(),
                detection("CD8")));
        assertThat(census.classes()).contains(PathClass.NULL_CLASS);
        assertThat(census.countForClass(PathClass.NULL_CLASS)).isEqualTo(2);
        assertThat(census.classCount()).isEqualTo(2);
    }

    @Test
    void harvestOfNothingIsTheEmptyCensus() {
        assertThat(ClassHarvester.harvest((List<PathObject>) null)).isSameAs(ClassCensus.EMPTY);
        assertThat(ClassHarvester.harvest(List.of())).isSameAs(ClassCensus.EMPTY);
        assertThat(ClassHarvester.harvest(null, ClassHarvester.Scope.DETECTIONS))
                .isSameAs(ClassCensus.EMPTY);
    }

    @Test
    void scopeSelectsTheRightObjectsAndNeverTheRootObject() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        hierarchy.addObject(detection("CD8"));
        hierarchy.addObject(detection("CD8"));
        hierarchy.addObject(annotation("Tumor"));

        assertThat(ClassHarvester.objectsInScope(hierarchy, ClassHarvester.Scope.DETECTIONS))
                .hasSize(2);
        assertThat(ClassHarvester.objectsInScope(hierarchy, ClassHarvester.Scope.ANNOTATIONS))
                .hasSize(1);
        // The root object is a real hierarchy member but is not a listable object, and counting
        // it would put a phantom Unclassified row in every image.
        assertThat(ClassHarvester.objectsInScope(hierarchy, ClassHarvester.Scope.ALL_OBJECTS))
                .hasSize(3)
                .noneMatch(PathObject::isRootObject);

        ClassCensus all = ClassHarvester.harvest(hierarchy, ClassHarvester.Scope.ALL_OBJECTS);
        assertThat(all.classes()).doesNotContain(PathClass.NULL_CLASS);
        assertThat(all.totalObjects()).isEqualTo(3);
    }

    @Test
    void scopeNounsAreUsableInsideASentence() {
        assertThat(ClassHarvester.Scope.DETECTIONS.displayName()).isEqualTo("Detections");
        assertThat(ClassHarvester.Scope.DETECTIONS.lowerCaseNoun()).isEqualTo("detections");
        assertThat(ClassHarvester.Scope.ALL_OBJECTS.displayName()).isEqualTo("All objects");
        assertThat(ClassHarvester.Scope.ALL_OBJECTS.lowerCaseNoun()).isEqualTo("objects");
    }
}
