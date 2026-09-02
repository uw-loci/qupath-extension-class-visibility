package qupath.ext.classvisibility.ui;

import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.core.ClassCensus;
import qupath.ext.classvisibility.core.ClassHarvester;
import qupath.ext.classvisibility.core.ClassVisibilityController;
import qupath.ext.classvisibility.core.CombinationHint;
import qupath.ext.classvisibility.core.VisibilityPreset;
import qupath.ext.classvisibility.core.VisibilityPresetStore;
import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.ext.classvisibility.core.VisibilitySnapshot;
import qupath.ext.classvisibility.core.VisibilityStateStore;
import qupath.ext.classvisibility.preferences.ClassVisibilityPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ResourceManager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The whole Class Visibility user interface, as a self-contained {@link BorderPane}.
 *
 * <p><b>Surface-agnostic by construction, and that is load-bearing.</b> This pane holds no
 * {@code Stage}, no {@code Tab}, no modality and no window geometry, and it never asks which of
 * the three it is living in. That is what lets the panel move between a floating window
 * ({@link ClassVisibilityStage}) and a docked analysis-pane tab by <b>re-parenting the same
 * instance</b> -- so the user's rules, filter text, sort order and scroll position survive the
 * move, because nothing is rebuilt.</p>
 *
 * <p>Two profiles from one pane -- wide and side-by-side, or narrow and stacked -- switched on
 * the pane's <b>own</b> {@code widthProperty} with hysteresis. Driving it off the width rather
 * than off the surface is why a narrow floating window and a wide docked pane both come out
 * right, and why the layout does not thrash while a divider is dragged.</p>
 */
