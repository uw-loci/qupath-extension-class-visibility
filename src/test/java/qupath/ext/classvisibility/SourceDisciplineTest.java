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

    /**
     * The restore has to be on the close paths and off the re-parenting ones.
     *
     * <p>Closing the panel now puts back everything the panel found when it opened (user,
     * 2026-08-28), and every dismissal route reaches that through one method: the window's close
     * button, <i>Hide panel</i>, the Extensions-menu toggle, the toolbar button and QuPath
     * quitting all call {@code closePanel()}. Docking and undocking must not -- they hide a Stage
     * and remove a Tab exactly as a close does, and a restore reached from there would throw away
     * the rules the user is in the middle of using, which is the most likely way to break
     * something that currently works.</p>
     *
     * <p>Which method a click reaches is a wiring fact, not a behavioural one: proving it at
     * runtime needs a JavaFX toolkit, a QuPath instance and a real analysis pane. So it is pinned
     * here, the same way the shutdown guard's event type is.</p>
     */
    @Test
    void theRestoreIsOnTheClosePathAndNotOnTheReparentingOnes() throws IOException {
        String extension = Files.readString(MAIN_SOURCES.resolve(Path.of("qupath", "ext",
                "classvisibility", "ClassVisibilityExtension.java")), StandardCharsets.UTF_8);

        assertThat(occurrences(extension, "restoreOpeningState()"))
                .as("the restore has exactly one call site, so no route can skip or double it")
                .isEqualTo(1);
        assertThat(methodBody(extension, "private synchronized void closePanel()"))
                .as("closePanel is the one place the panel's session is ended")
                .contains("restoreOpeningState()");

        // Both re-parenting operations hide a Stage or remove a Tab. Neither ends the session.
        assertThat(methodBody(extension, "private synchronized void dockAsTab()"))
                .as("docking moves a running panel; the user's rules must survive it")
                .doesNotContain("restoreOpeningState")
                .doesNotContain("closePanel");
        assertThat(methodBody(extension, "private synchronized void undockToWindow()"))
                .as("undocking moves a running panel; the user's rules must survive it")
                .doesNotContain("restoreOpeningState")
                .doesNotContain("closePanel");
        // The window-hidden handler fires for both, which is what the flag is for.
        assertThat(methodBody(extension, "private void attachToWindow()"))
                .as("a hide that is a re-parenting step must not be read as a close")
                .contains("if (!reparenting)");

        assertThat(methodBody(extension, "private void installShutdownGuard()"))
                .as("quitting with the panel open is a close, and the only one the panel "
                        + "cannot see for itself")
                .contains("closePanel()");
    }

    /**
     * The attention pulse has to be stopped everywhere it can outlive its point.
     *
     * <p>A {@code Timeline} running against a node the panel has finished with is a leak, and
     * this panel's whole lifecycle discipline is about not leaving things behind. Five routes end
     * it: clicking either radio (the user has noticed -- continuing to pulse at them is nagging),
     * the count dropping below two (covered behaviourally in {@code CombinationHintTest}), an
     * image switch, the panel leaving the screen -- which is what a dock or an undock looks like
     * from inside the Pane -- and {@code dispose()}.</p>
     *
     * <p>Animation needs a JavaFX toolkit, so which method holds which call is pinned here rather
     * than exercised.</p>
     */
    @Test
    void theAttentionPulseIsStoppedOnEveryRouteThatEndsIt() throws IOException {
        String pane = Files.readString(MAIN_SOURCES.resolve(Path.of("qupath", "ext",
                "classvisibility", "ui", "ClassVisibilityPane.java")), StandardCharsets.UTF_8);

        assertThat(occurrences(methodBody(pane, "private void wireControls()"),
                "stopCombinationHintPulse()"))
                .as("both radios cancel the pulse on click, selected one included")
                .isEqualTo(2);
        assertThat(methodBody(pane, "public void dispose()"))
                .as("a disposed panel must not leave a Timeline running")
                .contains("stopCombinationHintPulse()");
        assertThat(methodBody(pane, "public void onImageChanged(String imageName)"))
                .as("the components it was pointing at are about to be replaced")
                .contains("stopCombinationHintPulse()");
        assertThat(methodBody(pane, "public ClassVisibilityPane(QuPathGUI qupath)"))
                .as("going off screen -- a dock, an undock, a collapsed analysis pane -- ends it")
                .contains("stopCombinationHintPulse()");
    }

    /**
     * Rows whose children an {@code HBox} will shrink when the row does not fit.
     *
     * <p>{@code FlowPane} rows are absent on purpose: it lays children out at their preferred
     * size and wraps, so nothing in one is ever squeezed. {@code VBox} rows are absent for the
     * same reason in the other axis.</p>
     */
    private static final List<String> SHRINKABLE_ROWS = List.of(
            "imageRow", "presetRow", "exactWarningBox", "filterRow", "scopeRow", "findRow",
            "classButtonRow");

    /**
     * The controls that are <b>meant</b> to give up width, and what they do instead of vanishing.
     *
     * <p>{@code imageLabel} truncates with a centre ellipsis and keeps a tooltip with the full
     * name; {@code exactWarningLabel} wraps; the two combos and the find field are the row's
     * shock absorbers and stay usable at any width because their content is not their label.</p>
     */
    private static final List<String> DELIBERATE_ABSORBERS = List.of(
            "imageLabel", "exactWarningLabel", "presetCombo", "scopeCombo", "findField");

    /**
     * No control in a shrinkable row may be squeezed below its own text.
     *
     * <p>Phase 6 recorded that "every layout number is arithmetic that has never met a font", and
     * this is that bill arriving: a ComboBox measures its preferred width from its prompt text,
     * {@code (no presets saved in this project)} was long, the preset row's preferred width
     * exceeded the docked pane, and an HBox pays for that by shrinking every child toward its
     * minimum -- which for a Label or a Button is the ellipsis. The user saw
     * {@code ... [combo] ... ...} at a width they described as "already pretty wide". Widening
     * would not have fixed it.</p>
     *
     * <p>Rendering needs a toolkit; this does not. It reads which controls each shrinkable row
     * holds and requires every one of them to be either protected by {@code keepFullyReadable}
     * or a declared absorber -- so a control added to one of these rows later cannot quietly
     * inherit the defect.</p>
     */
    @Test
    void noControlInAShrinkableRowCanBeSqueezedBelowItsText() throws IOException {
        String pane = Files.readString(MAIN_SOURCES.resolve(Path.of("qupath", "ext",
                "classvisibility", "ui", "ClassVisibilityPane.java")), StandardCharsets.UTF_8);

        // Excluding a brace keeps this off the method's own declaration, whose body would
        // otherwise be swallowed by the argument-list group.
        Matcher protectedCall = Pattern.compile("keepFullyReadable\\(([^;{]*)\\);").matcher(pane);
        assertThat(protectedCall.find()).as("keepFullyReadable must be called").isTrue();
        Set<String> protectedControls = new LinkedHashSet<>(identifiers(protectedCall.group(1)));
        assertThat(protectedControls).as("controls protected from shrinking").isNotEmpty();

        Matcher rows = Pattern.compile(
                "(\\w+)\\.getChildren\\(\\)\\.(?:setAll|addAll)\\(([^;{]*)\\);").matcher(pane);
        List<String> violations = new ArrayList<>();
        int rowsSeen = 0;
        while (rows.find()) {
            String row = rows.group(1);
            if (!SHRINKABLE_ROWS.contains(row)) {
                continue;
            }
            rowsSeen++;
            for (String child : identifiers(rows.group(2))) {
                if (SHRINKABLE_ROWS.contains(child) || DELIBERATE_ABSORBERS.contains(child)
                        || protectedControls.contains(child)) {
                    continue;
                }
                violations.add(child + " in " + row);
            }
        }
        assertThat(rowsSeen).as("shrinkable rows found in the source").isNotZero();
        assertThat(violations)
                .as("controls in a shrinkable row that are neither protected by "
                        + "keepFullyReadable nor declared absorbers")
                .isEmpty();
    }

    /**
     * The preset combo must not size itself from its own text.
     *
     * <p>It is the control the whole row grows and shrinks around, so its preferred width has to
     * come from the layout. Leaving it computed is what let a transient empty-state prompt decide
     * how wide the row wanted to be.</p>
     */
    @Test
    void thePresetComboTakesItsPreferredWidthFromTheLayout() throws IOException {
        String pane = Files.readString(MAIN_SOURCES.resolve(Path.of("qupath", "ext",
                "classvisibility", "ui", "ClassVisibilityPane.java")), StandardCharsets.UTF_8);
        assertThat(pane).contains("presetCombo.setPrefWidth(PRESET_COMBO_WIDTH)");
        // Still the row's shock absorber: it gives up width first and takes the slack back.
        assertThat(pane).contains("presetCombo.setMaxWidth(Double.MAX_VALUE)");
        assertThat(pane).contains("HBox.setHgrow(presetCombo, Priority.ALWAYS)");
    }

    /** @return the simple identifiers in an argument list, ignoring literals and method calls. */
    private static List<String> identifiers(String argumentList) {
        List<String> found = new ArrayList<>();
        for (String argument : argumentList.split(",")) {
            String trimmed = argument.strip();
            if (trimmed.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                found.add(trimmed);
            }
        }
        return found;
    }

    /** @return how many times {@code needle} appears in {@code text}. */
    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    /**
     * @param source a Java source file
     * @param signature the method signature, up to and excluding the opening brace
     * @return the body of that method, with string literals blanked so a brace inside one -- the
     *         {@code "{}"} of every SLF4J call -- cannot unbalance the scan
     */
    private static String methodBody(String source, String signature) {
        String blanked = source.replaceAll("\\\\.", "..").replaceAll("\"(\\\\.|[^\"])*\"", "\"\"");
        int start = blanked.indexOf(signature);
        assertThat(start).as("method %s must exist", signature).isNotNegative();
        int open = blanked.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < blanked.length(); i++) {
            char c = blanked.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return blanked.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
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
