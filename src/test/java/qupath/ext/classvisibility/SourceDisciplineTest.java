package qupath.ext.classvisibility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            "resetDetectionClassifications",
            "setColor(",
            "setPathClasses(",
            "getAvailablePathClasses().add",
            "getAvailablePathClasses().setAll",
            "getAvailablePathClasses().remove",
            "getAvailablePathClasses().clear",
            "getMeasurementList(",
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