public final class ClassVisibilityPane extends BorderPane implements ClassVisibilityController.View {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityPane.class);

    /** Above this pane width, lay out wide (side-by-side). */
    /**
     * Preferred width of the preset combo. Any modest number will do; what matters is that the
     * number comes from the layout rather than from the longest string the combo has ever held.
     */
    private static final double PRESET_COMBO_WIDTH = 180;

    private static final double WIDE_THRESHOLD = 640;

    /** Below this pane width, lay out narrow (stacked). The gap is deliberate hysteresis. */
    private static final double NARROW_THRESHOLD = 580;

    /** Column id of the count column in both tables, so its header can be found after a reorder. */
    private static final String COUNT_COLUMN_ID = "countColumn";

    /** Coverage emphasis is suppressed entirely below this many classes. */
    private static final int COVERAGE_EMPHASIS_MIN_CLASSES = 5;

    /**
     * The panel's one spacing unit. Every inset, every row gap and every gap between zones is
     * this, or an explicit multiple of it.
     *
     * <p>Before 0.2.1 there were six values in play -- 2, 4, 6, 8, 12 and a bare 6 on the pane --
     * arrived at one control at a time, which is why the two list headers ended up sitting flush
     * against the bar above them while other seams had room to spare (user, 2026-09-01). The
     * header is being rebuilt in this release anyway, so the rhythm is settled here rather than
     * patched with a seventh number.</p>
     */
    private static final double GAP = 6;

    /**
     * The one deliberate exception: a caption sitting directly on the control it names, where a
     * full {@link #GAP} would read as a gap between two unrelated things rather than as a label.
     * Used by the <i>Visibility rule:</i> and <i>Checked components combine as:</i> groups only.
     */
    private static final double CAPTION_GAP = 2;

    /**
     * Gap between a class colour swatch and the name beside it. Inside a table cell rather than
     * in the panel's vertical rhythm, so it is its own number and is exempt from {@link #GAP}.
     */
    private static final double GRAPHIC_GAP = 4;

    /** Smallest useful height for either list, below which the table is all header and scrollbar. */
    private static final double TABLE_MIN_HEIGHT = 120;

    /**
     * The halo colour for the class table's header check control while every object is hidden.
     *
     * <p>Mid-blue at full saturation, chosen to sit clear of both QuPath themes: it is lighter
     * than the dark theme's controls and darker than the light theme's background, so the glow
     * reads on either without being tuned per theme. It is never the only signal -- the status
     * strip states the condition in words, the control's tooltip and accessible text say it too,
     * and the rules table's placeholder says it a third time.</p>
     */
    private static final Color EVERYTHING_HIDDEN_HALO_COLOR = Color.web("#3D8BFD");

    /**
     * The attention pulse on the Any / All group: three gentle swells over five seconds.
     *
     * <p><b>0.6 Hz, well under WCAG's three-flashes-per-second threshold</b>, and a swelling glow
     * rather than an on/off flip -- a literal blink is the usual accessibility failure of this
     * kind, and above three flashes a second it is a photosensitive-seizure risk rather than a
     * style choice. One half-cycle is {@code HINT_HALF_PERIOD}; {@code HINT_HALF_CYCLES} of them
     * with auto-reverse is 3 full pulses in 4.998 s, ending back at radius zero.</p>
     */
    private static final Duration HINT_HALF_PERIOD = Duration.millis(833);

    private static final int HINT_HALF_CYCLES = 6;
    private static final double HINT_GLOW_RADIUS = 14;
    private static final double HINT_GLOW_SPREAD = 0.55;

    private static final NumberFormat COUNTS = NumberFormat.getIntegerInstance();

    /** Measured height of one line of cell text, and the font size it was measured at. */
    private static double lineHeight;
    private static double lineHeightFontSize;

    /** The one-deep undo slot: everything needed to put the rules and the mode back. */
    private record UndoEntry(String actionLabel,
                             VisibilityRuleModel.ModelState state,
                             OverlayOptions.ClassVisibilityMode mode,
                             boolean useExact) {
    }

    private final QuPathGUI qupath;
    private final OverlayOptions options;
    private final ClassVisibilityController controller;
    private final VisibilityRuleModel model;

    private final SimpleStringProperty title = new SimpleStringProperty(Strings.get("pane.title.noImage"));

    private final ObservableList<ClassRow> classRows = FXCollections.observableArrayList();
    private final ObservableList<ComponentRow> componentRows = FXCollections.observableArrayList();
    private final ObservableList<RuleRow> ruleRows = FXCollections.observableArrayList();

    private final FilteredList<ClassRow> filteredClasses = new FilteredList<>(classRows);
    private final FilteredList<ComponentRow> filteredComponents = new FilteredList<>(componentRows);

    private final TableView<ClassRow> classTable = new TableView<>();
    private final TableView<ComponentRow> componentTable = new TableView<>();
    private final TableView<RuleRow> ruleTable = new TableView<>();

    private final Label imageLabel = new Label(Strings.get("label.image.none"));
    private final Label classHeader = new Label();
    private final Label componentHeader = new Label();
    private final Label statusLabel = new Label();
    private final Label combinationLabel = new Label(Strings.get("label.combination"));
    private final ProgressIndicator spinner = new ProgressIndicator();

    private final RadioButton hideRadio = new RadioButton(Strings.get("radio.hide"));
    private final RadioButton showOnlyRadio = new RadioButton(Strings.get("radio.showOnly"));
    private final RadioButton anyRadio = new RadioButton();
    /** The label and both radios as one visual group, which is what the hint pulse glows. */
    private VBox combinationBox;
    private final RadioButton allRadio = new RadioButton();
    private final CheckBox exactCheck = new CheckBox(Strings.get("check.exact"));
    private final CheckBox includeEmptyCheck = new CheckBox(Strings.get("check.includeEmpty"));
    private final ComboBox<ClassHarvester.Scope> scopeCombo = new ComboBox<>();
    /**
     * The class table's header check control: one tri-state box that checks or unchecks every
     * class the list is currently showing. It replaced two full-width buttons below the table
     * (external tester, 2026-09-01) and it respects the {@code Find} filter exactly as they did.
     */
    private final CheckBox headerCheckAll = new CheckBox();

    /** Named visibility presets stored in the project, and the two controls that manage them. */
    private final ComboBox<String> presetCombo = new ComboBox<>();
    private final Button presetSaveButton = new Button(Strings.get("button.presetSave"));
    private final Button presetDeleteButton = new Button(Strings.get("button.presetDelete"));
    private final TextField findField = new TextField();
    private final Button clearFindButton = new Button(Strings.get("button.findClear"));
    private final Button helpButton = new Button(Strings.get("button.help"));

    private final Button undoButton = new Button(Strings.get("button.undo"));
    private final Button resetButton = new Button(Strings.get("button.reset"));
    private final Button switchToHideButton = new Button(Strings.get("button.switchToHide"));
    private final Button clearRulesButton = new Button(Strings.get("button.clearAllRules"));
    /** Dock / undock. Its label and action are supplied by whichever surface currently holds the pane. */
    private final Button surfaceButton = new Button();

    private final Label modeLabel = new Label(Strings.get("label.mode"));
    /**
     * A pointer, not a control. The {@code Cell display} combo that used to sit here was the one
     * thing in the panel with nothing to do with classes, and it duplicated QuPath's own
     * {@code View -> Cell display}; this line is what remains of it, so a user whose cells look
     * wrong still knows where to go.
     */
    private final Label cellDisplayNote = new Label(Strings.get("note.cellDisplay"));
    /**
     * The standing "what is in force" sentence, inside the <i>Active rules</i> expander. Only the
     * everything-hidden warning stays on the always-visible strip; see {@link #updateStatus()}.
     */
    private final Label rulesStatusLabel = new Label();
    private final Label presetLabel = new Label(Strings.get("label.presets"));
    private final Label scopeLabel = new Label(Strings.get("label.scope"));
    private final Label findLabel = new Label(Strings.get("label.find"));
    private final HBox imageRow = new HBox(GAP);
    private final HBox presetRow = new HBox(GAP);
    /** One instance whose text follows whether a project is open. */
    private final Tooltip presetTooltip = new Tooltip(Strings.get("tooltip.presets"));
    private final HBox scopeRow = new HBox(GAP);
    private final HBox findRow = new HBox(GAP);
    private final HBox exactWarningBox = new HBox(GAP);
    private final HBox statusButtons = new HBox(GAP);
    /** The always-visible strip. Out of the layout entirely when it has nothing to say. */
    private final VBox statusBox = new VBox(GAP);
    private final VBox modeBox = new VBox(CAPTION_GAP);
    private final HBox modeRow = new HBox(GAP * 2);
    private final VBox filterBox = new VBox(GAP);
    private final HBox filterRow = new HBox(GAP);
    private final SplitPane splitPane = new SplitPane();
    private final VBox classPane = new VBox(GAP);
    private final VBox componentPane = new VBox(GAP);
    private final TitledPane rulesPane = new TitledPane();

    private ClassCensus census = ClassCensus.EMPTY;
    private String currentImageName;
    private boolean wideProfile = true;
    private boolean profileInitialised = false;
    private boolean harvesting = false;
    /** True only while the rows on screen belong to a different image, so no count is knowable. */
    private boolean countsUnknown = false;
    private boolean countsStale = false;
    private boolean updatingControls = false;
    /** True while the preset combo is being repopulated, so its selection listener stands down. */
    private boolean updatingPresets = false;
    /** True once the user has changed a rule; the panel's own opening state does not set it. */
    private boolean userChangedRules = false;

    /**
     * The state QuPath was in when this panel opened, replayed in full when it closes.
     *
     * <p>The panel is a session: it hides every object as it opens, and closing it puts the user
     * back exactly where they were (user, 2026-08-28). Without this, opening the panel silently
     * discarded whatever class rules the user already had -- recoverable only through a menu item
     * they had to know about first.</p>
     *
     * <p>The same instance {@link VisibilityStateStore} keeps for the session, so the panel's
     * replay and the on-demand <i>Restore the state from when the panel opened</i> can never
     * disagree about what "before" was.</p>
     */
    private VisibilitySnapshot openingSnapshot;

    /**
     * The halo drawn on the class table's header check control while every object is hidden. One
     * instance, one control: a JavaFX {@code Effect} is attached to a node, so it is not shared.
     */
    private final DropShadow everythingHiddenHalo = new DropShadow(BlurType.GAUSSIAN,
            EVERYTHING_HIDDEN_HALO_COLOR, 10, 0.6, 0, 0);

    /**
     * The pulsing glow for the Any / All group. The same blue as the halo above, deliberately:
     * one "look here" idiom in this panel rather than two competing ones. It starts at radius
     * zero, so it is invisible until the timeline swells it.
     */
    private final DropShadow combinationHintGlow = new DropShadow(BlurType.GAUSSIAN,
            EVERYTHING_HIDDEN_HALO_COLOR, 0, 0, 0, 0);

    /** Decides when that glow fires: once per session, on the crossing to two components. */
    private final CombinationHint combinationHint = new CombinationHint();

    /** The running pulse, or null. Held so every teardown path can stop it. */
    private Timeline combinationHintPulse;

    /** Held so {@link #dispose()} can detach them from the session-lived shared options. */
    private ChangeListener<OverlayOptions.ClassVisibilityMode> modeListener;
    private ChangeListener<Boolean> exactListener;
    /** Held for the same reason: QuPath's project property outlives every panel. */
    private ChangeListener<Project<BufferedImage>> projectListener;

    private UndoEntry undoSlot;
    private PathClass soloedClass;
    private String soloedComponent;
    private String coverageNote;

    /**
     * Build the panel and install its listeners.
     *
     * @param qupath the running QuPath instance
     */
    public ClassVisibilityPane(QuPathGUI qupath) {
        this.qupath = qupath;
        ClassVisibilityPreferences.installPreferences();
        this.controller = new ClassVisibilityController(qupath, this);
        this.options = controller.getOverlayOptions();
        this.model = new VisibilityRuleModel(options::selectedClassesProperty,
                showSelectedOnly -> options.setSelectedClassVisibilityMode(showSelectedOnly
                        ? OverlayOptions.ClassVisibilityMode.SHOW_SELECTED
                        : OverlayOptions.ClassVisibilityMode.HIDE_SELECTED));
        this.model.setChangeListener(this::onModelChanged);
        this.model.setCombination(ClassVisibilityPreferences.combinationProperty().get());

        buildUi();
        wireControls();
        wireKeyboard();

        controller.setScope(ClassVisibilityPreferences.scopeProperty().get());
        controller.install();
        // A dock, an undock, or a collapsed analysis pane takes the panel off screen mid-pulse.
        // Both re-parenting moves hide the surface the Pane is in, which is what this sees.
        visibleForUpdatesProperty().addListener((obs, wasVisible, isVisible) -> {
            if (!Boolean.TRUE.equals(isVisible)) {
                stopCombinationHintPulse();
            }
        });
        applyOpeningState();
        refreshRuleDependentUi();
    }

    /**
     * Hide everything, the moment the panel opens.
     *
     * <p>This is the workflow the ported script had and the one the user asked for back: opening
     * the panel clears the viewer, and the user checks their way to the populations they want to
     * see. It is the inverse of a filter you build up while looking at the data, and it is the
     * right way round for the multiplexed case -- with thirty overlapping classes on screen there
     * is nothing to see until most of them are gone.</p>
     *
     * <p>Three things make it safe rather than alarming. The snapshot is taken first, and it is
     * replayed in full when the panel closes -- so this is a door the user can walk back out of,
     * not a one-way trade of the rules they had for the ones they are about to set
     * ({@link #restoreOpeningState()}); the same snapshot also backs <i>Restore the state from
     * when the panel opened</i> while the panel is still up. The status strip says
     * <b>[!] Every object is hidden</b> in words the moment it happens, with <i>Switch to "Hide
     * checked classes"</i> and <i>Reset all</i> beside it, the rules table's placeholder says it
     * again, and the class table's header check control is haloed as the way back. And the close guard still runs after the restore, for the case where the
     * state being put back is itself the everything-hidden pair (finding R2).</p>
     */
    private void applyOpeningState() {
        // Replace the snapshot rather than keeping an older one, so "Restore the state from when
        // the panel opened" is literally true on the second opening as well as the first. The
        // returned instance is held here too: closing the panel replays it, and holding the one
        // object keeps the close replay and the on-demand restore from ever diverging.
        openingSnapshot = VisibilityStateStore.capture(options);
        beforeMutation();
        soloedClass = null;
        soloedComponent = null;
        applyOpeningState(options, model);
        userChangedRules = false;
    }

    /**
     * The opening state, as a static so it can be verified against a real {@link OverlayOptions}
     * without a QuPath instance or a JavaFX toolkit.
     *
     * <p>Rules first, mode second, and the order is not arbitrary. Flipping the mode while old
     * rules are still in the set shows <i>only those classes</i> for a frame -- a view the user
     * never asked for, built from rules they may not remember setting. Clearing first passes
     * through "everything visible" instead, which is the state the panel is about to leave.</p>
     *
     * @param options the options to write
     * @param model the rule model to clear
     */
    public static void applyOpeningState(OverlayOptions options, VisibilityRuleModel model) {
        model.clearAllRules();
        options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
    }

    /** @return the pane title, tracking the current image. Used for the tab tooltip. */
    public ReadOnlyStringProperty titleProperty() {
        return title;
    }

    /**
     * @return whether the panel is on screen and worth updating. The extension binds this to
     *         QuPath's own tab-visibility idiom plus the collapsed-analysis-pane term.
     */
    public BooleanProperty visibleForUpdatesProperty() {
        return controller.paneVisibleProperty();
    }

    /**
     * Supply the dock / undock control shown at the top right of the panel.
     *
     * <p>The pane deliberately does not know which surface it is in, so the surface tells it what
     * the control should say and do. The same action is on the toolbar button's context menu; it
     * is duplicated here because a user who never right-clicks a toolbar button would otherwise
     * never find out that docking exists.</p>
     *
     * @param text the button label
     * @param tooltip the button tooltip
     * @param action what the button does
     */
    public void setSurfaceToggle(String text, String tooltip, Runnable action) {
        surfaceButton.setText(text);
        surfaceButton.setTooltip(new Tooltip(tooltip));
        surfaceButton.setAccessibleText(tooltip);
        surfaceButton.setOnAction(e -> action.run());
        surfaceButton.setVisible(true);
        surfaceButton.setManaged(true);
    }

    /** Hide the dock / undock control, for a surface in which neither move is meaningful. */
    public void hideSurfaceToggle() {
        surfaceButton.setVisible(false);
        surfaceButton.setManaged(false);
    }

    /**
     * Put the caret in the {@code Find} field. At the design centre of 20-40 combinatorial class
     * names that field is the primary navigation control, not a convenience, so it takes focus
     * when the panel is first revealed.
     */
    public void focusFind() {
        findField.requestFocus();
    }

    /**
     * Detach every listener. Called when the tab is removed and at QuPath shutdown.
     *
     * <p>The three detachments below are not tidiness. {@code OverlayOptions.getSharedInstance()}
     * lives for the whole QuPath session, so anything of ours still listening to it keeps this
     * pane -- its tables, its rows, its census -- reachable forever. The panel is now opened and
     * closed routinely rather than once (it hides everything on open, so closing it is a normal
     * move), and each cycle builds a new pane, so a retained one is a leak per cycle rather than
     * a curiosity.</p>
     */
    public void dispose() {
        stopCombinationHintPulse();
        controller.uninstall();
        if (modeListener != null) {
            options.selectedClassVisibilityModeProperty().removeListener(modeListener);
            modeListener = null;
        }
        if (exactListener != null) {
            options.useExactSelectedClassesProperty().removeListener(exactListener);
            exactListener = null;
        }
        if (projectListener != null) {
            qupath.projectProperty().removeListener(projectListener);
            projectListener = null;
        }
    }

    /**
     * Put back everything the panel found when it opened. Called from every close path.
     *
     * <p><b>The panel is a session</b> (user, 2026-08-28). It hides every object the moment it
     * opens, and until 0.1.1 that discarded whatever class rules the user already had -- a
     * one-way door out of a view they may have spent minutes building, with the way back on a
     * menu item they had to know existed. Closing the panel now restores the whole snapshot:
     * rules, mode, exact flag, object predicate, opacity, cell display mode and the per-type
     * show/fill booleans. QuPath ends up exactly where the user left it before pressing the
     * button.</p>
     *
     * <p><b>Not called when the panel is re-parented.</b> Docking and undocking move the same
     * panel between surfaces; the session continues and the user's rules must survive the move.
     * That is why this lives on the close path in
     * {@code ClassVisibilityExtension.closePanel()} and nowhere else.</p>
     *
     * @return the outcome, which decides what the caller tells the user
     */
    public RestoreOutcome restoreOpeningState() {
        return restoreQuietly(openingSnapshot, options);
    }

    /**
     * Whether the class rules in force right now are the ones the panel opened onto.
     *
     * @return true when the rule set, mode and exact flag all match the opening snapshot
     */
    public boolean matchesOpeningState() {
        return openingSnapshot != null && openingSnapshot.matchesRules(options);
    }

    /**
     * Replay a snapshot without ever throwing, as a static so it can be tested against options
     * that fail mid-restore.
     *
     * <p>This runs during QuPath's quit sequence, inside an event filter that fires before
     * {@code PathPrefs.savePreferences()}. An exception escaping here would propagate into
     * QuPath's close handling and could refuse the user their quit -- so a failed restore is a
     * logged, reported failure, never a thrown one. Same discipline as the shutdown
     * notification, and for the same reason.</p>
     *
     * @param snapshot the snapshot to replay; may be null
     * @param options the options to write
     * @return {@code RESTORED}, {@code FAILED}, or {@code NOTHING_TO_RESTORE} when there was no
     *         snapshot to replay
     */
    static RestoreOutcome restoreQuietly(VisibilitySnapshot snapshot, OverlayOptions options) {
        if (snapshot == null) {
            // Only reachable if the panel's own constructor did not finish, which is why it is
            // reported rather than treated as "nothing to do": the caller has to know that the
            // user was NOT put back.
            logger.warn("Class visibility: no opening snapshot to restore on close");
            return RestoreOutcome.NOTHING_TO_RESTORE;
        }
        try {
            snapshot.restore(options);
            logger.info("Restored the visibility state the Class visibility panel opened onto");
            return RestoreOutcome.RESTORED;
        } catch (RuntimeException ex) {
            logger.warn("Could not restore the visibility state the panel opened onto: {}",
                    ex.getMessage(), ex);
            return RestoreOutcome.FAILED;
        }
    }

    /** What {@link #restoreOpeningState()} managed to do. */
    public enum RestoreOutcome {
        /** The snapshot was replayed. */
        RESTORED,
        /** A snapshot existed and replaying it threw; the user is somewhere they did not choose. */
        FAILED,
        /** There was no snapshot -- the panel never finished opening. */
        NOTHING_TO_RESTORE
    }

    /**
     * The ratified R2 guard. If the mode is "show only checked classes" and nothing is checked,
     * every object in every image is hidden -- and because QuPath persists the mode but not the
     * set, that state comes back at the next launch with the panel closed and no visible cause.
     *
     * <p>Fires when the panel closes and at QuPath shutdown only, never while the panel is
     * installed: while it is installed the state is one click from visible and the user may be
     * one click from checking the class they were reaching for.</p>
     *
     * <p><b>Still needed after the close restore, in three cases.</b> Closing the panel now
     * replays the opening snapshot, so our own opening state can no longer be what is left
     * behind -- but the guard can still fire on (a) a user who already had "show only checked
     * classes" with nothing checked before they opened the panel, set from QuPath's own class
     * list, which the restore faithfully puts back; (b) a restore that failed or had no
     * snapshot; and (c) the startup reconciliation after a crash, where no close of ours ever
     * ran. Cases (a) and (c) are the ones that would otherwise come back as an empty viewer at
     * the next launch, and neither is reachable by the restore.</p>
     *
     * @return true when the guard changed the mode, so the caller can say so
     */
    public boolean applyCloseGuard() {
        return applyCloseGuard(options);
    }

    /**
     * The R2 guard, as a static so QuPath shutdown can run it without a live panel.
     *
     * @param options the options to guard
     * @return true when the mode was changed
     */
    public static boolean applyCloseGuard(OverlayOptions options) {
        if (options.getSelectedClassVisibilityMode() == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED
                && options.selectedClassesProperty().isEmpty()) {
            options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
            logger.info("Class visibility guard: reset SHOW_SELECTED with an empty rule set to HIDE_SELECTED");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------------------------------

    /**
     * Refuse to be squeezed below the text. A control whose whole job is to be readable -- a
     * fixed label, a button with a verb on it -- has no useful state narrower than its own words:
     * {@code Save} at its minimum width is {@code ...}, which names nothing and clicks the same.
     *
     * <p>{@code HBox} resolves an over-wide row by shrinking children toward their minimums, and
     * a Label's or Button's computed minimum is the ellipsis. Pinning the minimum to the
     * preferred size takes these controls out of that negotiation, so the shortfall lands on the
     * controls that can absorb it -- the combos, the find field, the image label -- instead of
     * being spread evenly over everything, which is how one over-wide combo turned an entire row
     * into dots (user, 2026-08-28).</p>
     *
     * <p>Do not pass a wrapping control: a {@code USE_PREF_SIZE} minimum asks for the whole text
     * on one line, which is the opposite of wrapping.</p>
     *
     * @param controls the controls to protect
     */
    private static void keepFullyReadable(Region... controls) {
        for (Region control : controls) {
            control.setMinWidth(Region.USE_PREF_SIZE);
        }
    }

    private void buildUi() {
        setPadding(new Insets(GAP));

        // Centre ellipsis, not the default trailing one: image names in a project share long
        // prefixes and differ near the end, so a trailing ellipsis truncates away the only part
        // that identifies the image -- and a truncated safeguard is not a safeguard.
        imageLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        imageLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imageLabel, Priority.ALWAYS);
        surfaceButton.setVisible(false);
        surfaceButton.setManaged(false);
        // Help is on the Extensions menu and the toolbar button's context menu, neither of which
        // is reachable from a docked panel without leaving it (finding N4).
        helpButton.setTooltip(new Tooltip(Strings.get("tooltip.button.help")));
        helpButton.setAccessibleText(Strings.get("tooltip.button.help"));
        helpButton.setOnAction(e -> showHelpDialog());
        imageRow.getChildren().setAll(imageLabel, helpButton, surfaceButton);
        imageRow.setAlignment(Pos.CENTER_LEFT);

        presetLabel.setLabelFor(presetCombo);
        // A ComboBox measures its preferred width from its contents, prompt text included -- so
        // an empty preset list with a long prompt made this row ask for more width than the pane
        // had, and an HBox pays for that by shrinking its children toward their MINIMUM widths,
        // which for a Label or a Button is the ellipsis. The user saw a whole line of "..." at a
        // width that was not remotely tight. A fixed preferred width takes the measurement away
        // from whatever text happens to be in the control; max and HGrow are unchanged, so it
        // still absorbs the slack, and its minimum stays computed, so it is also the control that
        // gives up width first when the row really is tight.
        presetCombo.setPrefWidth(PRESET_COMBO_WIDTH);
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setTooltip(presetTooltip);
        presetCombo.setAccessibleText(Strings.get("label.presets"));
        presetSaveButton.setTooltip(new Tooltip(Strings.get("tooltip.button.presetSave")));
        presetDeleteButton.setTooltip(new Tooltip(Strings.get("tooltip.button.presetDelete")));
        HBox.setHgrow(presetCombo, Priority.ALWAYS);
        // Contents are set per profile: the wide one puts "List:" on the end of this row.
        presetRow.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup modeGroup = new ToggleGroup();
        hideRadio.setToggleGroup(modeGroup);
        showOnlyRadio.setToggleGroup(modeGroup);
        hideRadio.setTooltip(new Tooltip(Strings.get("tooltip.radio.hide")));
        showOnlyRadio.setTooltip(new Tooltip(Strings.get("tooltip.radio.showOnly")));
        exactCheck.setTooltip(new Tooltip(Strings.get("tooltip.check.exact")));

        modeRow.setAlignment(Pos.CENTER_LEFT);

        Button turnOffExact = new Button(Strings.get("button.turnOff"));
        turnOffExact.setTooltip(new Tooltip(Strings.get("tooltip.button.turnOff")));
        turnOffExact.setOnAction(e -> exactCheck.setSelected(false));
        Label exactWarningLabel = new Label(Strings.get("status.exactWarning"));
        exactWarningLabel.setWrapText(true);
        HBox.setHgrow(exactWarningLabel, Priority.ALWAYS);
        exactWarningBox.getChildren().addAll(exactWarningLabel, turnOffExact);
        exactWarningBox.setAlignment(Pos.CENTER_LEFT);
        exactWarningBox.setPadding(new Insets(GAP, 0, GAP, 0));
        exactWarningBox.visibleProperty().bind(exactCheck.selectedProperty());
        exactWarningBox.managedProperty().bind(exactCheck.selectedProperty());

        scopeCombo.getItems().setAll(ClassHarvester.Scope.values());
        scopeCombo.setTooltip(new Tooltip(Strings.get("tooltip.scope")));


        cellDisplayNote.setWrapText(true);
        findField.setPromptText(Strings.get("prompt.find"));
        findField.setTooltip(new Tooltip(Strings.get("tooltip.find")));
        HBox.setHgrow(findField, Priority.ALWAYS);
        clearFindButton.setTooltip(new Tooltip(Strings.get("tooltip.findClear")));
        // A one-character label is not an accessible name, and with the field already empty the
        // button has nothing to clear -- disabled beats a click that appears to be ignored.
        clearFindButton.setAccessibleText(Strings.get("tooltip.findClear"));
        clearFindButton.disableProperty().bind(findField.textProperty().isEmpty());
        clearFindButton.setOnAction(e -> {
            findField.clear();
            findField.requestFocus();
        });
        findRow.setAlignment(Pos.CENTER_LEFT);
        scopeRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // Every control in a horizontally shrinkable row whose label IS the control. Two groups
        // are deliberately absent. The wrapping ones -- exactWarningLabel, includeEmptyCheck,
        // cellDisplayNote, the Any / All radios -- because a USE_PREF_SIZE minimum asks for the
        // whole text on one line, which is the opposite of wrapping. And the absorbers --
        // imageLabel (centre ellipsis plus a tooltip, by design), the scope combo, the find field
        // -- because giving up width is their job.
        //
        // Two joined in 0.2.1, when the header collapsed onto two rows.
        //
        // exactCheck, because it moved onto the Find row: it does not wrap, its label is the
        // whole control, and it is the widest fixed thing in the panel's tightest row --
        // unprotected, it is the first control an over-subscribed Find row would turn into "...".
        //
        // scopeCombo, because it STOPPED being an absorber. It shared the Find row's slack with
        // the find field until now; on the preset row it would share it with the preset combo,
        // and the two are not the same kind of control. This one has three fixed short values and
        // is exactly as readable at its preferred 127px as at 400, so every pixel it absorbed
        // would come off a user-authored preset name that genuinely can be long. Pinning it also
        // gives the merged row a computable floor, which is what makes the narrow profile's
        // stacking decision a measurement rather than a guess.
        keepFullyReadable(helpButton, surfaceButton,
                presetLabel, presetSaveButton, presetDeleteButton,
                turnOffExact, exactCheck,
                scopeLabel, scopeCombo, findLabel, clearFindButton);

        // exactWarningBox now sits BELOW filterBox, because since 0.2.1 the checkbox it is
        // explaining lives in that row. A warning rendered above the control it refers to reads
        // as being about the radios instead.
        VBox header = new VBox(GAP, imageRow, presetRow, modeBox, cellDisplayNote, filterBox,
                exactWarningBox);
        header.setPadding(new Insets(0, 0, GAP, 0));
        setTop(header);

        buildClassTable();
        buildComponentTable();
        buildRulesPane();

        splitPane.getItems().addAll(classPane, componentPane);
        setCenter(splitPane);

        statusLabel.setWrapText(true);
        spinner.setPrefSize(16, 16);
        spinner.setVisible(false);
        spinner.setManaged(false);
        undoButton.setTooltip(new Tooltip(Strings.get("tooltip.button.undo")));
        resetButton.setTooltip(new Tooltip(Strings.get("tooltip.button.reset")));
        switchToHideButton.setTooltip(new Tooltip(Strings.get("tooltip.button.switchToHide")));
        statusButtons.setAlignment(Pos.CENTER_LEFT);
        HBox statusRow = new HBox(GAP, spinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusBox.getChildren().setAll(statusRow, statusButtons);
        statusBox.setPadding(new Insets(GAP, 0, 0, 0));

        VBox bottom = new VBox(GAP, rulesPane, statusBox);
        setBottom(bottom);

        widthProperty().addListener((obs, oldValue, newValue) -> applyProfile(newValue.doubleValue()));
        applyWideProfile();
    }

    /**
     * Assemble one list pane: its header label, its table, and whatever sits under the table.
     *
     * <p><b>One factory because there are two of these and they are built by parallel code.</b>
     * The class list and the component list had their header label, their table's minimum height
     * and their {@code Vgrow} set independently in two methods, which is how the space above a
     * header comes to be right in one list and wrong in the other. It was wrong in both: the two
     * headers sat flush against the control above them, with the class header touching the Find
     * field in the wide profile and the component header touching the split divider in the narrow
     * one (user, 2026-09-01).</p>
     *
     * <p>The breathing room is {@link #GAP} above the label, on top of whatever the container
     * above already contributes -- so nothing here has to know which profile it is in or what is
     * sitting above it.</p>
     *
     * @param pane the pane to fill
     * @param header the list's header label
     * @param table the list's table
     * @param footer the controls under the table
     */
    private static void buildListPane(VBox pane, Label header, TableView<?> table, Node footer) {
        header.setPadding(new Insets(GAP, 0, 0, 0));
        // Wrapping, because both headers are sentences that name the list scope and its counts,
        // and a docked pane is narrower than either of them.
        header.setWrapText(true);
        table.setMinHeight(TABLE_MIN_HEIGHT);
        VBox.setVgrow(table, Priority.ALWAYS);
        pane.getChildren().setAll(header, table, footer);
    }

    private void buildClassTable() {
        TableColumn<ClassRow, ClassRow> checkColumn = new TableColumn<>("");
        checkColumn.setPrefWidth(30);
        checkColumn.setMinWidth(30);
        checkColumn.setMaxWidth(30);
        checkColumn.setSortable(false);
        checkColumn.setReorderable(false);
        checkColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        checkColumn.setCellFactory(col -> new ClassCheckCell());
        // The two full-width buttons this replaces sat under the table and said what they did in
        // words. A bare box in a header says nothing at all, so the tooltip and the accessible
        // text carry the whole meaning -- and updateCheckAllState keeps both current.
        // setSelected does not fire an ActionEvent, so the state sync below cannot re-enter here.
        headerCheckAll.setOnAction(e -> {
            List<PathClass> listed = filteredClasses.stream().map(ClassRow::pathClass).toList();
            if (headerCheckAll.isSelected()) {
                pushUndo(Strings.get("action.checkAllListed"));
                model.checkClasses(listed);
            } else {
                pushUndo(Strings.get("action.uncheckAllListed"));
                model.uncheckClasses(listed);
            }
        });
        checkColumn.setGraphic(headerCheckAll);
        keepColumnVisible(checkColumn);

        TableColumn<ClassRow, ClassRow> nameColumn = new TableColumn<>();
        nameColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        nameColumn.setCellFactory(col -> new ClassNameCell());
        nameColumn.setComparator(Comparator.comparing(ClassRow::displayName, String.CASE_INSENSITIVE_ORDER));
        nameAndExplain(nameColumn, Strings.get("column.class"), Strings.get("tooltip.column.class"));

        TableColumn<ClassRow, ClassRow> countColumn = new TableColumn<>();
        countColumn.setPrefWidth(84);
        countColumn.setMinWidth(60);
        countColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        countColumn.setCellFactory(col -> new CountCell<>(ClassRow::count));
        countColumn.setComparator(Comparator.comparingLong(ClassRow::count));
        countColumn.setId(COUNT_COLUMN_ID);
        nameAndExplain(countColumn, Strings.get("column.count"), Strings.get("tooltip.column.count"));
        // Off by default. Affects is the number that answers "what will this click do", so with
        // both columns on screen the class table showed two numbers where one of them is a trap
        // (external tester, 2026-09-01: "too many columns"). Count stays one click away in the
        // table's own menu button, and the Affects tooltip names it as the comparison.
        countColumn.setVisible(false);

        // The truth about the click. The Count column answers "how many objects carry this exact
        // class"; with "Exact matches only" off -- the shipped default -- a click on the row acts
        // on every class containing all of this one's parts as well, which on a combinatorial
        // panel is routinely several times the number in Count. Finding S1: a count shown beside
        // a control that acts on a different number is the one thing a counting UI must not do.
        TableColumn<ClassRow, ClassRow> affectsColumn = new TableColumn<>();
        affectsColumn.setPrefWidth(84);
        affectsColumn.setMinWidth(60);
        affectsColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        affectsColumn.setCellFactory(col -> new AffectsCell());
        affectsColumn.setComparator(Comparator.comparingLong(
                (ClassRow row) -> affectedObjects(row.pathClass())));
        nameAndExplain(affectsColumn, Strings.get("column.affects"), Strings.get("tooltip.column.affects"));

        // No "Only" column. It cost 52px of a column in which "FoxP3 (Opal 570): 1+: ..." was
        // already being cut off, to save one click -- and once checking a row means "show this"
        // rather than "hide this", solo is no longer a different KIND of operation, just a faster
        // one. It survives as a double-click, a right-click item and the O key.
        classTable.getColumns().setAll(List.of(checkColumn, nameColumn, countColumn,
                affectsColumn));
        installSoloGestures(classTable, ClassRow::displayName, this::soloClass);
        // Affects is no longer dropped at narrow widths. It was, back when the class table
        // carried four columns and the names had nothing left; with Count hidden by default the
        // pressure is halved, and this is the number the whole S1 correction rests on -- hiding
        // it to save 84px would take away the one thing on the row that says what the click does.
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        classTable.setTableMenuButtonVisible(true);
        installHeaderTooltips(classTable);

        SortedList<ClassRow> sorted = new SortedList<>(filteredClasses);
        classTable.setItems(sorted);
        // A custom sort policy rather than binding the SortedList comparator to the table's.
        // The class list has an ordering the user's column choice sits INSIDE -- classes the
        // image does not use never interleave with ones it does, and Unclassified always sorts
        // last because it is a category, not a class. Binding a derived comparator instead makes
        // TableView's default policy see two different comparators, log
        // "the SortedList comparator should be bound to the TableView comparator", and refuse to
        // sort at all. Found in a WSL smoke run, not by reading the docs.
        classTable.setSortPolicy(table -> {
            sorted.setComparator(classOrdering(table.getComparator()));
            return true;
        });
        // Default sort: with 28 classes the ones worth acting on are the populous ones, and an
        // alphabetical sort buries a long derived name among its near-identical siblings. On
        // Affects rather than Count since 0.2.0, because Count is now hidden by default and a
        // table sorted by a column nobody can see is a table sorted for no stated reason.
        affectsColumn.setSortType(TableColumn.SortType.DESCENDING);
        classTable.getSortOrder().add(affectsColumn);

        // A sentence rather than a control name, and the longest string in the panel. It wraps
        // instead of being pinned to its preferred width -- pinning it would make the whole class
        // pane refuse to be narrower than that one line, and wrapping is what the Any / All radios
        // already do with their long labels.
        includeEmptyCheck.setWrapText(true);
        includeEmptyCheck.setTooltip(new Tooltip(Strings.get("tooltip.check.includeEmpty")));

        buildListPane(classPane, classHeader, classTable, includeEmptyCheck);
    }

    private void buildComponentTable() {
        TableColumn<ComponentRow, ComponentRow> checkColumn = new TableColumn<>("");
        checkColumn.setPrefWidth(30);
        checkColumn.setMinWidth(30);
        checkColumn.setMaxWidth(30);
        checkColumn.setSortable(false);
        checkColumn.setReorderable(false);
        checkColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        checkColumn.setCellFactory(col -> new ComponentCheckCell());
        keepColumnVisible(checkColumn);

        TableColumn<ComponentRow, ComponentRow> nameColumn = new TableColumn<>();
        nameColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        nameColumn.setCellFactory(col -> new ComponentNameCell());
        nameColumn.setComparator(Comparator.comparing(ComponentRow::name, String.CASE_INSENSITIVE_ORDER));
        nameAndExplain(nameColumn, Strings.get("column.component"), Strings.get("tooltip.column.component"));

        TableColumn<ComponentRow, ComponentRow> spreadColumn = new TableColumn<>();
        spreadColumn.setPrefWidth(60);
        spreadColumn.setMinWidth(50);
        spreadColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        spreadColumn.setCellFactory(col -> new SpreadCell());
        spreadColumn.setComparator(Comparator.comparingDouble(ComponentRow::coverage));
        nameAndExplain(spreadColumn, Strings.get("column.spread"), Strings.get("tooltip.column.spread"));
        spreadColumn.setId("spreadColumn");
        // Off by default, and no longer width-driven. Spread is a diagnostic about a component's
        // discriminating power, not the answer to "what will this click do" -- and the SpreadCell
        // tooltip plus the swamping note in the status strip already carry that warning at the
        // point of the click. Recoverable from the table's menu button.
        spreadColumn.setVisible(false);

        TableColumn<ComponentRow, ComponentRow> countColumn = new TableColumn<>();
        countColumn.setPrefWidth(84);
        countColumn.setMinWidth(60);
        countColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        countColumn.setCellFactory(col -> new CountCell<>(ComponentRow::count));
        countColumn.setComparator(Comparator.comparingLong(ComponentRow::count));
        countColumn.setId(COUNT_COLUMN_ID);
        nameAndExplain(countColumn, Strings.get("column.count"), Strings.get("tooltip.column.count"));

        componentTable.getColumns().setAll(List.of(checkColumn, nameColumn, spreadColumn, countColumn));
        installSoloGestures(componentTable, ComponentRow::name, this::soloComponent);
        componentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        componentTable.setTableMenuButtonVisible(true);
        installHeaderTooltips(componentTable);

        SortedList<ComponentRow> sorted = new SortedList<>(filteredComponents);
        sorted.comparatorProperty().bind(componentTable.comparatorProperty());
        componentTable.setItems(sorted);
        // Default sort: alphabetical by component name. Spread-ascending was the first choice --
        // discriminating power first -- but at the design centre every real marker sits in the
        // middle of the spread distribution, so ascending order ranks them arbitrarily and only
        // reliably promotes one-offs and typos. The bold spread ratio already warns about a
        // degenerate component at the point of the click, which is where that warning belongs;
        // the default sort is better spent on the other job, looking a marker up (finding S12).
        nameColumn.setSortType(TableColumn.SortType.ASCENDING);
        componentTable.getSortOrder().add(nameColumn);

        ToggleGroup combinationGroup = new ToggleGroup();
        anyRadio.setToggleGroup(combinationGroup);
        allRadio.setToggleGroup(combinationGroup);
        anyRadio.setTooltip(new Tooltip(Strings.get("tooltip.combination.any")));
        allRadio.setTooltip(new Tooltip(Strings.get("tooltip.combination.all")));
        anyRadio.setWrapText(true);
        allRadio.setWrapText(true);
        combinationBox = new VBox(CAPTION_GAP, combinationLabel, anyRadio, allRadio);

        buildListPane(componentPane, componentHeader, componentTable, combinationBox);
    }

    private void buildRulesPane() {
        TableColumn<RuleRow, String> ruleColumn = new TableColumn<>(Strings.get("rules.column.rule"));
        ruleColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().rule()));
        TableColumn<RuleRow, String> sourceColumn = new TableColumn<>(Strings.get("rules.column.source"));
        sourceColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().source()));
        TableColumn<RuleRow, String> statusColumn = new TableColumn<>(Strings.get("rules.column.status"));
        statusColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status()));
        statusColumn.setCellFactory(col -> {
            TableCell<RuleRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            Label wrapping = new Label();
            wrapping.setWrapText(true);
            wrapping.textProperty().bind(cell.itemProperty());
            cell.setGraphic(wrapping);
            cell.setText(null);
            return cell;
        });
        TableColumn<RuleRow, RuleRow> actionColumn = new TableColumn<>(Strings.get("rules.column.action"));
        actionColumn.setPrefWidth(90);
        actionColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        actionColumn.setCellFactory(col -> new RemoveCell());

        ruleTable.getColumns().setAll(List.of(ruleColumn, sourceColumn, statusColumn, actionColumn));
        ruleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ruleTable.setItems(ruleRows);
        ruleTable.setPrefHeight(140);
        // Set here only so the table is never placeholder-less before the first render;
        // updateRuleTable owns it from then on, because with "Show only checked classes" on and
        // nothing checked -- the state the panel OPENS in -- "every object is visible" is the
        // exact opposite of what the viewer is showing (external tester, 2026-09-01).
        updateRulesPlaceholder();
        // The rules table is the reproducibility surface: it is the only place that states, for
        // the record, what is hiding things -- including rules for classes absent from this image.
        // Rules are deliberately not persisted, so without this the only way out was retyping it
        // off the screen (finding S11).
        MenuItem copyItem = new MenuItem(Strings.get("menu.rules.copy"));
        copyItem.setOnAction(e -> copyRulesToClipboard());
        ruleTable.setContextMenu(new ContextMenu(copyItem));
        KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        ruleTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (copyCombo.match(event)) {
                copyRulesToClipboard();
                event.consume();
            }
        });

        clearRulesButton.setTooltip(new Tooltip(Strings.get("tooltip.button.clearAllRules")));
        HBox rulesTop = new HBox(GAP, clearRulesButton);
        rulesTop.setAlignment(Pos.CENTER_RIGHT);
        rulesTop.setPadding(new Insets(0, 0, GAP, 0));
        // The routine "N rules active" sentence lives here rather than on the always-visible
        // strip (external tester, 2026-09-01: "put inside drop down"). The everything-hidden
        // warning does NOT -- see updateStatus.
        rulesStatusLabel.setWrapText(true);
        VBox rulesContent = new VBox(GAP, rulesStatusLabel, rulesTop, ruleTable);

        rulesPane.setText(Strings.get("rules.none"));
        rulesPane.setContent(rulesContent);
        rulesPane.setTooltip(new Tooltip(Strings.get("tooltip.rules.expander")));
        rulesPane.setExpanded(ClassVisibilityPreferences.rulesExpandedProperty().get());
        rulesPane.expandedProperty().bindBidirectional(ClassVisibilityPreferences.rulesExpandedProperty());
    }

    private void applyProfile(double width) {
        if (width <= 0) {
            return;
        }
        if (!profileInitialised) {
            profileInitialised = true;
            wideProfile = width >= WIDE_THRESHOLD;
            applyProfileLayout();
        } else if (wideProfile && width < NARROW_THRESHOLD) {
            wideProfile = false;
            applyProfileLayout();
        } else if (!wideProfile && width >= WIDE_THRESHOLD) {
            wideProfile = true;
            applyProfileLayout();
        }
    }

    /**
     * Keep a column out of the reach of the table's own menu button.
     *
     * <p>JavaFX offers no way to leave a column out of that menu, and finding N11 recorded that
     * the checkbox column can be hidden from it -- which since 0.2.0 would also take the
     * check-all control in its header. Two other columns are now hidden by default and the menu
     * is the only way back to them, so the menu is load-bearing rather than incidental: it has to
     * stay usable, and it has to stay unable to remove the one column every row is acted on
     * through. Re-showing on the next pulse is the only lever available.</p>
     *
     * @param column the column that must always be on screen
     */
    private static void keepColumnVisible(TableColumn<?, ?> column) {
        column.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (!Boolean.TRUE.equals(isVisible)) {
                column.setVisible(true);
            }
        });
    }

    /**
     * Name a column and give its header a hover explanation.
     *
     * <p><b>The name has to be the column's own {@code text}, and until 0.2.0 none of them was.</b>
     * A {@code TableColumn}'s text carries no tooltip, so every header worth explaining was built
     * as a {@code Label} graphic with the text blanked -- which works in the header and is
     * invisible to the table's menu button, because {@code TableViewSkinBase} binds each menu
     * item to {@code column.textProperty()} and nothing else. Every entry in both menus was a
     * blank row. That was survivable while every column was on screen anyway; it is not now that
     * Count and Spread are off by default and the menu is the only way back to them.</p>
     *
     * <p>So the text goes back on the column, where the menu can read it, and the tooltip goes
     * onto the header node itself once the skin has built one -- see
     * {@link #installHeaderTooltips(TableView)}.</p>
     *
     * @param column the column to name
     * @param text the header text, which is also what the table's menu button will show
     * @param tooltipText the hover explanation, parked on the column until its header exists
     */
    private static void nameAndExplain(TableColumn<?, ?> column, String text, String tooltipText) {
        column.setText(text);
        column.setUserData(tooltipText);
    }

    /**
     * Attach the parked header tooltips, once the table has a skin and therefore column headers.
     *
     * <p>{@code TableColumn.getStyleableNode()} is the supported way to reach a header, and it
     * returns null until the skin exists -- so this waits for one, and waits a pulse longer,
     * because the headers are built during the skin's own layout. It re-runs when a column is
     * shown or hidden as well: the header row rebuilds its children then, and a tooltip installed
     * on a discarded header would be lost silently, which is exactly the sort of quiet
     * degradation the menu is now load-bearing enough to notice.</p>
     *
     * @param table the table whose columns were named by {@link #nameAndExplain}
     */
    private static void installHeaderTooltips(TableView<?> table) {
        for (TableColumn<?, ?> column : table.getColumns()) {
            column.visibleProperty().addListener(
                    (obs, was, is) -> Platform.runLater(() -> applyHeaderTooltips(table)));
        }
        if (table.getSkin() != null) {
            Platform.runLater(() -> applyHeaderTooltips(table));
            return;
        }
        table.skinProperty().addListener(new ChangeListener<Skin<?>>() {
            @Override
            public void changed(ObservableValue<? extends Skin<?>> obs, Skin<?> was, Skin<?> is) {
                if (is != null) {
                    table.skinProperty().removeListener(this);
                    Platform.runLater(() -> applyHeaderTooltips(table));
                }
            }
        });
    }

    private static void applyHeaderTooltips(TableView<?> table) {
        for (TableColumn<?, ?> column : table.getColumns()) {
            if (!(column.getUserData() instanceof String text)) {
                continue;
            }
            Node header = column.getStyleableNode();
            if (header == null) {
                continue;
            }
            Tooltip tooltip = new Tooltip(text);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(340);
            Tooltip.install(header, tooltip);
        }
    }

    /**
     * @param pathClass a class, or null for Unclassified
     * @return how many objects a rule for that class would hide or show, under the matching mode
     *         in force right now
     */
    private long affectedObjects(PathClass pathClass) {
        return census.matchedObjectsForClass(pathClass, options.getUseExactSelectedClasses());
    }

    /** Put every active rule on the clipboard, one per line, with its source and status. */
    private void copyRulesToClipboard() {
        if (ruleRows.isEmpty()) {
            return;
        }
        String text = ruleRows.stream()
                .map(row -> row.rule() + "\t" + row.source() + "\t" + row.status())
                .collect(Collectors.joining(System.lineSeparator()));
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        logger.info("Copied {} class visibility rule(s) to the clipboard", ruleRows.size());
    }

    /**
     * Show the panel's help text. One implementation, called from the panel's own {@code ?}
     * button, the Extensions menu and the toolbar button's context menu.
     */
    public static void showHelpDialog() {
        Dialogs.showMessageDialog(Strings.get("help.title"), Strings.get("help.body"));
    }

    private void applyProfileLayout() {
        if (wideProfile) {
            applyWideProfile();
        } else {
            applyNarrowProfile();
        }
        updateHeaders();
    }

    private void applyWideProfile() {
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(ClassVisibilityPreferences.wideDividerProperty().get());

        modeRow.getChildren().setAll(hideRadio, showOnlyRadio);
        modeBox.getChildren().setAll(modeLabel, modeRow);

        // Two rows, and the point of the second one is where the slack goes rather than the row
        // count. "List:" moves up beside the presets (user, 2026-09-01: "that bar is far too long
        // anyway") -- the preset combo had HGrow.ALWAYS and nothing to its right, so on a wide
        // panel it stretched into an enormous empty dropdown for no benefit. Measured at an 800px
        // panel, that combo goes from 458px to 298px and the find field takes what it gave up.
        //
        // Nothing on either row can be squeezed below its own text: probes of both rows with the
        // real strings hold every fixed control at its width down to a 240px panel, and the only
        // things that ever give are the preset combo and the find field, which is their job.
        scopeRow.getChildren().setAll(scopeLabel, scopeCombo);
        presetRow.getChildren().setAll(presetLabel, presetCombo, presetSaveButton,
                presetDeleteButton, scopeRow);
        findRow.getChildren().setAll(findLabel, findField, clearFindButton, exactCheck);
        filterRow.getChildren().setAll(findRow);
        HBox.setHgrow(findRow, Priority.ALWAYS);
        filterBox.getChildren().setAll(filterRow);
    }

    private void applyNarrowProfile() {
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(ClassVisibilityPreferences.narrowDividerProperty().get());

        // Stacked, not hidden. Every zone survives the narrow profile; the only concessions are
        // stacking and ellipsis-with-tooltip on long names.
        modeRow.getChildren().clear();
        modeBox.getChildren().setAll(modeLabel, hideRadio, showOnlyRadio);

        // Stacked, not on the Find row, and the number is measured rather than guessed. The
        // checkbox is 152px that never shrinks, so in an HBox the find field pays for all of it:
        // a probe of this exact row gives the field 98px at a 320px pane and 18px at 240px,
        // against a 170px preferred. An 18px find field is not a narrower control, it is a gone
        // one -- and Find is this panel's primary navigation at thirty near-identical class
        // names, which is why focusFind() exists at all. Below about 390px the row cannot hold
        // both, and the narrow profile runs to 580px, so it stacks throughout it.
        //
        // This costs nothing here: exactCheck had its own line in the narrow profile before the
        // move too, under the radios. It has simply moved down to sit with the filter controls
        // and directly above the warning it raises.
        //
        // "List:" stays off the preset row here for the same measured reason. That merged row's
        // floor is 386px -- the four preset controls at their own widths plus the scope combo's
        // 127px -- and below it the row does not ellipsise, it OVERFLOWS: "List:" and its combo
        // are pushed past the right edge of the panel and simply are not there. Silently absent
        // is worse than truncated. The narrow profile runs to 580px, so it stacks throughout.
        presetRow.getChildren().setAll(presetLabel, presetCombo, presetSaveButton,
                presetDeleteButton);
        filterRow.getChildren().clear();
        scopeRow.getChildren().setAll(scopeLabel, scopeCombo);
        findRow.getChildren().setAll(findLabel, findField, clearFindButton);
        filterBox.getChildren().setAll(scopeRow, findRow, exactCheck);
    }

    // ------------------------------------------------------------------------------------------
    // Control wiring
    // ------------------------------------------------------------------------------------------

    private void wireControls() {
        // The mode and the exact flag are QuPath-wide persistent properties. We read and reflect
        // them; we never keep a second copy, and we never set them without a user action.
        hideRadio.setSelected(options.getSelectedClassVisibilityMode()
                == OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
        showOnlyRadio.setSelected(!hideRadio.isSelected());
        hideRadio.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingControls) {
                return;
            }
            beforeMutation();
            options.setSelectedClassVisibilityMode(Boolean.TRUE.equals(newValue)
                    ? OverlayOptions.ClassVisibilityMode.HIDE_SELECTED
                    : OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
        });
        modeListener = (obs, oldValue, newValue) -> {
            updatingControls = true;
            try {
                hideRadio.setSelected(newValue == OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
                showOnlyRadio.setSelected(newValue == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
            } finally {
                updatingControls = false;
            }
            refreshRuleDependentUi();
        };
        options.selectedClassVisibilityModeProperty().addListener(modeListener);

        exactCheck.setSelected(options.getUseExactSelectedClasses());
        exactCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingControls) {
                beforeMutation();
                options.setUseExactSelectedClasses(Boolean.TRUE.equals(newValue));
            }
            componentPane.setDisable(Boolean.TRUE.equals(newValue));
            refreshRuleDependentUi();
        });
        exactListener = (obs, oldValue, newValue) -> {
            updatingControls = true;
            try {
                exactCheck.setSelected(Boolean.TRUE.equals(newValue));
            } finally {
                updatingControls = false;
            }
        };
        options.useExactSelectedClassesProperty().addListener(exactListener);
        componentPane.setDisable(exactCheck.isSelected());

        scopeCombo.getSelectionModel().select(ClassVisibilityPreferences.scopeProperty().get());
        scopeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                ClassVisibilityPreferences.scopeProperty().set(newValue);
                controller.setScope(newValue);
            }
        });

        includeEmptyCheck.selectedProperty().bindBidirectional(
                ClassVisibilityPreferences.includeEmptyClassesProperty());
        includeEmptyCheck.selectedProperty().addListener((obs, oldValue, newValue) -> rebuildRows());

        findField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter(newValue));

        wirePresets();

        // The stop is on the interaction, not inside setCombination: clicking the radio that is
        // already selected changes nothing but still means the user has noticed, and continuing
        // to pulse at somebody who has already acted is nagging.
        anyRadio.setOnAction(e -> {
            stopCombinationHintPulse();
            setCombination(VisibilityRuleModel.Combination.ANY);
        });
        allRadio.setOnAction(e -> {
            stopCombinationHintPulse();
            setCombination(VisibilityRuleModel.Combination.ALL);
        });

        clearRulesButton.setOnAction(e -> {
            pushUndo(Strings.get("action.clearAllRules"));
            model.clearAllRules();
        });
        undoButton.setOnAction(e -> undo());
        resetButton.setOnAction(e -> resetAll());
        switchToHideButton.setOnAction(e -> {
            beforeMutation();
            options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
        });

        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> {
            if (wideProfile) {
                ClassVisibilityPreferences.wideDividerProperty().set(newValue.doubleValue());
            } else {
                ClassVisibilityPreferences.narrowDividerProperty().set(newValue.doubleValue());
            }
        });
    }

    private void wireKeyboard() {
        // Ctrl+Z is deliberately NOT bound here. QuPath binds shortcut+Z to Edit > Undo as a
        // scene accelerator, so the same keystroke meant two different things depending on state
        // the user cannot see: with our slot armed it undid a visibility change and swallowed the
        // key, and with it empty the key bubbled to QuPath and reverted the last HIERARCHY action
        // -- object data changing under a panel that advertises itself as never touching object
        // data. Undo stays a labelled button that names the action it will undo (findings S3, m8).
        KeyCombination find = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (find.match(event)) {
                findField.requestFocus();
                findField.selectAll();
                event.consume();
            }
        });
        findField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Escape clears the filter and keeps focus. It must not close anything: this is a
            // docked pane, not a dialog, and Escape removing the tab would be a trap.
            if (event.getCode() == KeyCode.ESCAPE && !findField.getText().isEmpty()) {
                findField.clear();
                event.consume();
            }
        });
        classTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            ClassRow row = classTable.getSelectionModel().getSelectedItem();
            if (row == null) {
                return;
            }
            if (event.getCode() == KeyCode.SPACE) {
                toggleClass(row, !model.isClassSelected(row.pathClass()));
                event.consume();
            } else if (event.getCode() == KeyCode.O) {
                soloClass(row);
                event.consume();
            }
        });
        componentTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            ComponentRow row = componentTable.getSelectionModel().getSelectedItem();
            if (row == null) {
                return;
            }
            if (event.getCode() == KeyCode.SPACE) {
                toggleComponent(row, !model.isComponentSelected(row.name()));
                event.consume();
            } else if (event.getCode() == KeyCode.O) {
                soloComponent(row);
                event.consume();
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------------------------

    /**
     * Called before anything in this panel changes overlay state. Takes the automatic session
     * snapshot if none exists yet, so <i>Restore visibility state</i> always has somewhere to go
     * back to without the user having thought ahead.
     */
    private void beforeMutation() {
        // The near-universal-component note describes the action the user just took, so it is
        // cleared by the next one rather than lingering over an unrelated state.
        coverageNote = null;
        userChangedRules = true;
        VisibilityStateStore.captureIfAbsent(options);
    }

    /**
     * @return whether the user has changed a rule since the panel opened. The opening state --
     *         everything hidden, nothing checked -- does not count, and that distinction is what
     *         keeps the close guard's notification meaningful. Opening the panel and closing it
     *         again without checking anything now ends in exactly the state the guard exists to
     *         clear, so announcing the reset every time would train the user to dismiss the one
     *         notification that matters when it fires over a setting they made themselves.
     */
    public boolean hasUserChanges() {
        return userChangedRules;
    }

    private void pushUndo(String actionLabel) {
        beforeMutation();
        undoSlot = new UndoEntry(actionLabel, model.captureState(),
                options.getSelectedClassVisibilityMode(), options.getUseExactSelectedClasses());
    }

    private void undo() {
        if (undoSlot == null) {
            return;
        }
        UndoEntry entry = undoSlot;
        undoSlot = null;
        soloedClass = null;
        soloedComponent = null;
        options.setSelectedClassVisibilityMode(entry.mode());
        options.setUseExactSelectedClasses(entry.useExact());
        model.restoreState(entry.state());
    }

    private void resetAll() {
        pushUndo(Strings.get("action.resetAll"));
        soloedClass = null;
        soloedComponent = null;
        // Mirrors QuPath's own restoreClassVisibilityDefaults(): mode, exact flag and set, in
        // that order. Not two of the three -- "Reset selected classes" clears only the set, and
        // on its own that is precisely what walks a user into the everything-hidden state.
        options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
        options.setUseExactSelectedClasses(false);
        model.clearAllRules();
    }

    private void setCombination(VisibilityRuleModel.Combination combination) {
        if (model.getCombination() == combination) {
            return;
        }
        beforeMutation();
        ClassVisibilityPreferences.combinationProperty().set(combination);
        model.setCombination(combination);
    }

    private void toggleClass(ClassRow row, boolean selected) {
        // Single-row toggles push undo as well as bulk actions. They did not before, which made
        // the slot say "Undo Check all listed" long after fifteen individual corrections had been
        // made on top of it -- and undoing then silently discarded all fifteen (finding S3).
        pushUndo(selected
                ? Strings.format("action.check", row.displayName())
                : Strings.format("action.uncheck", row.displayName()));
        soloedClass = null;
        model.setClassSelected(row.pathClass(), selected);
    }

    private void toggleComponent(ComponentRow row, boolean selected) {
        pushUndo(selected
                ? Strings.format("action.check", row.name())
                : Strings.format("action.uncheck", row.name()));
        soloedComponent = null;
        model.setComponentSelected(row.name(), selected);
        if (selected) {
            noteCoverageIfSwamping(row);
            updateStatus();
        }
    }

    /**
     * Give a table the three routes to solo that replaced the {@code Only} column.
     *
     * <p><b>Double-click</b> is the primary gesture -- isolate-on-double-click is what a table
     * row invites. <b>Right-click</b> carries the discoverability the column was providing: a
     * gesture nobody can see is not a feature, and the menu item names the row it will act on so
     * the meaning of "only" needs no explaining. The <b>O</b> key still works, wired separately
     * with the rest of the keyboard.</p>
     *
     * <p>The double-click handler ignores clicks that land on a checkbox. Without that, a
     * double-click on the check column would toggle the rule twice AND solo the row, which is
     * three state changes from one gesture.</p>
     *
     * @param table the table to wire
     * @param namer how to name a row in the menu item
     * @param solo what to do with the row
     * @param <T> the row type
     */
    private <T> void installSoloGestures(TableView<T> table, java.util.function.Function<T, String> namer,
                                         java.util.function.Consumer<T> solo) {
        table.setRowFactory(view -> {
            TableRow<T> row = new TableRow<>();
            MenuItem soloItem = new MenuItem();
            soloItem.setOnAction(e -> {
                if (row.getItem() != null) {
                    solo.accept(row.getItem());
                }
            });
            ContextMenu menu = new ContextMenu(soloItem);
            row.itemProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue == null) {
                    row.setContextMenu(null);
                } else {
                    soloItem.setText(Strings.format("action.showOnly", namer.apply(newValue)));
                    row.setContextMenu(menu);
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null
                        && !isInsideCheckBox(event.getTarget())) {
                    solo.accept(row.getItem());
                    event.consume();
                }
            });
            return row;
        });
    }

    /**
     * @param target the click target
     * @return whether it is a checkbox or inside one. A double-click there is two toggles, and
     *         the user meant the toggle, not an isolate.
     */
    private static boolean isInsideCheckBox(Object target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null) {
            if (node instanceof CheckBox) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Named presets, stored in the project
    // ------------------------------------------------------------------------------------------

    /**
     * Wire the preset combo and its two buttons.
     *
     * <p>Selecting a preset applies it immediately, which is what the Brightness &amp; Contrast
     * settings combo does and what a list of named views is for. It goes through
     * {@link #pushUndo(String)} like every other bulk change, so a preset applied by accident is
     * one click from being undone.</p>
     */
    private void wirePresets() {
        presetCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingPresets) {
                return;
            }
            presetDeleteButton.setDisable(newValue == null);
            if (newValue != null) {
                applyPreset(newValue);
            }
        });
        presetSaveButton.setOnAction(e -> promptToSavePreset());
        presetDeleteButton.setOnAction(e -> promptToDeletePreset());
        // The project can change under an open panel, and its presets change with it.
        projectListener = (obs, oldValue, newValue) -> refreshPresets();
        qupath.projectProperty().addListener(projectListener);
        refreshPresets();
    }

    /**
     * Re-read the preset names from the project and re-enable the controls to match.
     *
     * <p>The combo is <b>not</b> disabled when a project has none saved -- JavaFX shows no
     * tooltip on a disabled control, so the state that most needs explaining would be the one
     * that cannot explain itself. It says so in its prompt text instead, which is always
     * visible.</p>
     */
    private void refreshPresets() {
        Project<BufferedImage> project = qupath.getProject();
        boolean hasProject = VisibilityPresetStore.managerFor(project) != null;
        List<String> names = VisibilityPresetStore.names(project);
        updatingPresets = true;
        try {
            String selected = presetCombo.getSelectionModel().getSelectedItem();
            presetCombo.getItems().setAll(names);
            if (selected != null && names.contains(selected)) {
                presetCombo.getSelectionModel().select(selected);
            } else {
                presetCombo.getSelectionModel().clearSelection();
            }
        } finally {
            updatingPresets = false;
        }
        presetCombo.setPromptText(hasProject
                ? (names.isEmpty() ? Strings.get("prompt.presets.none") : Strings.get("prompt.presets"))
                : Strings.get("prompt.presets.noProject"));
        presetSaveButton.setDisable(!hasProject);
        presetDeleteButton.setDisable(!hasProject
                || presetCombo.getSelectionModel().getSelectedItem() == null);
        // One Tooltip whose text changes, not a new one installed per refresh: installing
        // repeatedly stacks behaviours on the same node. The buttons are disabled without a
        // project and JavaFX shows no tooltip on a disabled control, so the reason lives on the
        // combo, which stays enabled, and in its prompt text.
        presetTooltip.setText(hasProject
                ? Strings.get("tooltip.presets")
                : Strings.get("tooltip.presets.noProject"));
    }

    private void applyPreset(String name) {
        ResourceManager.Manager<VisibilityPreset> manager =
                VisibilityPresetStore.managerFor(qupath.getProject());
        if (manager == null) {
            return;
        }
        try {
            VisibilityPreset preset = manager.get(name);
            pushUndo(Strings.format("action.applyPreset", name));
            soloedClass = null;
            soloedComponent = null;
            preset.restore(options, model);
            logger.info("Applied class visibility preset '{}'", name);
        } catch (IOException | RuntimeException ex) {
            // A preset hand-edited into invalid JSON is a plausible failure, and it must not take
            // the panel with it.
            logger.error("Could not read visibility preset '{}': {}", name, ex.getMessage(), ex);
            Dialogs.showErrorMessage(Strings.get("notify.title"),
                    Strings.format("error.presetLoad", name));
        }
    }

    private void promptToSavePreset() {
        ResourceManager.Manager<VisibilityPreset> manager =
                VisibilityPresetStore.managerFor(qupath.getProject());
        if (manager == null) {
            return;
        }
        String suggested = presetCombo.getSelectionModel().getSelectedItem();
        String name = Dialogs.showInputDialog(Strings.get("dialog.presetName.title"),
                Strings.get("dialog.presetName.prompt"), suggested == null ? "" : suggested);
        if (name == null) {
            return;
        }
        name = name.strip();
        if (name.isEmpty()) {
            return;
        }
        // The name becomes a filename on disk, so a name QuPath cannot write is refused here
        // with the reason, rather than at the write with a stack trace.
        if (!GeneralTools.isValidFilename(name)) {
            Dialogs.showErrorMessage(Strings.get("dialog.presetName.title"),
                    Strings.format("error.presetName", name));
            return;
        }
        try {
            if (manager.contains(name) && !Dialogs.showConfirmDialog(
                    Strings.get("dialog.presetOverwrite.title"),
                    Strings.format("dialog.presetOverwrite.message", name))) {
                return;
            }
            manager.put(name, VisibilityPreset.capture(name, options, model));
            logger.info("Saved class visibility preset '{}'", name);
            refreshPresets();
            selectPresetQuietly(name);
            Dialogs.showInfoNotification(Strings.get("notify.title"),
                    Strings.format("notify.presetSaved", name));
        } catch (IOException ex) {
            logger.error("Could not save visibility preset '{}': {}", name, ex.getMessage(), ex);
            Dialogs.showErrorMessage(Strings.get("notify.title"),
                    Strings.format("error.presetSave", name));
        }
    }

    private void promptToDeletePreset() {
        ResourceManager.Manager<VisibilityPreset> manager =
                VisibilityPresetStore.managerFor(qupath.getProject());
        String name = presetCombo.getSelectionModel().getSelectedItem();
        if (manager == null || name == null) {
            return;
        }
        if (!Dialogs.showConfirmDialog(Strings.get("dialog.presetDelete.title"),
                Strings.format("dialog.presetDelete.message", name))) {
            return;
        }
        try {
            manager.remove(name);
            logger.info("Deleted class visibility preset '{}'", name);
            refreshPresets();
            Dialogs.showInfoNotification(Strings.get("notify.title"),
                    Strings.format("notify.presetDeleted", name));
        } catch (IOException ex) {
            logger.error("Could not delete visibility preset '{}': {}", name, ex.getMessage(), ex);
            Dialogs.showErrorMessage(Strings.get("notify.title"),
                    Strings.format("error.presetDelete", name));
        }
    }

    /**
     * Select a preset without applying it.
     *
     * @param name the preset just saved. It already <i>is</i> the current view, so re-applying it
     *        would repaint for nothing and push an undo entry for a change that did not happen.
     */
    private void selectPresetQuietly(String name) {
        updatingPresets = true;
        try {
            presetCombo.getSelectionModel().select(name);
        } finally {
            updatingPresets = false;
        }
        presetDeleteButton.setDisable(false);
    }

    private void soloClass(ClassRow row) {
        if (soloedClass != null && soloedClass == row.pathClass()) {
            undo();
            return;
        }
        pushUndo(Strings.format("action.showOnly", row.displayName()));
        soloedComponent = null;
        soloedClass = row.pathClass();
        // One call. The mode flip used to live here, which meant the model half alone hid exactly
        // the class it was asked to isolate (finding L1); it is now inside soloClass.
        model.soloClass(row.pathClass());
    }

    private void soloComponent(ComponentRow row) {
        if (row.name().equals(soloedComponent)) {
            undo();
            return;
        }
        pushUndo(Strings.format("action.showOnly", row.name()));
        soloedClass = null;
        soloedComponent = row.name();
        model.soloComponent(row.name());
    }

    /**
     * Report the scope of a near-universal component the user has just made an independent rule.
     * It states two ratios and clears on the next action; it contains no verb of advice, because
     * ticking a near-universal component to get "everything positive" is a legitimate choice and
     * a panel that tuts at deliberate choices teaches people to ignore its signals.
     *
     * <p>Only fires for the swamping case. A near-universal component folded into an
     * {@code All} composite alongside others changes almost nothing, so a line about it would be
     * noise.</p>
     */
    private void noteCoverageIfSwamping(ComponentRow row) {
        coverageNote = null;
        if (census.classCount() < COVERAGE_EMPHASIS_MIN_CLASSES) {
            return;
        }
        if (row.coverage() < ClassVisibilityPreferences.coverageThresholdProperty().get()) {
            return;
        }
        boolean independent = model.getCombination() == VisibilityRuleModel.Combination.ANY
                || model.getSelectedComponents().size() == 1;
        if (!independent) {
            return;
        }
        coverageNote = Strings.format("status.s9", row.name(), row.spread(), row.classCount(),
                row.count(), census.totalObjects());
    }

    // ------------------------------------------------------------------------------------------
    // View callbacks
    // ------------------------------------------------------------------------------------------

    @Override
    public void onCensus(ClassCensus newCensus) {
        this.census = newCensus;
        harvesting = false;
        countsUnknown = false;
        countsStale = false;
        classTable.setDisable(false);
        componentTable.setDisable(false);
        spinner.setVisible(false);
        spinner.setManaged(false);
        rebuildRows();
        refreshRuleDependentUi();
    }

    @Override
    public void onHarvestStarted(String imageName, boolean imageChanged) {
        harvesting = true;
        if (imageChanged) {
            // Rows on screen belong to the previous image: leave them in place so the panel does
            // not flash blank, but disable them so nothing can be acted on by mistake. Their
            // counts are not merely stale, they are unknowable, so the cells read "--".
            countsUnknown = true;
            classTable.setDisable(true);
            componentTable.setDisable(true);
        } else {
            // Same image, new count: the numbers on screen are still meaningful, so they stay
            // and the header says they are old. countsStale had no rendered effect at all before
            // Phase 5, because this method never called updateHeaders (finding S4).
            countsStale = true;
        }
        spinner.setVisible(true);
        spinner.setManaged(true);
        updateStatus();
        updateHeaders();
        classTable.refresh();
        componentTable.refresh();
    }

    @Override
    public void onImageChanged(String imageName) {
        // The components on screen are about to be replaced, so a glow around a control that was
        // describing the old image's components is pointing at nothing.
        stopCombinationHintPulse();
        currentImageName = imageName;
        imageLabel.setText(imageName == null
                ? Strings.get("label.image.none")
                : Strings.format("label.image", imageName));
        imageLabel.setTooltip(imageName == null ? null : new Tooltip(imageName));
        title.set(imageName == null
                ? Strings.get("pane.title.noImage")
                : Strings.format("pane.title", imageName));
    }

    @Override
    public void onRulesChanged() {
        model.onExternalChange();
    }

    private void onModelChanged() {
        refreshRuleDependentUi();
    }

    // ------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------

    private void rebuildRows() {
        List<ClassRow> classes = new ArrayList<>();
        for (PathClass pathClass : census.classes()) {
            classes.add(new ClassRow(pathClass, displayName(pathClass),
                    census.countForClass(pathClass), true));
        }
        if (includeEmptyCheck.isSelected()) {
            Set<PathClass> present = new LinkedHashSet<>(census.classes());
            // Read-only. QuPath syncs this list back into the project, so writing it would edit
            // the user's project class list -- which this panel must never do.
            for (PathClass pathClass : qupath.getAvailablePathClasses()) {
                if (pathClass == null || pathClass == PathClass.NULL_CLASS || present.contains(pathClass)) {
                    continue;
                }
                classes.add(new ClassRow(pathClass, displayName(pathClass), 0L, false));
            }
        }
        classRows.setAll(classes);

        List<ComponentRow> components = new ArrayList<>();
        for (String component : census.components()) {
            components.add(new ComponentRow(component, census.spreadForComponent(component),
                    census.classCount(), census.countForComponent(component)));
        }
        componentRows.setAll(components);

        applyFilter(findField.getText());
        updatePlaceholders();
        updateHeaders();
    }

    private static String displayName(PathClass pathClass) {
        if (pathClass == null || pathClass == PathClass.NULL_CLASS) {
            return Strings.get("row.unclassified");
        }
        return pathClass.toString();
    }

    private void applyFilter(String text) {
        String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            filteredClasses.setPredicate(row -> true);
            filteredComponents.setPredicate(row -> true);
        } else {
            filteredClasses.setPredicate(row -> row.displayName().toLowerCase(Locale.ROOT).contains(needle));
            filteredComponents.setPredicate(row -> row.name().toLowerCase(Locale.ROOT).contains(needle));
        }
        updatePlaceholders();
        updateHeaders();
        updateCheckAllState();
        // The name cells render differently with a filter on -- matched text is bold -- and a
        // predicate change does not by itself re-render a row that survived the change.
        classTable.refresh();
        componentTable.refresh();
    }

    /**
     * Drive the class table's header check control from the rows the list is currently showing.
     *
     * <p>It acts on the listed rows only, so with nothing listed -- an empty image, or a filter
     * that matches no class -- it has nothing to act on. Disabled beats a click that appears to
     * be ignored; the list's placeholder says why the list is empty.</p>
     *
     * <p>Tri-state, and the indeterminate leg is not decoration: a two-state box over a partly
     * checked list has to claim one of the two, and either claim is wrong about most of the rows
     * underneath it. {@code setAllowIndeterminate} stays false so a click cycles checked /
     * unchecked only -- indeterminate is a state this panel reports, never one the user is asked
     * to pass through -- and from indeterminate JavaFX's own {@code fire()} lands on checked,
     * which is the useful direction.</p>
     */
    private void updateCheckAllState() {
        long checked = filteredClasses.stream()
                .filter(row -> model.isClassSelected(row.pathClass()))
                .count();
        CheckAllState state = checkAllStateFor(filteredClasses.size(), checked);
        boolean nothingListed = state == CheckAllState.NOTHING_LISTED;
        headerCheckAll.setDisable(nothingListed);
        headerCheckAll.setIndeterminate(state == CheckAllState.SOME);
        headerCheckAll.setSelected(state == CheckAllState.ALL);
        // The way out of the blank viewer, marked on the control that takes it. Not while it is
        // disabled: a halo on a control that cannot be clicked points at a dead end.
        boolean everythingHidden = !nothingListed && isEverythingHidden();
        headerCheckAll.setEffect(everythingHidden ? everythingHiddenHalo : null);
        headerCheckAll.setTooltip(new Tooltip(everythingHidden
                ? Strings.get("tooltip.column.checkAll.allHidden")
                : Strings.get("tooltip.column.checkAll")));
        // A bare checkbox in a column header has no label at all, so this is not a nicety: it is
        // the only name a screen reader can read out for it.
        headerCheckAll.setAccessibleText(everythingHidden
                ? Strings.get("accessible.checkAll.allHidden")
                : Strings.get("accessible.checkAll"));
    }

    /**
     * @return whether the current state hides every object in every image: "show only checked
     *         classes" with nothing checked. This is the same condition the status strip
     *         reports as {@code status.s2}, computed from the same two facts, so the halo and
     *         the sentence explaining it cannot disagree.
     */
    private boolean isEverythingHidden() {
        return isEverythingHidden(options.getSelectedClassVisibilityMode(), model.activeRuleCount());
    }

    /**
     * The everything-hidden pair, as a static so every surface that has to react to it can be
     * verified without a JavaFX toolkit.
     *
     * @param mode the visibility mode in force
     * @param ruleCount how many entries are in the rule set
     * @return whether this pair hides every object in every image
     */
    static boolean isEverythingHidden(OverlayOptions.ClassVisibilityMode mode, int ruleCount) {
        return mode == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED && ruleCount == 0;
    }

    /**
     * @param mode the visibility mode in force
     * @param ruleCount how many entries are in the rule set
     * @return the empty rules table's placeholder text. Two strings, because there was one, and
     *         it was a static Label claiming "Every object is visible" in the state the panel
     *         opens in, where every object is hidden (external tester, 2026-09-01).
     */
    static String rulesPlaceholderText(OverlayOptions.ClassVisibilityMode mode, int ruleCount) {
        return isEverythingHidden(mode, ruleCount)
                ? Strings.get("placeholder.rules.empty.allHidden")
                : Strings.get("placeholder.rules.empty");
    }

    /**
     * What the {@code Active rules} table says about one rule, as a string key.
     *
     * @param exactMatchesOnly whether QuPath's {@code Exact matches only} is on
     * @param source what produced the rule
     * @param listedInImage whether the entry is itself one of the classes the list is showing
     * @param reachesObjects whether the rule matches any object the class list is counting
     * @return the status text
     */
    static String ruleStatusText(boolean exactMatchesOnly, VisibilityRuleModel.RuleSource source,
                                 boolean listedInImage, boolean reachesObjects) {
        if (exactMatchesOnly && source != VisibilityRuleModel.RuleSource.CLASS) {
            return Strings.get("rules.status.exactOnly");
        }
        if (listedInImage) {
            return Strings.get("rules.status.listed");
        }
        return reachesObjects
                ? Strings.get("rules.status.derived")
                : Strings.get("rules.status.noMatch");
    }

    /**
     * What the always-visible status strip shows, given the standing state message.
     *
     * <p>The whole of item 6 is here. The routine message moves inside the {@code Active rules}
     * expander; the everything-hidden warning does not, because it is the only on-screen
     * explanation for a blank viewer; and the coverage note stays because it describes the click
     * the user has just made.</p>
     *
     * @param standingText the standing state message
     * @param alarm whether that message is the everything-hidden warning
     * @param coverageNote the near-universal-component note, or null
     * @return the strip text, empty when the strip should leave the layout
     */
    static String stripText(String standingText, boolean alarm, String coverageNote) {
        String text = alarm ? standingText : "";
        if (coverageNote != null && !coverageNote.isEmpty()) {
            text = text.isEmpty() ? coverageNote : text + " " + coverageNote;
        }
        return text;
    }

    /** What the class table's header check control shows for the rows currently listed. */
    enum CheckAllState {
        /** Nothing is listed, so there is nothing to act on. */
        NOTHING_LISTED,
        /** No listed class is a rule. */
        NONE,
        /** Some are, some are not. */
        SOME,
        /** Every listed class is a rule. */
        ALL
    }

    /**
     * @param listed how many classes the list is currently showing
     * @param checked how many of those are rules
     * @return the header control's state. The {@code SOME} leg is not decoration: a two-state box
     *         over a partly checked list has to claim one of the two, and either claim is wrong
     *         about most of the rows underneath it.
     */
    static CheckAllState checkAllStateFor(int listed, long checked) {
        if (listed <= 0) {
            return CheckAllState.NOTHING_LISTED;
        }
        if (checked <= 0) {
            return CheckAllState.NONE;
        }
        return checked >= listed ? CheckAllState.ALL : CheckAllState.SOME;
    }

    private void updateHeaders() {
        // Present rows only, both numbers. "Include classes with no objects here" adds rows for
        // project classes this image does not use, and counting those into a header that ends
        // "in this image" made the class header disagree with the component list's spread
        // denominator, which is census-only -- two numbers on screen, one image, no reconciling
        // them. The unused rows are still listed, sorted last, showing a count of zero.
        int classTotal = (int) classRows.stream().filter(ClassRow::present).count();
        int classShown = (int) filteredClasses.stream().filter(ClassRow::present).count();
        int componentTotal = componentRows.size();
        int componentShown = filteredComponents.size();
        boolean filtered = !findField.getText().isEmpty();
        // The list scope is named in the header, not only in the combo box and a hover tooltip.
        // The misreading this control invites -- "List: Annotations means only annotations are
        // hidden" -- is a confident one, and a confident user does not hover (finding S2).
        String scopeNoun = controller.getScope().lowerCaseNoun();
        if (wideProfile) {
            classHeader.setText(filtered
                    ? Strings.format("header.classes.wide.filtered", scopeNoun, classShown, classTotal)
                    : Strings.format("header.classes.wide", scopeNoun, classTotal));
        } else {
            classHeader.setText(filtered
                    ? Strings.format("header.classes.narrow.filtered", scopeNoun, classShown, classTotal)
                    : Strings.format("header.classes.narrow", scopeNoun, classTotal));
        }
        // The component header says what CHECKING a row does, not what the list contains, and
        // that sentence is the same length in both profiles -- so unlike the class header it does
        // not need a narrow variant, and it does not name the list scope. The scope is still
        // named once, in the class header above it.
        componentHeader.setText(filtered
                ? Strings.format("header.components.filtered", componentShown, componentTotal)
                : Strings.format("header.components", componentTotal));
        String countHeaderText = countsStale
                ? Strings.get("column.count.stale")
                : Strings.get("column.count");
        setCountHeaderText(classTable, countHeaderText);
        setCountHeaderText(componentTable, countHeaderText);
    }

    private static void setCountHeaderText(TableView<?> table, String text) {
        // By id, not by position: both tables let the user reorder their columns, and addressing
        // "the last column" would eventually put "Count (stale)" over the wrong header. On the
        // column's own text since 0.2.0, so the table's menu button names it too.
        for (TableColumn<?, ?> column : table.getColumns()) {
            if (COUNT_COLUMN_ID.equals(column.getId())) {
                column.setText(text);
                return;
            }
        }
    }

    private void updatePlaceholders() {
        String scopeNoun = controller.getScope().lowerCaseNoun();
        boolean noImage = currentImageName == null;
        boolean filtered = !findField.getText().isEmpty();
        if (noImage) {
            classTable.setPlaceholder(new Label(Strings.get("placeholder.classes.noImage")));
            componentTable.setPlaceholder(new Label(Strings.get("placeholder.components.noImage")));
        } else if (filtered && classRows.size() > 0) {
            VBox box = new VBox(GAP);
            box.setAlignment(Pos.CENTER);
            Label label = new Label(Strings.format("placeholder.classes.noMatch", findField.getText()));
            label.setWrapText(true);
            Button clear = new Button(Strings.get("button.clearFilter"));
            clear.setOnAction(e -> findField.clear());
            box.getChildren().addAll(label, clear);
            classTable.setPlaceholder(box);
            Label componentLabel = new Label(
                    Strings.format("placeholder.components.noMatch", findField.getText()));
            componentLabel.setWrapText(true);
            componentTable.setPlaceholder(componentLabel);
        } else {
            Label label = new Label(Strings.format("placeholder.classes.noObjects", scopeNoun));
            label.setWrapText(true);
            classTable.setPlaceholder(label);
            Label componentLabel = new Label(Strings.format("placeholder.components.noObjects", scopeNoun));
            componentLabel.setWrapText(true);
            componentTable.setPlaceholder(componentLabel);
        }
    }

    private void refreshRuleDependentUi() {
        updateCombinationLabels();
        updateRuleTable();
        updateStatus();
        updateCheckAllState();
        classTable.refresh();
        componentTable.refresh();
    }

    private void updateCombinationLabels() {
        List<String> selected = new ArrayList<>(model.getSelectedComponents());
        int n = selected.size();
        String anyText;
        String allText;
        switch (n) {
            case 0 -> {
                anyText = Strings.get("combination.any.none");
                allText = Strings.get("combination.all.none");
            }
            case 1 -> {
                anyText = Strings.format("combination.any.one", selected.get(0));
                allText = Strings.format("combination.all.one", selected.get(0));
            }
            case 2 -> {
                anyText = Strings.format("combination.any.two", selected.get(0), selected.get(1));
                allText = Strings.format("combination.all.two", selected.get(0), selected.get(1));
            }
            case 3 -> {
                anyText = Strings.format("combination.any.three",
                        selected.get(0), selected.get(1), selected.get(2));
                allText = Strings.format("combination.all.three",
                        selected.get(0), selected.get(1), selected.get(2));
            }
            default -> {
                anyText = Strings.format("combination.any.many",
                        selected.get(0), selected.get(1), selected.get(2), n - 3);
                allText = Strings.format("combination.all.many",
                        selected.get(0), selected.get(1), selected.get(2), n - 3);
            }
        }
        anyRadio.setText(anyText);
        allRadio.setText(allText);
        boolean disabled = n < 2;
        anyRadio.setDisable(disabled);
        allRadio.setDisable(disabled);
        // JavaFX shows no tooltip on a disabled node, so tooltip.combination.disabled could never
        // be read. At one checked component -- a first-timer's most natural first action -- the
        // radio labels read "Any -- CD8" / "All -- CD8", greyed, with nothing anywhere stating
        // the two-or-more rule (finding m1). The label is not disabled-only text; it is the rule.
        combinationLabel.setText(disabled
                ? Strings.get("label.combination.disabled")
                : Strings.get("label.combination"));
        Tooltip disabledTip = new Tooltip(Strings.get("tooltip.combination.disabled"));
        if (disabled) {
            anyRadio.setTooltip(disabledTip);
            allRadio.setTooltip(disabledTip);
        } else {
            anyRadio.setTooltip(new Tooltip(Strings.get("tooltip.combination.any")));
            allRadio.setTooltip(new Tooltip(Strings.get("tooltip.combination.all")));
        }
        anyRadio.setSelected(model.getCombination() == VisibilityRuleModel.Combination.ANY);
        allRadio.setSelected(model.getCombination() == VisibilityRuleModel.Combination.ALL);

        // The moment the control stops being inert is the only moment at which it can be taught.
        // The panel has to be on screen for that to be true: a rule change can arrive from
        // QuPath's own class list while the panel sits behind a collapsed analysis pane, and
        // spending the one showing the user gets on a pulse nobody saw would waste it. A crossing
        // that cannot fire leaves the session latch unspent.
        boolean mayPulse = ClassVisibilityPreferences.highlightNewControlsProperty().get()
                && visibleForUpdatesProperty().get();
        switch (combinationHint.onComponentCount(n, mayPulse)) {
            case PULSE -> startCombinationHintPulse();
            case STOP -> stopCombinationHintPulse();
            case NONE -> { }
        }
    }

    /**
     * Draw the eye to the Any / All group, once per session, for five seconds.
     *
     * <p>Three gentle swells of the same blue halo the panel already uses for the class table's
     * header check control, at 0.6 Hz -- see {@link #HINT_HALF_PERIOD} for why the rate matters. The pulse
     * is pure emphasis: the label beside it already states the rule in words, and nothing here
     * encodes information that is available only from the motion or only from the colour.</p>
     *
     * <p><b>It cannot intercept a click, because it is not a node.</b> A {@code DropShadow} on the
     * existing container renders behind and around the group without adding anything to the scene
     * graph, so there is no overlay to make mouse-transparent -- and making the group itself
     * mouse-transparent would break the very radios it is pointing at. Nothing here touches focus
     * either.</p>
     */
    private void startCombinationHintPulse() {
        stopCombinationHintPulse();
        if (combinationBox == null) {
            return;
        }
        combinationBox.setEffect(combinationHintGlow);
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(combinationHintGlow.radiusProperty(), 0.0),
                        new KeyValue(combinationHintGlow.spreadProperty(), 0.0)),
                new KeyFrame(HINT_HALF_PERIOD,
                        new KeyValue(combinationHintGlow.radiusProperty(), HINT_GLOW_RADIUS),
                        new KeyValue(combinationHintGlow.spreadProperty(), HINT_GLOW_SPREAD)));
        pulse.setAutoReverse(true);
        pulse.setCycleCount(HINT_HALF_CYCLES);
        // An even cycle count ends on the reverse leg, so the glow is already back at zero here;
        // clearing the effect is about not leaving one attached, not about hiding a bright frame.
        pulse.setOnFinished(e -> clearCombinationHintGlow());
        combinationHintPulse = pulse;
        pulse.play();
        logger.debug("Pulsing the Any / All hint");
    }

    /**
     * Stop the pulse and detach the glow. Idempotent, and called from every route by which the
     * user can stop needing it: clicking either radio, dropping back below two checked
     * components, switching image, the panel leaving the screen (which includes a dock or an
     * undock), and {@link #dispose()}. A {@code Timeline} left running against a node the panel
     * has finished with is exactly the kind of thing this panel's lifecycle discipline exists to
     * prevent.
     */
    private void stopCombinationHintPulse() {
        if (combinationHintPulse != null) {
            combinationHintPulse.stop();
            combinationHintPulse = null;
        }
        clearCombinationHintGlow();
    }

    private void clearCombinationHintGlow() {
        combinationHintGlow.setRadius(0);
        combinationHintGlow.setSpread(0);
        if (combinationBox != null && combinationBox.getEffect() == combinationHintGlow) {
            combinationBox.setEffect(null);
        }
    }

    private void updateRuleTable() {
        List<RuleRow> rows = new ArrayList<>();
        Set<PathClass> present = new LinkedHashSet<>(census.classes());
        for (PathClass entry : model.activeRules()) {
            VisibilityRuleModel.RuleSource ruleSource = model.sourceOf(entry);
            // An All composite is not a class. Rendering it as one -- "CD45: CD8", built in
            // alphabetical order rather than the project's naming order -- put a class name in
            // the one table that states the truth about what is in force, for a class that exists
            // nowhere in the user's data (finding S9). The sorted build stays; only the label
            // changes.
            String name = ruleSource == VisibilityRuleModel.RuleSource.COMPONENTS_ALL && entry != null
                    ? Strings.format("rules.name.composite", String.join(" + ", new TreeSet<>(entry.toSet())))
                    : displayName(entry);
            String source = switch (ruleSource) {
                case CLASS -> Strings.get("rules.source.class");
                case COMPONENTS_ANY -> Strings.get("rules.source.componentsAny");
                case COMPONENTS_ALL -> Strings.get("rules.source.componentsAll");
                case ELSEWHERE -> Strings.get("rules.source.elsewhere");
            };
            // What the status column has to answer is "is this rule doing anything", and until
            // 0.2.0 it answered "is this rule's name a row above" -- which is a different
            // question and gets the combinatorial case backwards. A rule for CD8 in an image
            // whose objects all carry CD8: GzB has no row of its own and hides thousands of
            // objects, and the table called that "Not in this image" (external tester,
            // 2026-09-01). The three live statuses now come off the same predicate the Affects
            // column uses, so the two cannot disagree.
            String status = ruleStatusText(exactCheck.isSelected(), ruleSource,
                    present.contains(entry == null ? PathClass.NULL_CLASS : entry),
                    !ruleReachesNothing(entry));
            rows.add(new RuleRow(entry, name, source, status));
        }
        ruleRows.setAll(rows);
        updateRulesPlaceholder();
        // Nothing to clear is not the same as a broken button. The table's own placeholder is
        // the explanation sitting right beside it.
        clearRulesButton.setDisable(rows.isEmpty());
        int count = rows.size();
        rulesPane.setText(count == 0
                ? Strings.get("rules.none")
                : count == 1 ? Strings.get("rules.one") : Strings.format("rules.many", count));
    }

    /**
     * The empty-rules-table message, which has to name the mode.
     *
     * <p>It was a static Label reading <i>"No rules are active. Every object is visible."</i> --
     * true under <i>Hide checked classes</i>, and the exact opposite of the truth under
     * <i>Show only checked classes</i> with nothing checked, which is the state this panel
     * <b>opens in</b>. So the table asserted that everything was visible directly above a status
     * strip saying every object was hidden, and an external tester photographed both at once
     * (2026-09-01).</p>
     */
    private void updateRulesPlaceholder() {
        Label label = new Label(rulesPlaceholderText(
                options.getSelectedClassVisibilityMode(), model.activeRuleCount()));
        label.setWrapText(true);
        ruleTable.setPlaceholder(label);
    }

    /**
     * @param entry a rule in force
     * @return whether it matches no object the class list is counting. Deliberately
     *         {@link #affectedObjects(PathClass)} rather than "is this entry a row above": a rule
     *         is doing its job whenever it reaches an object, and on a combinatorial panel it
     *         usually reaches them through classes containing its parts rather than through a
     *         class of its own name. Same predicate as the Affects column.
     */
    private boolean ruleReachesNothing(PathClass entry) {
        return affectedObjects(entry) == 0;
    }

    private void updateStatus() {
        if (harvesting && currentImageName != null) {
            // Progress, not a standing state: it belongs beside the spinner, which is on the
            // strip, and it is gone again within a frame or two.
            showStripText(Strings.format("status.s6", currentImageName), false);
            statusButtons.getChildren().clear();
            updateStripVisibility();
            return;
        }
        // Counts are of set entries, not checked rows. A rule whose class is absent from the
        // current image has no row; a row-counting indicator would read "0 rules active" while
        // objects were being hidden, which is the exact failure this strip exists to prevent.
        int count = model.activeRuleCount();
        boolean showOnly = options.getSelectedClassVisibilityMode()
                == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED;
        boolean noImage = currentImageName == null;
        String text;
        List<Node> buttons = new ArrayList<>();
        boolean alarm = false;
        if (count == 0 && !showOnly) {
            text = Strings.get("status.s1");
        } else if (count == 0) {
            text = Strings.get("status.s2");
            alarm = true;
            buttons.add(switchToHideButton);
            buttons.add(resetButton);
        } else if (noImage) {
            text = count == 1 ? Strings.get("status.s8.one") : Strings.format("status.s8.many", count);
            buttons.add(resetButton);
        } else if (showOnly && count == 1 && (soloedClass != null || soloedComponent != null)) {
            // "Showing only X" was false whenever X had supersets in the image, which at the
            // design centre is most of the time: a rule for "CD3: CD8" also shows
            // "CD3: CD8: CD4: CD45". The wording follows what the rule actually reaches rather
            // than what was clicked (finding S1).
            if (soloedClass != null) {
                String name = displayName(soloedClass);
                text = affectedObjects(soloedClass) > census.countForClass(soloedClass)
                        ? Strings.format("status.s5.class", name)
                        : Strings.format("status.s5.only", name);
            } else {
                text = options.getUseExactSelectedClasses()
                        ? Strings.format("status.s5.only", soloedComponent)
                        : Strings.format("status.s5.component", soloedComponent);
            }
            buttons.add(resetButton);
        } else if (showOnly) {
            text = count == 1 ? Strings.get("status.s4.one") : Strings.format("status.s4.many", count);
            buttons.add(resetButton);
        } else {
            text = count == 1 ? Strings.get("status.s3.one") : Strings.format("status.s3.many", count);
            buttons.add(resetButton);
        }
        // Only claimed when there is a census to claim it against. With no objects in the
        // chosen List scope every rule trivially reaches nothing, and saying so would be a
        // statement about the scope dressed up as a statement about the rules.
        int orphans = census.isEmpty() ? 0 : countOrphanRules();
        if (orphans > 0 && count > 0 && !noImage) {
            text = text + " " + (orphans == 1
                    ? Strings.get("status.s7.one")
                    : Strings.format("status.s7.many", orphans));
        }
        rulesStatusLabel.setText(text);
        rulesStatusLabel.setAccessibleText(text);
        // The split (external tester, 2026-09-01: "put inside drop down"). The routine "N rules
        // active" sentence is a standing description of a state the user built on purpose, and
        // it sat on screen permanently for the whole session; it now lives inside the Active
        // rules expander, whose own title already carries the count.
        //
        // status.s2 does NOT move, and this is the one place in this round where less on screen
        // would have been worse. It is the only on-screen explanation for a blank viewer, and
        // burying the sentence that explains an empty image inside a collapsed control is the
        // exact failure this panel exists to prevent (finding C1, story R2). The coverage note
        // stays too: it describes the click the user just made, and a response nobody can see is
        // not a response.
        showStripText(stripText(text, alarm, coverageNote), alarm);
        if (undoSlot != null) {
            undoButton.setText(Strings.format("button.undo.named", undoSlot.actionLabel()));
            buttons.add(0, undoButton);
        }
        statusButtons.getChildren().setAll(buttons);
        updateStripVisibility();
    }

    /**
     * Take the always-visible strip out of the layout when it holds neither a message nor a
     * button. With the routine sentence gone the strip is usually empty, and an empty strip that
     * still claims its padding is the panel reserving space for nothing -- which is the complaint
     * this round is answering.
     */
    private void updateStripVisibility() {
        boolean anything = statusLabel.isManaged() || !statusButtons.getChildren().isEmpty();
        statusBox.setVisible(anything);
        statusBox.setManaged(anything);
    }

    /**
     * Put text on the always-visible strip, or take the strip out of the layout when there is
     * none. Unmanaged rather than merely blank: an empty label still claims a line, and a blank
     * line that appears and disappears as the state changes is the panel twitching at the user.
     *
     * @param text what to show; empty removes the strip
     * @param alarm whether this is the everything-hidden warning
     */
    private void showStripText(String text, boolean alarm) {
        boolean any = !text.isEmpty();
        statusLabel.setText(text);
        statusLabel.setAccessibleText(text);
        statusLabel.setVisible(any);
        statusLabel.setManaged(any);
        // Severity is carried by the [OK] / [i] / [!] text markers and by weight, never by colour
        // alone -- an accessibility requirement, and it keeps the panel theme-neutral.
        statusLabel.setFont(alarm
                ? Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize())
                : Font.getDefault());
    }

    /**
     * @return how many rules in force reach no object the class list is counting.
     *
     *         <p>This counted set membership until 0.2.0 -- "is this entry one of the classes in
     *         the census" -- which made two different things read as orphans that are not: an
     *         {@code All} composite, which is never a class in anybody's image and yet is the
     *         whole point of the Components list, and any rule matching only through derived
     *         classes. An external tester saw <i>"1 rule active -- only objects matching it are
     *         shown. 1 rule has no class in this image."</i> over an image she knew carried those
     *         cells, with the rules table calling the same entry a composite in the same breath
     *         (2026-09-01). Counting what a rule reaches makes the strip, the rules table and the
     *         Affects column three views of one number.</p>
     */
    private int countOrphanRules() {
        int orphans = 0;
        for (PathClass entry : model.activeRules()) {
            if (ruleReachesNothing(entry)) {
                orphans++;
            }
        }
        return orphans;
    }

    private Comparator<ClassRow> classOrdering(Comparator<ClassRow> base) {
        // Present classes never interleave with project classes that this image does not use, and
        // Unclassified always sorts to the bottom: it is a category, not a class.
        Comparator<ClassRow> ordering = Comparator
                .comparingInt((ClassRow row) -> row.present() ? 0 : 1)
                .thenComparingInt(row -> row.isUnclassified() ? 1 : 0);
        return base == null ? ordering.thenComparing(ClassRow::displayName) : ordering.thenComparing(base);
    }

    // ------------------------------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------------------------------

    private final class ClassCheckCell extends TableCell<ClassRow, ClassRow> {

        private final CheckBox checkBox = new CheckBox();

        private ClassCheckCell() {
            checkBox.setOnAction(e -> {
                ClassRow row = getItem();
                if (row != null) {
                    toggleClass(row, checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(ClassRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            PathClass key = item.pathClass() == null ? PathClass.NULL_CLASS : item.pathClass();
            boolean derived = model.componentDerivedEntries().contains(key);
            checkBox.setSelected(model.isClassSelected(key));
            checkBox.setDisable(derived);
            checkBox.setTooltip(new Tooltip(derived
                    ? Strings.get("tooltip.row.class.disabled")
                    : Strings.get("tooltip.row.class")));
            // JavaFX shows no tooltip on a disabled node, so the explanation for a checkbox the
            // user cannot tick has to live on the cell around it -- otherwise the one row in the
            // list that refuses to respond is the one row with nothing to say for itself.
            setTooltip(derived ? new Tooltip(Strings.get("tooltip.row.class.disabled")) : null);
            checkBox.setAccessibleText(Strings.format("accessible.row.class", item.displayName()));
            setGraphic(checkBox);
        }
    }

    private final class ComponentCheckCell extends TableCell<ComponentRow, ComponentRow> {

        private final CheckBox checkBox = new CheckBox();

        private ComponentCheckCell() {
            checkBox.setOnAction(e -> {
                ComponentRow row = getItem();
                if (row != null) {
                    toggleComponent(row, checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(ComponentRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(model.isComponentSelected(item.name()));
            checkBox.setTooltip(new Tooltip(Strings.get("tooltip.row.component")));
            checkBox.setAccessibleText(Strings.format("accessible.row.component", item.name()));
            setGraphic(checkBox);
        }
    }

    /**
     * Class name cell: colour swatch, ellipsis-with-tooltip name, bold while soloed.
     *
     * <p>The swatch is the channel that connects a row to what is on screen. At thirty
     * combinatorial names -- the condition this panel was built for -- the names are
     * near-identical strings and colour is the only thing that distinguishes a population at a
     * glance; QuPath's own class list has it, and the README concedes the point (finding S6). It
     * is read-only: {@code PathClass} colours are interned, global and persisted with the
     * project, so nothing here ever writes one.</p>
     */
    private final class ClassNameCell extends TableCell<ClassRow, ClassRow> {

        private final Rectangle swatch = new Rectangle(10, 10);
        private final TextFlow flow = new TextFlow();
        /** Holds the swatch always, and the highlighted name only while the filter is on. */
        private final HBox box = new HBox(GRAPHIC_GAP);

        private ClassNameCell() {
            swatch.setArcWidth(2);
            swatch.setArcHeight(2);
            // A thin outline so a class coloured close to the background is still a visible chip
            // in both QuPath themes.
            swatch.setStroke(Color.gray(0.5, 0.6));
            swatch.setStrokeWidth(0.75);
            box.setAlignment(Pos.CENTER_LEFT);
            // A Labeled re-fits its own text when the column is dragged; a TextFlow does not, so
            // the highlighted path has to be told. Only while filtering -- the unfiltered path is
            // still a plain Labeled and still ellipsises itself.
            widthProperty().addListener((obs, oldValue, newValue) -> {
                if (MatchHighlighter.isActive(findField.getText())) {
                    render(getItem());
                }
            });
        }

        @Override
        protected void updateItem(ClassRow item, boolean empty) {
            super.updateItem(item, empty);
            render(empty ? null : item);
        }

        private void render(ClassRow item) {
            if (item == null || isEmpty()) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                setFont(Font.getDefault());
                return;
            }
            String name = item.displayName();
            // The full name, always, whichever path renders it: both of them truncate.
            setTooltip(new Tooltip(name));
            PathClass pathClass = item.pathClass();
            boolean hasSwatch = pathClass != null && pathClass != PathClass.NULL_CLASS
                    && pathClass.getColor() != null;
            if (hasSwatch) {
                swatch.setFill(ColorToolsFX.getPathClassColor(pathClass));
            }
            boolean solo = soloedClass != null && soloedClass == pathClass;
            String filter = findField.getText();
            if (!MatchHighlighter.isActive(filter)) {
                setFont(soloFont(solo));
                setText(name);
                box.getChildren().setAll(hasSwatch ? List.of(swatch) : List.of());
            } else {
                setFont(Font.getDefault());
                setText(null);
                double reserved = hasSwatch ? swatch.getWidth() + GRAPHIC_GAP : 0;
                fillHighlighted(this, flow, name, filter, solo, availableWidth(this, reserved));
                box.getChildren().setAll(hasSwatch ? List.of(swatch, flow) : List.of(flow));
            }
            setGraphic(box.getChildren().isEmpty() ? null : box);
        }
    }

    /** Ellipsis-with-tooltip component name cell, bold while soloed, filter matches bolded. */
    private final class ComponentNameCell extends TableCell<ComponentRow, ComponentRow> {

        private final TextFlow flow = new TextFlow();

        private ComponentNameCell() {
            widthProperty().addListener((obs, oldValue, newValue) -> {
                if (MatchHighlighter.isActive(findField.getText())) {
                    render(getItem());
                }
            });
        }

        @Override
        protected void updateItem(ComponentRow item, boolean empty) {
            super.updateItem(item, empty);
            render(empty ? null : item);
        }

        private void render(ComponentRow item) {
            if (item == null || isEmpty()) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                setFont(Font.getDefault());
                return;
            }
            setTooltip(new Tooltip(item.name()));
            boolean solo = item.name().equals(soloedComponent);
            String filter = findField.getText();
            if (!MatchHighlighter.isActive(filter)) {
                setFont(soloFont(solo));
                setText(item.name());
                setGraphic(null);
            } else {
                setFont(Font.getDefault());
                setText(null);
                fillHighlighted(this, flow, item.name(), filter, solo, availableWidth(this, 0));
                setGraphic(flow);
            }
        }
    }

    /**
     * @param cell the cell being rendered
     * @param reserved pixels already spoken for inside the cell, such as a colour swatch
     * @return how much width the name has to fit in. One pixel is held back so a name measured as
     *         exactly fitting cannot round up into wrapping onto a second line, which is the one
     *         failure mode of a {@code TextFlow} that a {@code Labeled} does not have.
     */
    private static double availableWidth(TableCell<?, ?> cell, double reserved) {
        Insets insets = cell.getInsets();
        return cell.getWidth() - insets.getLeft() - insets.getRight() - reserved - 1;
    }

    /**
     * Render a name into a {@link TextFlow}, bolding what the filter matched and truncating with
     * an ellipsis if it does not fit.
     *
     * <p><b>Why this measures its own text.</b> A {@code TableCell} is a {@code Labeled} and
     * ellipsises its own text for free; a {@code TextFlow} is not, and will happily paint past
     * the column or wrap onto a second line and grow the row. The derived class names this panel
     * was built for -- {@code CD3: CD8: CD4: CD45: PD1 positive} -- overflow a docked-narrow
     * column routinely, so "it will usually fit" is not an available answer. The runs are laid
     * out, summed, and the last visible one is trimmed by binary search until it plus an ellipsis
     * fits. The full name is on the tooltip in both paths.</p>
     *
     * @param cell the cell being rendered, whose text fill the runs follow so they stay legible
     *        in both themes and on a selected row
     * @param flow the flow to fill
     * @param name the full name
     * @param filterText the raw contents of the Find field
     * @param solo whether this row is the soloed one, and so already bold
     * @param available the width to fit in
     */
    private static void fillHighlighted(TableCell<?, ?> cell, TextFlow flow, String name,
                                        String filterText, boolean solo, double available) {
        List<Text> nodes = new ArrayList<>();
        for (MatchHighlighter.Run run : MatchHighlighter.runs(name, filterText)) {
            nodes.add(styledRun(cell, run.text(), run.match(), solo));
        }
        flow.setMaxWidth(available > 0 ? available : Double.MAX_VALUE);
        flow.getChildren().setAll(available > 0 ? truncateToFit(cell, nodes, solo, available) : nodes);
        pinToOneLine(flow);
    }

    /**
     * Hold a {@link TextFlow} to a single line, and clip it there.
     *
     * <p><b>This is a safety net, and it was earned.</b> A {@code TextFlow} given less width than
     * its content does not truncate -- it wraps, and because its height then feeds the row's
     * height, the row grows with it. A JavaFX probe against this code rendering
     * {@code CD3: CD8: CD4: CD45: PD1 positive: FoxP3 negative} in a 164px column produced a
     * <b>748-pixel row</b>: one class name, most of the table. That is what "blowing out the
     * column" looks like, and it is one arithmetic slip away at all times, because the width the
     * cell reports and the width the layout finally hands the flow are computed by different
     * code.</p>
     *
     * <p>With the height pinned and a clip on, a measurement that comes out slightly too
     * generous costs a few clipped characters on one row instead of a table with one readable
     * entry in it. The ellipsis from {@code truncateToFit} is the good path; this is the floor
     * under it.</p>
     *
     * @param flow the flow to pin
     */
    private static void pinToOneLine(TextFlow flow) {
        double lineHeight = singleLineHeight();
        flow.setMinHeight(lineHeight);
        flow.setPrefHeight(lineHeight);
        flow.setMaxHeight(lineHeight);
        if (flow.getClip() == null) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(flow.widthProperty());
            clip.heightProperty().bind(flow.heightProperty());
            flow.setClip(clip);
        }
    }

    /**
     * @return the height of one line in the fonts these cells use, measured rather than assumed.
     *         Cached against the default font size, which is the only thing that moves it: QuPath
     *         has a font-size preference, and a hard-coded line height would clip every name in
     *         the panel the moment somebody raised it. FX thread only, like everything else that
     *         renders a cell.
     */
    private static double singleLineHeight() {
        double size = Font.getDefault().getSize();
        if (size != lineHeightFontSize) {
            Text probe = new Text("Xg");
            // Bold, because a matched run is bold and bold is never the shorter of the two.
            probe.setFont(soloFont(true));
            lineHeight = probe.getLayoutBounds().getHeight();
            lineHeightFontSize = size;
        }
        return lineHeight;
    }

    /**
     * @param cell the cell whose text fill the run follows
     * @param text the run text
     * @param match whether the filter matched this run
     * @param solo whether the whole row is bold because it is soloed
     * @return the styled run. A matched run is bold -- except on a soloed row, where the whole
     *         name is already bold and bold could no longer mean "this is what you searched for",
     *         so the match is underlined instead.
     */
    private static Text styledRun(TableCell<?, ?> cell, String text, boolean match, boolean solo) {
        Text node = new Text(text);
        node.setFont(match || solo ? soloFont(true) : Font.getDefault());
        node.setUnderline(match && solo);
        // A raw Text defaults to opaque black, which is invisible in QuPath's dark theme and
        // wrong on a selected row. The cell's own text fill is the value CSS already computed
        // for exactly this text, in this theme, in this selection state.
        node.fillProperty().bind(cell.textFillProperty());
        return node;
    }

    /**
     * @param cell the cell whose text fill the ellipsis run follows
     * @param nodes the runs, in order
     * @param solo whether the row is soloed, so the ellipsis matches its weight
     * @param available the width to fit in
     * @return the runs unchanged if they already fit, otherwise as many as fit with the last one
     *         trimmed and an ellipsis appended
     */
    private static List<Text> truncateToFit(TableCell<?, ?> cell, List<Text> nodes, boolean solo,
                                            double available) {
        double total = 0;
        for (Text node : nodes) {
            total += node.getLayoutBounds().getWidth();
        }
        if (total <= available) {
            return nodes;
        }
        Text ellipsis = styledRun(cell, "...", false, solo);
        double budget = available - ellipsis.getLayoutBounds().getWidth();
        List<Text> fitted = new ArrayList<>();
        for (Text node : nodes) {
            double width = node.getLayoutBounds().getWidth();
            if (width <= budget) {
                fitted.add(node);
                budget -= width;
                continue;
            }
            String trimmed = trimToWidth(node, budget);
            if (!trimmed.isEmpty()) {
                node.setText(trimmed);
                fitted.add(node);
            }
            break;
        }
        fitted.add(ellipsis);
        return fitted;
    }

    /**
     * @param node a run, used as its own measuring stick so the trimmed text is measured in the
     *        font it will be drawn in
     * @param budget the width to fit in
     * @return the longest prefix of the run that fits, possibly empty
     */
    private static String trimToWidth(Text node, double budget) {
        String text = node.getText();
        if (budget <= 0) {
            return "";
        }
        // Binary search rather than a character-at-a-time walk: this runs for every visible row
        // on every pixel of a column drag, and a long derived class name is 40-plus characters.
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            node.setText(text.substring(0, mid));
            if (node.getLayoutBounds().getWidth() <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        node.setText(text);
        return text.substring(0, low);
    }

    /**
     * @param solo whether this row is the soloed one
     * @return the font for it. The soloed row is named in the status strip and by the Undo
     *         button, but nothing marked the row itself, so a user who solos twice in a row could
     *         not see which row was in force (deviation 3.8, finding N13).
     */
    private static Font soloFont(boolean solo) {
        return solo
                ? Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize())
                : Font.getDefault();
    }

    /**
     * The Affects cell: how many objects a click on this row would act on, right now. Bold when
     * that is more than the row's own Count, which is the case the Count column alone misreports.
     */
    private final class AffectsCell extends TableCell<ClassRow, ClassRow> {

        private AffectsCell() {
            setStyle("-fx-alignment: CENTER-RIGHT;");
        }

        @Override
        protected void updateItem(ClassRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                setFont(Font.getDefault());
                return;
            }
            if (countsUnknown) {
                setText("--");
                setFont(Font.getDefault());
                return;
            }
            long affects = affectedObjects(item.pathClass());
            setText(COUNTS.format(affects));
            boolean reachesMore = affects > item.count();
            setFont(reachesMore
                    ? Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize())
                    : Font.getDefault());
        }
    }

    /** Right-aligned, locale-grouped count. Renders {@code --} while the count is unknown. */
    private final class CountCell<S> extends TableCell<S, S> {

        private final java.util.function.ToLongFunction<S> extractor;

        private CountCell(java.util.function.ToLongFunction<S> extractor) {
            this.extractor = extractor;
            setStyle("-fx-alignment: CENTER-RIGHT;");
        }

        @Override
        protected void updateItem(S item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(countsUnknown ? "--" : COUNTS.format(extractor.applyAsLong(item)));
        }
    }

    /**
     * The spread cell: {@code 6/28}, emphasised in bold above the coverage threshold. Bold is the
     * whole treatment -- the row itself is never dimmed, because dimming would read as advice
     * against a click that is often exactly what the user wants.
     */
    private final class SpreadCell extends TableCell<ComponentRow, ComponentRow> {

        @Override
        protected void updateItem(ComponentRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                setFont(Font.getDefault());
                return;
            }
            setText(item.spread() + "/" + item.classCount());
            boolean emphasise = item.classCount() >= COVERAGE_EMPHASIS_MIN_CLASSES
                    && item.coverage() >= ClassVisibilityPreferences.coverageThresholdProperty().get();
            setFont(emphasise
                    ? Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize())
                    : Font.getDefault());
            setTooltip(new Tooltip(Strings.format("tooltip.row.component.coverage",
                    item.spread(), item.classCount(), item.count(), census.totalObjects())));
        }
    }

    private final class RemoveCell extends TableCell<RuleRow, RuleRow> {

        private final Button button = new Button(Strings.get("button.remove"));

        private RemoveCell() {
            button.setTooltip(new Tooltip(Strings.get("tooltip.button.remove")));
            button.setOnAction(e -> {
                RuleRow row = getItem();
                if (row != null) {
                    beforeMutation();
                    model.removeRule(row.entry());
                }
            });
        }

        @Override
        protected void updateItem(RuleRow item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(empty || item == null ? null : button);
        }
    }
}
