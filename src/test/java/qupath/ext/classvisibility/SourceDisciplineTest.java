package qupath.ext.classvisibility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two whole-source checks that no unit test of behaviour can express.
 *
 * <p>Both scan {@code src/main/java} only. Test sources are deliberately exempt: building a
 * fixture object legitimately calls {@code setPathClass}, and that is the one place it is
 * allowed.</p>
 */
class SourceDisciplineTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /**
     * Calls that would make this panel destructive. It is a viewer for visibility state: nothing
     * in it may alter a classification, the project's class list, or object data.
     *
     * <p>{@code setColor(} is here because the only lever for a future per-group opacity feature
     * is {@code PathClass.setColor(Integer)} -- and because {@code PathClass} instances are
     * interned and global, that would be an edit to the user's colour scheme, persisted with the
     * project. It is a roadmap item with a snapshot requirement attached, not something to
     * acquire by accident.</p>
     */
    private static final List<String> FORBIDDEN_CALLS = List.of(
            ".setPathClass(",
            "::setPathClass",
            "resetDetectionClassifications",
            "setColor(",
            "::setColor",
            "setPathClasses(",
            "setClassification(",
            "::setClassification",
            "getAvailablePathClasses().add",
            "getAvailablePathClasses().setAll",
            "getAvailablePathClasses().remove",
            "getAvailablePathClasses().clear",
            "getMeasurementList(",
            ".setLocked(",
            ".addObject(",
            ".removeObject(",
            ".addObjects(",
            ".removeObjects(",
            "clearAll(",
            "syncChanges(",
            "saveImageData(",
            "qupath.lib.gui.dialogs.Dialogs");

    private static List<Path> javaSources() throws IOException {
        assertThat(Files.isDirectory(MAIN_SOURCES))
                .as("main sources at %s (tests run from the project directory)",
                        MAIN_SOURCES.toAbsolutePath())
                .isTrue();
        try (Stream<Path> stream = Files.walk(MAIN_SOURCES)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noSourceFileCallsADestructiveApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.stripLeading().startsWith("*") || line.stripLeading().startsWith("//")) {
                    continue;
                }
                for (String forbidden : FORBIDDEN_CALLS) {
                    if (line.contains(forbidden)) {
                        violations.add(file + ":" + (i + 1) + " -> " + forbidden);
                    }
                }
            }
        }
        assertThat(violations).as("destructive API calls in main sources").isEmpty();
    }

    /**
     * Every display string must be reachable, and every key asked for must exist.
     *
     * <p>An orphaned string reads, to anyone auditing the shipped vocabulary, as a feature that
     * exists -- and a menu built from a key nobody uses is indistinguishable from a menu whose
     * item silently does nothing. That is the same defect a user hit in 0.1.0, in a form a
     * reviewer can catch before a user does. The reverse direction is worse and cheaper: a key
     * asked for and not present throws {@code MissingResourceException} at runtime, on whichever
     * click first needs it.</p>
     */
    @Test
    void everyDisplayStringIsUsedAndEveryUsedKeyExists() throws IOException {
        Path bundle = Path.of("src", "main", "resources", "qupath", "ext", "classvisibility",
                "ui", "strings.properties");
        Set<String> declared = new LinkedHashSet<>();
        boolean continuation = false;
        for (String line : Files.readAllLines(bundle, StandardCharsets.UTF_8)) {
            boolean nextContinuation = line.endsWith("\\");
            if (!continuation) {
                String trimmed = line.strip();
                int eq = trimmed.indexOf('=');
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && eq > 0) {
                    declared.add(trimmed.substring(0, eq).strip());
                }
            }
            continuation = nextContinuation;
        }
        assertThat(declared).as("keys parsed from the bundle").isNotEmpty();

        Set<String> referenced = new LinkedHashSet<>();
        Pattern usage = Pattern.compile("Strings\\.(?:get|format)\\(\"([^\"]+)\"");
        for (Path file : javaSources()) {
            Matcher matcher = usage.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                referenced.add(matcher.group(1));
            }
        }

        assertThat(referenced).as("keys asked for but missing from strings.properties")
                .isSubsetOf(declared);
        assertThat(declared).as("strings declared but never shown to anyone")
                .isSubsetOf(referenced);
    }

    /**
     * The restart guard has to be registered on the event QuPath actually fires.
     *
     * <p>It was on {@code WINDOW_HIDING} through Phase 4, where it never ran once: QuPath's
     * {@code handleCloseMainStageRequest} runs the whole quit sequence inline from
     * {@code setOnCloseRequest} and finishes with {@code System.exit(0)}, so nothing ever hides
     * the main stage. It must also be a FILTER, not a handler, and on
     * {@code WINDOW_CLOSE_REQUEST}, because the same handler calls
     * {@code PathPrefs.savePreferences()} -- the guard has to have written before that runs
     * (finding B1). None of this is expressible as a behaviour test without a JavaFX toolkit and
     * a real QuPath stage, so it is pinned here, where a refactor that moves it back cannot pass
     * quietly.</p>
     */
    @Test
    void theRestartGuardIsAFilterOnTheCloseRequest() throws IOException {
        Path extension = MAIN_SOURCES.resolve(Path.of("qupath", "ext", "classvisibility",
                "ClassVisibilityExtension.java"));
        String text = Files.readString(extension, StandardCharsets.UTF_8);
        assertThat(text)
                .as("the guard must run in the capturing phase, ahead of QuPath's own "
                        + "onCloseRequest handler")
                .contains("stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST");
        assertThat(text)
                .as("WINDOW_HIDING never fires for QuPath's main stage")
                .doesNotContain("WindowEvent.WINDOW_HIDING");
    }

    @Test
    void everySourceFileIsPureAscii() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) > 127) {
                    violations.add(file + " contains a non-ASCII character at offset " + i);
                    break;
                }
            }
        }
        // Production runs on Windows with cp1252. Non-ASCII in logging, internal strings or
        // comments has hung workflows in this project more than once.
        assertThat(violations).as("non-ASCII characters in main sources").isEmpty();
    }
}
