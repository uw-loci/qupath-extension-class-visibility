package qupath.ext.classvisibility.core;

import org.junit.jupiter.api.Test;
import qupath.lib.objects.classes.PathClass;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Census arithmetic: class counts, component counts, and the spread that separates a
 * discriminating component from a near-universal one.
 */
class ClassCensusTest {

    private static PathClass pc(String... tokens) {
        return PathClass.fromCollection(java.util.List.of(tokens));
    }

    private static Map<PathClass, Long> counts(Object... pairs) {
        Map<PathClass, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((PathClass) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return map;
    }

    @Test
    void classCountsAreReportedVerbatim() {
        ClassCensus census = ClassCensus.of(counts(pc("CD3", "CD8"), 12, pc("CD8"), 5));
        assertThat(census.countForClass(pc("CD3", "CD8"))).isEqualTo(12);
        assertThat(census.countForClass(pc("CD8"))).isEqualTo(5);
        assertThat(census.totalObjects()).isEqualTo(17);
        assertThat(census.classCount()).isEqualTo(2);
    }

    @Test
    void componentCountsSumOverEveryClassContainingTheComponent() {
        ClassCensus census = ClassCensus.of(counts(pc("CD3", "CD8"), 12, pc("CD8"), 5, pc("CD3"), 3));
        assertThat(census.countForComponent("CD8")).isEqualTo(17);
        assertThat(census.countForComponent("CD3")).isEqualTo(15);
        assertThat(census.components()).containsExactlyInAnyOrder("CD3", "CD8");
    }

    @Test
    void spreadCountsClassesNotObjects() {
        // "positive" is in every class and carries almost every object; "Foxp3" is in one class
        // and carries very few. Ranking by object count would put the useless component first,
        // which is the whole reason spread exists.
        ClassCensus census = ClassCensus.of(counts(
                pc("CD3", "positive"), 40_000,
                pc("CD8", "positive"), 30_000,
                pc("CD68", "positive"), 20_000,
                pc("Foxp3", "positive"), 100));
        assertThat(census.spreadForComponent("positive")).isEqualTo(4);
        assertThat(census.spreadForComponent("Foxp3")).isEqualTo(1);
        assertThat(census.countForComponent("positive")).isEqualTo(90_100);
        assertThat(census.countForComponent("Foxp3")).isEqualTo(100);
        assertThat(census.coverageFraction("positive")).isEqualTo(1.0, within(1e-9));
        assertThat(census.coverageFraction("Foxp3")).isEqualTo(0.25, within(1e-9));
    }

    @Test
    void nullAndNullClassFoldOntoOneUnclassifiedKey() {
        Map<PathClass, Long> map = new LinkedHashMap<>();
        map.put(null, 7L);
        map.put(PathClass.NULL_CLASS, 3L);
        ClassCensus census = ClassCensus.of(map);
        assertThat(census.classes()).containsExactly(PathClass.NULL_CLASS);
        assertThat(census.countForClass(null)).isEqualTo(10);
        assertThat(census.countForClass(PathClass.NULL_CLASS)).isEqualTo(10);
        assertThat(census.classCount()).isEqualTo(1);
    }

    @Test
    void unclassifiedContributesNoComponents() {
        Map<PathClass, Long> map = new LinkedHashMap<>();
        map.put(PathClass.NULL_CLASS, 5L);
        map.put(pc("CD8"), 5L);
        ClassCensus census = ClassCensus.of(map);
        assertThat(census.components()).containsExactly("CD8");
        // The spread denominator is the class count the header shows, Unclassified included.
        assertThat(census.classCount()).isEqualTo(2);
        assertThat(census.coverageFraction("CD8")).isEqualTo(0.5, within(1e-9));
    }

    @Test
    void emptyCensusIsSafeToQuery() {
        assertThat(ClassCensus.EMPTY.isEmpty()).isTrue();
        assertThat(ClassCensus.EMPTY.classCount()).isZero();
        assertThat(ClassCensus.EMPTY.countForClass(pc("CD8"))).isZero();
        assertThat(ClassCensus.EMPTY.countForComponent("CD8")).isZero();
        assertThat(ClassCensus.EMPTY.coverageFraction("CD8")).isZero();
        assertThat(ClassCensus.of(null)).isSameAs(ClassCensus.EMPTY);
    }
}
