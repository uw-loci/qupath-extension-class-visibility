package qupath.ext.classvisibility.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.layout.FlowPane;
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
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.core.ClassCensus;
import qupath.ext.classvisibility.core.ClassHarvester;
import qupath.ext.classvisibility.core.ClassVisibilityController;
import qupath.ext.classvisibility.core.VisibilityPreset;
import qupath.ext.classvisibility.core.VisibilityPresetStore;
import qupath.ext.classvisibility.core.VisibilityRuleModel;
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
    private static final double WIDE_THRESHOLD = 640;

    /** Below this pane width, lay out narrow (stacked). The gap is deliberate hysteresis. */
    private static final double NARROW_THRESHOLD = 580;

    /** Column id of the count column in both tables, so its header can be found after a reorder. */
    private static final String COUNT_COLUMN_ID = "countColumn";

    /** Below this width the component spread column is dropped; recoverable from the column menu. */
    private static final double DROP_SPREAD_COLUMN_WIDTH = 360;

    /**
     * Below this CLASS TABLE width the Affects column is dropped; recoverable from the column
     * menu. Driven off the table rather than the pane, because in the wide profile the table is
     * one half of a split pane and the pane's own width says nothing about how much room the
     * class names have left.
     */
    private static final double DROP_AFFECTS_COLUMN_WIDTH = 340;

    /** Column id of the Affects column, so its visibility can be driven after a reorder. */
    private static final String AFFECTS_COLUMN_ID = "affectsColumn";

    /** Coverage emphasis is suppressed entirely below this many classes. */
    private static final int COVERAGE_EMPHASIS_MIN_CLASSES = 5;

    /** Gap between a class colour swatch and the name beside it. */
    private static final double GRAPHIC_GAP = 4;

    /**
     * The halo colour for <i>Check all listed</i> while every object is hidden.
     *
     * <p>Mid-blue at full saturation, chosen to sit clear of both QuPath themes: it is lighter
     * than the dark theme's controls and darker than the light theme's background, so the glow
     * reads on either without being tuned per theme. It is never the only signal -- the status
     * strip states the condition in words, the button's tooltip and accessible text say it too,
     * and the halo is on a control that is already labelled.</p>
     */
    private static final Color EVERYTHING_HIDDEN_HALO_COLOR = Color.web("#3D8BFD");

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
    private final RadioButton allRadio = new RadioButton();
    private final CheckBox exactCheck = new CheckBox(Strings.get("check.exact"));
    private final CheckBox autoRefreshCheck = new CheckBox(Strings.get("check.autoRefresh"));
    private final CheckBox includeEmptyCheck = new CheckBox(Strings.get("check.includeEmpty"));
    private final ComboBox<ClassHarvester.Scope> scopeCombo = new ComboBox<>();
    /**
     * How QuPath draws cells. The one control in this panel that has nothing to do with classes:
     * it is QuPath's own {@code View -> Cell display}, put where the user is already looking.
     */
    private final ComboBox<OverlayOptions.DetectionDisplayMode> cellDisplayCombo = new ComboBox<>();

    /** Named visibility presets stored in the project, and the two controls that manage them. */
    private final ComboBox<String> presetCombo = new ComboBox<>();
    private final Button presetSaveButton = new Button(Strings.get("button.presetSave"));
    private final Button presetDeleteButton = new Button(Strings.get("button.presetDelete"));
    private final TextField findField = new TextField();
    private final Button clearFindButton = new Button(Strings.get("button.findClear"));
    private final Button refreshButton = new Button(Strings.get("button.refresh"));
    private final Button helpButton = new Button(Strings.get("button.help"));

    private final Button undoButton = new Button(Strings.get("button.undo"));
    private final Button resetButton = new Button(Strings.get("button.reset"));
    private final Button switchToHideButton = new Button(Strings.get("button.switchToHide"));
    private final Button clearRulesButton = new Button(Strings.get("button.clearAllRules"));
    private final Button checkAllButton = new Button(Strings.get("button.checkAllListed"));
    /** Dock / undock. Its label and action are supplied by whichever surface currently holds the pane. */
    private final Button surfaceButton = new Button();
    private final Button uncheckAllButton = new Button(Strings.get("button.uncheckAllListed"));

    private final Label modeLabel = new Label(Strings.get("label.mode"));
    private final Label cellDisplayLabel = new Label(Strings.get("label.cellDisplay"));
    private final Label presetLabel = new Label(Strings.get("label.presets"));
    private final Label scopeLabel = new Label(Strings.get("label.scope"));
    private final Label findLabel = new Label(Strings.get("label.find"));
    private final HBox imageRow = new HBox(6);
    private final HBox presetRow = new HBox(4);
    /** One instance whose text follows whether a project is open. */
    private final Tooltip presetTooltip = new Tooltip(Strings.get("tooltip.presets"));
    private final HBox scopeRow = new HBox(4);
    private final HBox findRow = new HBox(4);
    private final HBox exactWarningBox = new HBox(6);
    private final HBox statusButtons = new HBox(6);
    private final VBox modeBox = new VBox(2);
    private final VBox cellDisplayBox = new VBox(2);
    private final HBox modeRow = new HBox(12);
    /**
     * Holds the visibility-rule group and the cell-display group side by side.
     *
     * <p>A {@link FlowPane} rather than an {@code HBox} because the request was to use the empty
     * space beside the radios, and how much space that is depends on the surface: a docked tab is
     * narrow, an undocked window is whatever the user dragged it to. FlowPane puts the two groups
     * side by side when they fit and wraps the second onto its own line when they do not, which
     * is the behaviour without a width threshold of our own to get wrong.</p>
     */
    private final FlowPane modeAndDisplayRow = new FlowPane(16, 4);
    private final VBox filterBox = new VBox(4);
    private final HBox filterRow = new HBox(8);
    private final VBox classButtons = new VBox(4);
    private final HBox classButtonRow = new HBox(6);
    private final SplitPane splitPane = new SplitPane();
    private final VBox classPane = new VBox(4);
    private final VBox componentPane = new VBox(4);
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
     * The halo drawn on <i>Check all listed</i> while every object is hidden. One instance, one
     * button: a JavaFX {@code Effect} is attached to a node, so it is not shared.
     */
    private final DropShadow everythingHiddenHalo = new DropShadow(BlurType.GAUSSIAN,
            EVERYTHING_HIDDEN_HALO_COLOR, 10, 0.6, 0, 0);

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
        controller.autoRefreshProperty().bindBidirectional(
                ClassVisibilityPreferences.autoRefreshCountsProperty());
        controller.install();
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
     * <p>Three things make it safe rather than alarming. The automatic snapshot is taken first,
     * so <i>Restore the state from when the panel opened</i> can put back whatever the user had. The status strip says
     * <b>[!] Every object is hidden</b> in words the moment it happens, with <i>Switch to "Hide
     * checked classes"</i> and <i>Reset all</i> beside it, and <i>Check all listed</i> is haloed
     * as the way back. And the close guard undoes the mode on the way out, so a user who never
     * checks anything is returned exactly where they started (finding R2).</p>
     */
    private void applyOpeningState() {
        // Replace the snapshot rather than keeping an older one, so "Restore the state from when
        // the panel opened" is literally true on the second opening as well as the first.
        VisibilityStateStore.capture(options);
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
     * a curiosity. The two mode listeners were retained this way before the combo existed.</p>
     */
    public void dispose() {
        controller.uninstall();
        cellDisplayCombo.valueProperty().unbindBidirectional(options.detectionDisplayModeProperty());
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
     * The ratified R2 guard. If the mode is "show only checked classes" and nothing is checked,
     * every object in every image is hidden -- and because QuPath persists the mode but not the
     * set, that state comes back at the next launch with the panel closed and no visible cause.
     *
     * <p>Fires at tab removal and at QuPath shutdown only, never while the panel is installed:
     * while it is installed the state is one click from visible and the user may be one click
     * from checking the class they were reaching for.</p>
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

    private void buildUi() {
        setPadding(new Insets(6));

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
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setTooltip(presetTooltip);
        presetCombo.setAccessibleText(Strings.get("label.presets"));
        presetSaveButton.setTooltip(new Tooltip(Strings.get("tooltip.button.presetSave")));
        presetDeleteButton.setTooltip(new Tooltip(Strings.get("tooltip.button.presetDelete")));
        HBox.setHgrow(presetCombo, Priority.ALWAYS);
        presetRow.getChildren().setAll(presetLabel, presetCombo, presetSaveButton, presetDeleteButton);
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
        exactWarningBox.setPadding(new Insets(4, 0, 4, 0));
        exactWarningBox.visibleProperty().bind(exactCheck.selectedProperty());
        exactWarningBox.managedProperty().bind(exactCheck.selectedProperty());

        scopeCombo.getItems().setAll(ClassHarvester.Scope.values());
        scopeCombo.setTooltip(new Tooltip(Strings.get("tooltip.scope")));

        cellDisplayCombo.getItems().setAll(OverlayOptions.DetectionDisplayMode.values());
        cellDisplayCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(OverlayOptions.DetectionDisplayMode mode) {
                return mode == null ? "" : cellDisplayLabelFor(mode);
            }

            @Override
            public OverlayOptions.DetectionDisplayMode fromString(String text) {
                // Not editable, so this is never called; returning null beats inventing a parse.
                return null;
            }
        });
        cellDisplayCombo.setTooltip(new Tooltip(Strings.get("tooltip.cellDisplay")));
        cellDisplayCombo.setAccessibleText(Strings.get("label.cellDisplay"));
        cellDisplayLabel.setLabelFor(cellDisplayCombo);
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
        autoRefreshCheck.setTooltip(new Tooltip(Strings.get("tooltip.autoRefresh")));
        refreshButton.setTooltip(new Tooltip(Strings.get("tooltip.refresh")));
        refreshButton.setOnAction(e -> controller.requestHarvest(true));
        refreshButton.visibleProperty().bind(autoRefreshCheck.selectedProperty().not());
        refreshButton.managedProperty().bind(autoRefreshCheck.selectedProperty().not());

        findRow.setAlignment(Pos.CENTER_LEFT);
        scopeRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(4, imageRow, presetRow, modeAndDisplayRow, exactWarningBox, filterBox);
        header.setPadding(new Insets(0, 0, 6, 0));
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
        HBox statusRow = new HBox(6, spinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        VBox statusBox = new VBox(4, statusRow, statusButtons);
        statusBox.setPadding(new Insets(6, 0, 0, 0));

        VBox bottom = new VBox(4, rulesPane, statusBox);
        setBottom(bottom);

        widthProperty().addListener((obs, oldValue, newValue) -> applyProfile(newValue.doubleValue()));
        applyWideProfile();
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

        TableColumn<ClassRow, ClassRow> nameColumn = new TableColumn<>(Strings.get("column.class"));
        nameColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        nameColumn.setCellFactory(col -> new ClassNameCell());
        nameColumn.setComparator(Comparator.comparing(ClassRow::displayName, String.CASE_INSENSITIVE_ORDER));
        Label nameHeader = new Label(Strings.get("column.class"));
        nameHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.class")));
        nameColumn.setGraphic(nameHeader);
        nameColumn.setText("");

        TableColumn<ClassRow, ClassRow> countColumn = new TableColumn<>();
        countColumn.setPrefWidth(84);
        countColumn.setMinWidth(60);
        countColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        countColumn.setCellFactory(col -> new CountCell<>(ClassRow::count));
        countColumn.setComparator(Comparator.comparingLong(ClassRow::count));
        countColumn.setId(COUNT_COLUMN_ID);
        Label countHeader = new Label(Strings.get("column.count"));
        countHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.count")));
        countColumn.setGraphic(countHeader);

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
        affectsColumn.setId(AFFECTS_COLUMN_ID);
        affectsColumn.setGraphic(headerLabel(Strings.get("column.affects"), Strings.get("tooltip.column.affects")));

        // No "Only" column. It cost 52px of a column in which "FoxP3 (Opal 570): 1+: ..." was
        // already being cut off, to save one click -- and once checking a row means "show this"
        // rather than "hide this", solo is no longer a different KIND of operation, just a faster
        // one. It survives as a double-click, a right-click item and the O key.
        classTable.getColumns().setAll(List.of(checkColumn, nameColumn, countColumn,
                affectsColumn));
        installSoloGestures(classTable, ClassRow::displayName, this::soloClass);
        // Dropped when the class names would otherwise have nothing left, and recoverable from
        // the table's own column menu.
        classTable.widthProperty().addListener((obs, oldValue, newValue) ->
                setColumnVisible(classTable, AFFECTS_COLUMN_ID,
                        newValue.doubleValue() >= DROP_AFFECTS_COLUMN_WIDTH));
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        classTable.setTableMenuButtonVisible(true);
        classTable.setMinHeight(120);
        VBox.setVgrow(classTable, Priority.ALWAYS);

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
        // alphabetical sort buries a long derived name among its near-identical siblings.
        countColumn.setSortType(TableColumn.SortType.DESCENDING);
        classTable.getSortOrder().add(countColumn);

        // checkAllButton's tooltip is set by updateBulkButtonState, which swaps it for the
        // everything-hidden wording; setting it here as well would leave two sources for one
        // string and a stale first paint.
        uncheckAllButton.setTooltip(new Tooltip(Strings.get("tooltip.button.uncheckAllListed")));
        includeEmptyCheck.setTooltip(new Tooltip(Strings.get("tooltip.check.includeEmpty")));
        classButtonRow.getChildren().addAll(checkAllButton, uncheckAllButton);
        classButtons.getChildren().addAll(classButtonRow, includeEmptyCheck);

        classPane.getChildren().addAll(classHeader, classTable, classButtons);
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

        TableColumn<ComponentRow, ComponentRow> nameColumn = new TableColumn<>();
        nameColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        nameColumn.setCellFactory(col -> new ComponentNameCell());
        nameColumn.setComparator(Comparator.comparing(ComponentRow::name, String.CASE_INSENSITIVE_ORDER));
        Label nameHeader = new Label(Strings.get("column.component"));
        nameHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.component")));
        nameColumn.setGraphic(nameHeader);

        TableColumn<ComponentRow, ComponentRow> spreadColumn = new TableColumn<>();
        spreadColumn.setPrefWidth(60);
        spreadColumn.setMinWidth(50);
        spreadColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        spreadColumn.setCellFactory(col -> new SpreadCell());
        spreadColumn.setComparator(Comparator.comparingDouble(ComponentRow::coverage));
        Label spreadHeader = new Label(Strings.get("column.spread"));
        spreadHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.spread")));
        spreadColumn.setGraphic(spreadHeader);
        spreadColumn.setId("spreadColumn");

        TableColumn<ComponentRow, ComponentRow> countColumn = new TableColumn<>();
        countColumn.setPrefWidth(84);
        countColumn.setMinWidth(60);
        countColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        countColumn.setCellFactory(col -> new CountCell<>(ComponentRow::count));
        countColumn.setComparator(Comparator.comparingLong(ComponentRow::count));
        countColumn.setId(COUNT_COLUMN_ID);
        Label countHeader = new Label(Strings.get("column.count"));
        countHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.count")));
        countColumn.setGraphic(countHeader);

        componentTable.getColumns().setAll(List.of(checkColumn, nameColumn, spreadColumn, countColumn));
        installSoloGestures(componentTable, ComponentRow::name, this::soloComponent);
        componentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        componentTable.setTableMenuButtonVisible(true);
        componentTable.setMinHeight(120);
        VBox.setVgrow(componentTable, Priority.ALWAYS);

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
        VBox combinationBox = new VBox(2, combinationLabel, anyRadio, allRadio);

        componentPane.getChildren().addAll(componentHeader, componentTable, combinationBox);
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
        ruleTable.setPlaceholder(new Label(Strings.get("placeholder.rules.empty")));
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
        HBox rulesTop = new HBox(6, clearRulesButton);
        rulesTop.setAlignment(Pos.CENTER_RIGHT);
        rulesTop.setPadding(new Insets(0, 0, 4, 0));
        VBox rulesContent = new VBox(4, rulesTop, ruleTable);

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
        setColumnVisible(componentTable, "spreadColumn", width >= DROP_SPREAD_COLUMN_WIDTH);
    }

    private static void setColumnVisible(TableView<?> table, String id, boolean visible) {
        for (TableColumn<?, ?> column : table.getColumns()) {
            if (id.equals(column.getId())) {
                column.setVisible(visible);
                return;
            }
        }
    }

    /**
     * A column header that can carry a tooltip. A {@code TableColumn}'s own text cannot, so every
     * header a user might hover for an explanation has to be a {@code Label} graphic instead.
     *
     * @param text the header text
     * @param tooltipText the hover explanation
     * @return the header node
     */
    private static Label headerLabel(String text, String tooltipText) {
        Label label = new Label(text);
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        label.setTooltip(tooltip);
        return label;
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
        modeBox.getChildren().setAll(modeLabel, modeRow, exactCheck);
        cellDisplayBox.getChildren().setAll(cellDisplayLabel, cellDisplayCombo);
        modeAndDisplayRow.getChildren().setAll(modeBox, cellDisplayBox);
        cellDisplayCombo.setMaxWidth(Region.USE_COMPUTED_SIZE);

        scopeRow.getChildren().setAll(scopeLabel, scopeCombo);
        findRow.getChildren().setAll(findLabel, findField, clearFindButton);
        filterRow.getChildren().setAll(scopeRow, findRow, autoRefreshCheck, refreshButton);
        HBox.setHgrow(findRow, Priority.ALWAYS);
        filterBox.getChildren().setAll(filterRow);

        classButtonRow.getChildren().setAll(checkAllButton, uncheckAllButton);
        classButtons.getChildren().setAll(classButtonRow, includeEmptyCheck);
        checkAllButton.setMaxWidth(Region.USE_COMPUTED_SIZE);
        uncheckAllButton.setMaxWidth(Region.USE_COMPUTED_SIZE);
    }

    private void applyNarrowProfile() {
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(ClassVisibilityPreferences.narrowDividerProperty().get());

        // Stacked, not hidden. Every zone survives the narrow profile; the only concessions are
        // stacking and ellipsis-with-tooltip on long names.
        modeRow.getChildren().clear();
        modeBox.getChildren().setAll(modeLabel, hideRadio, showOnlyRadio, exactCheck);
        // Same two groups, same FlowPane. At this width they will usually wrap onto separate
        // lines by themselves, which is what the other header zones do here too.
        cellDisplayBox.getChildren().setAll(cellDisplayLabel, cellDisplayCombo);
        modeAndDisplayRow.getChildren().setAll(modeBox, cellDisplayBox);
        cellDisplayCombo.setMaxWidth(Double.MAX_VALUE);

        filterRow.getChildren().clear();
        scopeRow.getChildren().setAll(scopeLabel, scopeCombo);
        findRow.getChildren().setAll(findLabel, findField, clearFindButton);
        filterBox.getChildren().setAll(scopeRow, findRow, autoRefreshCheck, refreshButton);

        classButtonRow.getChildren().clear();
        classButtons.getChildren().setAll(checkAllButton, uncheckAllButton, includeEmptyCheck);
        checkAllButton.setMaxWidth(Double.MAX_VALUE);
        uncheckAllButton.setMaxWidth(Double.MAX_VALUE);
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

        // Bidirectional, and that is the whole wiring: the combo is a second face for a value
        // QuPath owns and persists (OverlayOptions.java:141), so it must follow the View menu and
        // the viewer's right-click menu as readily as it drives them. Nothing here keeps a copy,
        // and no preference of ours shadows it.
        cellDisplayCombo.valueProperty().bindBidirectional(options.detectionDisplayModeProperty());

        scopeCombo.getSelectionModel().select(ClassVisibilityPreferences.scopeProperty().get());
        scopeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                ClassVisibilityPreferences.scopeProperty().set(newValue);
                controller.setScope(newValue);
            }
        });

        autoRefreshCheck.selectedProperty().bindBidirectional(
                ClassVisibilityPreferences.autoRefreshCountsProperty());
        autoRefreshCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                countsStale = false;
                controller.requestHarvest(true);
            }
            updateHeaders();
        });

        includeEmptyCheck.selectedProperty().bindBidirectional(
                ClassVisibilityPreferences.includeEmptyClassesProperty());
        includeEmptyCheck.selectedProperty().addListener((obs, oldValue, newValue) -> rebuildRows());

        findField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter(newValue));

        wirePresets();

        anyRadio.setOnAction(e -> setCombination(VisibilityRuleModel.Combination.ANY));
        allRadio.setOnAction(e -> setCombination(VisibilityRuleModel.Combination.ALL));

        checkAllButton.setOnAction(e -> {
            pushUndo(Strings.get("action.checkAllListed"));
            model.checkClasses(filteredClasses.stream().map(ClassRow::pathClass).toList());
        });
        uncheckAllButton.setOnAction(e -> {
            pushUndo(Strings.get("action.uncheckAllListed"));
            model.uncheckClasses(filteredClasses.stream().map(ClassRow::pathClass).toList());
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

    /**
     * @param mode a cell display mode
     * @return QuPath's own label for it, verbatim from {@code qupath-gui-strings.properties}
     *         ({@code OverlayActions.showCell*}). Copied rather than read from QuPath's bundle
     *         because that bundle is not public API -- but copied WORD FOR WORD, because a combo
     *         box and a menu that name the same four options differently is worse than either
     *         wording alone.
     */
    private static String cellDisplayLabelFor(OverlayOptions.DetectionDisplayMode mode) {
        return switch (mode) {
            case BOUNDARIES_ONLY -> Strings.get("cellDisplay.boundaries");
            case NUCLEI_ONLY -> Strings.get("cellDisplay.nuclei");
            case NUCLEI_AND_BOUNDARIES -> Strings.get("cellDisplay.both");
            case CENTROIDS -> Strings.get("cellDisplay.centroids");
        };
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
        updateBulkButtonState();
        // The name cells render differently with a filter on -- matched text is bold -- and a
        // predicate change does not by itself re-render a row that survived the change.
        classTable.refresh();
        componentTable.refresh();
    }

    /**
     * Both bulk buttons act on the currently listed rows, so with nothing listed -- an empty
     * image, or a filter that matches no class -- they have nothing to act on. Disabled beats a
     * click that appears to be ignored; the list's placeholder says why the list is empty.
     */
    private void updateBulkButtonState() {
        boolean nothingListed = filteredClasses.isEmpty();
        checkAllButton.setDisable(nothingListed);
        uncheckAllButton.setDisable(nothingListed);
        // The way out of the blank viewer, marked on the control that takes it. Not while the
        // button is disabled: a halo on a control that cannot be clicked points at a dead end.
        boolean everythingHidden = !nothingListed && isEverythingHidden();
        checkAllButton.setEffect(everythingHidden ? everythingHiddenHalo : null);
        checkAllButton.setTooltip(new Tooltip(everythingHidden
                ? Strings.get("tooltip.button.checkAllListed.allHidden")
                : Strings.get("tooltip.button.checkAllListed")));
        checkAllButton.setAccessibleText(everythingHidden
                ? Strings.get("accessible.checkAllListed.allHidden")
                : Strings.get("button.checkAllListed"));
    }

    /**
     * @return whether the current state hides every object in every image: "show only checked
     *         classes" with nothing checked. This is the same condition the status strip
     *         reports as {@code status.s2}, computed from the same two facts, so the halo and
     *         the sentence explaining it cannot disagree.
     */
    private boolean isEverythingHidden() {
        return options.getSelectedClassVisibilityMode()
                        == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED
                && model.activeRuleCount() == 0;
    }

    private void updateHeaders() {
        int classTotal = classRows.size();
        int classShown = filteredClasses.size();
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
        String countHeaderText = countsStale || !autoRefreshCheck.isSelected()
                ? Strings.get("column.count.stale")
                : Strings.get("column.count");
        setCountHeaderText(classTable, countHeaderText);
        setCountHeaderText(componentTable, countHeaderText);
    }

    private static void setCountHeaderText(TableView<?> table, String text) {
        // By id, not by position: both tables let the user reorder their columns, and addressing
        // "the last column" would eventually put "Count (stale)" over the wrong header.
        for (TableColumn<?, ?> column : table.getColumns()) {
            if (COUNT_COLUMN_ID.equals(column.getId()) && column.getGraphic() instanceof Label label) {
                label.setText(text);
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
            VBox box = new VBox(6);
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
        updateBulkButtonState();
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
            String status;
            if (exactCheck.isSelected() && ruleSource != VisibilityRuleModel.RuleSource.CLASS) {
                status = Strings.get("rules.status.exactOnly");
            } else if (present.contains(entry == null ? PathClass.NULL_CLASS : entry)) {
                status = Strings.get("rules.status.listed");
            } else if (ruleSource == VisibilityRuleModel.RuleSource.COMPONENTS_ALL) {
                status = Strings.get("rules.status.composite");
            } else {
                status = Strings.get("rules.status.notInImage");
            }
            rows.add(new RuleRow(entry, name, source, status));
        }
        ruleRows.setAll(rows);
        // Nothing to clear is not the same as a broken button. The table's own placeholder --
        // "No rules are active." -- is the explanation sitting right beside it.
        clearRulesButton.setDisable(rows.isEmpty());
        int count = rows.size();
        rulesPane.setText(count == 0
                ? Strings.get("rules.none")
                : count == 1 ? Strings.get("rules.one") : Strings.format("rules.many", count));
    }

    private void updateStatus() {
        if (harvesting && currentImageName != null) {
            statusLabel.setText(Strings.format("status.s6", currentImageName));
            statusLabel.setFont(Font.getDefault());
            statusButtons.getChildren().clear();
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
        int orphans = countOrphanRules();
        if (orphans > 0 && count > 0 && !noImage) {
            text = text + " " + (orphans == 1
                    ? Strings.get("status.s7.one")
                    : Strings.format("status.s7.many", orphans));
        }
        if (coverageNote != null) {
            text = text + " " + coverageNote;
        }
        statusLabel.setText(text);
        // Severity is carried by the [OK] / [i] / [!] text markers and by weight, never by colour
        // alone -- an accessibility requirement, and it keeps the panel theme-neutral.
        statusLabel.setFont(alarm
                ? Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize())
                : Font.getDefault());
        if (undoSlot != null) {
            undoButton.setText(Strings.format("button.undo.named", undoSlot.actionLabel()));
            buttons.add(0, undoButton);
        }
        statusButtons.getChildren().setAll(buttons);
        statusLabel.setAccessibleText(text);
    }

    private int countOrphanRules() {
        Set<PathClass> present = new LinkedHashSet<>(census.classes());
        int orphans = 0;
        for (PathClass entry : model.activeRules()) {
            PathClass key = entry == null ? PathClass.NULL_CLASS : entry;
            if (!present.contains(key)) {
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
