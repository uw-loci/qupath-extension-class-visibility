package qupath.ext.classvisibility.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.core.ClassCensus;
import qupath.ext.classvisibility.core.ClassHarvester;
import qupath.ext.classvisibility.core.ClassVisibilityController;
import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.ext.classvisibility.core.VisibilityStateStore;
import qupath.ext.classvisibility.preferences.ClassVisibilityPreferences;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.objects.classes.PathClass;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The whole Class Visibility user interface, as a self-contained {@link BorderPane}.
 *
 * <p>Owns no {@code Stage}, no modality and no window geometry. QuPath owns the window in both
 * profiles -- docked as an analysis-pane tab, or undocked by
 * {@code FXUtils.makeTabUndockable} -- which is what keeps multi-monitor placement, HiDPI
 * positioning and geometry persistence out of this extension entirely.</p>
 *
 * <p>Two profiles from one pane, switched on the pane's own width with hysteresis so the layout
 * does not thrash while the user drags the analysis-pane divider.</p>
 */
public final class ClassVisibilityPane extends BorderPane implements ClassVisibilityController.View {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityPane.class);

    /** Above this pane width, lay out wide (side-by-side). */
    private static final double WIDE_THRESHOLD = 640;

    /** Below this pane width, lay out narrow (stacked). The gap is deliberate hysteresis. */
    private static final double NARROW_THRESHOLD = 580;

    /** Below this width the component spread column is dropped; recoverable from the column menu. */
    private static final double DROP_SPREAD_COLUMN_WIDTH = 360;

    /** Coverage emphasis is suppressed entirely below this many classes. */
    private static final int COVERAGE_EMPHASIS_MIN_CLASSES = 5;

    private static final NumberFormat COUNTS = NumberFormat.getIntegerInstance();

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
    private final TextField findField = new TextField();
    private final Button clearFindButton = new Button("x");
    private final Button refreshButton = new Button(Strings.get("button.refresh"));

    private final Button undoButton = new Button(Strings.get("button.undo"));
    private final Button resetButton = new Button(Strings.get("button.reset"));
    private final Button switchToHideButton = new Button(Strings.get("button.switchToHide"));
    private final Button clearRulesButton = new Button(Strings.get("button.clearAllRules"));
    private final Button checkAllButton = new Button(Strings.get("button.checkAllListed"));
    private final Button uncheckAllButton = new Button(Strings.get("button.uncheckAllListed"));

    private final Label modeLabel = new Label(Strings.get("label.mode"));
    private final Label scopeLabel = new Label(Strings.get("label.scope"));
    private final Label findLabel = new Label(Strings.get("label.find"));
    private final HBox scopeRow = new HBox(4);
    private final HBox findRow = new HBox(4);
    private final HBox exactWarningBox = new HBox(6);
    private final HBox statusButtons = new HBox(6);
    private final VBox modeBox = new VBox(2);
    private final HBox modeRow = new HBox(12);
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
    private boolean countsStale = false;
    private boolean updatingControls = false;

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
        this.model = new VisibilityRuleModel(options::selectedClassesProperty);
        this.model.setChangeListener(this::onModelChanged);
        this.model.setCombination(ClassVisibilityPreferences.combinationProperty().get());

        buildUi();
        wireControls();
        wireKeyboard();

        controller.setScope(ClassVisibilityPreferences.scopeProperty().get());
        controller.autoRefreshProperty().bindBidirectional(
                ClassVisibilityPreferences.autoRefreshCountsProperty());
        controller.install();
        refreshRuleDependentUi();
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

    /** Detach every listener. Called when the tab is removed and at QuPath shutdown. */
    public void dispose() {
        controller.uninstall();
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

        imageLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        imageLabel.setMaxWidth(Double.MAX_VALUE);

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
        findField.setPromptText(Strings.get("prompt.find"));
        findField.setTooltip(new Tooltip(Strings.get("tooltip.find")));
        HBox.setHgrow(findField, Priority.ALWAYS);
        clearFindButton.setTooltip(new Tooltip(Strings.get("tooltip.findClear")));
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

        VBox header = new VBox(4, imageLabel, modeBox, exactWarningBox, filterBox);
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

        TableColumn<ClassRow, ClassRow> onlyColumn = new TableColumn<>(Strings.get("column.only"));
        onlyColumn.setPrefWidth(52);
        onlyColumn.setMinWidth(52);
        onlyColumn.setMaxWidth(52);
        onlyColumn.setSortable(false);
        onlyColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        onlyColumn.setCellFactory(col -> new ClassOnlyCell());

        TableColumn<ClassRow, String> nameColumn = new TableColumn<>(Strings.get("column.class"));
        nameColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().displayName()));
        nameColumn.setCellFactory(col -> new NameCell<>());
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
        Label countHeader = new Label(Strings.get("column.count"));
        countHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.count")));
        countColumn.setGraphic(countHeader);

        classTable.getColumns().setAll(List.of(checkColumn, onlyColumn, nameColumn, countColumn));
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

        checkAllButton.setTooltip(new Tooltip(Strings.get("tooltip.button.checkAllListed")));
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

        TableColumn<ComponentRow, ComponentRow> onlyColumn = new TableColumn<>(Strings.get("column.only"));
        onlyColumn.setPrefWidth(52);
        onlyColumn.setMinWidth(52);
        onlyColumn.setMaxWidth(52);
        onlyColumn.setSortable(false);
        onlyColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        onlyColumn.setCellFactory(col -> new ComponentOnlyCell());

        TableColumn<ComponentRow, String> nameColumn = new TableColumn<>();
        nameColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name()));
        nameColumn.setCellFactory(col -> new NameCell<>());
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
        Label countHeader = new Label(Strings.get("column.count"));
        countHeader.setTooltip(new Tooltip(Strings.get("tooltip.column.count")));
        countColumn.setGraphic(countHeader);

        componentTable.getColumns().setAll(List.of(checkColumn, onlyColumn, nameColumn, spreadColumn, countColumn));
        componentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        componentTable.setTableMenuButtonVisible(true);
        componentTable.setMinHeight(120);
        VBox.setVgrow(componentTable, Priority.ALWAYS);

        SortedList<ComponentRow> sorted = new SortedList<>(filteredComponents);
        sorted.comparatorProperty().bind(componentTable.comparatorProperty());
        componentTable.setItems(sorted);
        // Default sort: discriminating power first. Count-descending would sort the degenerate,
        // near-universal component to row one, which is exactly the trap the column exists for.
        spreadColumn.setSortType(TableColumn.SortType.ASCENDING);
        componentTable.getSortOrder().add(spreadColumn);

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
        boolean showSpread = width >= DROP_SPREAD_COLUMN_WIDTH;
        for (TableColumn<ComponentRow, ?> column : componentTable.getColumns()) {
            if ("spreadColumn".equals(column.getId())) {
                column.setVisible(showSpread);
            }
        }
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
        options.selectedClassVisibilityModeProperty().addListener((obs, oldValue, newValue) -> {
            updatingControls = true;
            try {
                hideRadio.setSelected(newValue == OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
                showOnlyRadio.setSelected(newValue == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
            } finally {
                updatingControls = false;
            }
            refreshRuleDependentUi();
        });

        exactCheck.setSelected(options.getUseExactSelectedClasses());
        exactCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingControls) {
                beforeMutation();
                options.setUseExactSelectedClasses(Boolean.TRUE.equals(newValue));
            }
            componentPane.setDisable(Boolean.TRUE.equals(newValue));
            refreshRuleDependentUi();
        });
        options.useExactSelectedClassesProperty().addListener((obs, oldValue, newValue) -> {
            updatingControls = true;
            try {
                exactCheck.setSelected(Boolean.TRUE.equals(newValue));
            } finally {
                updatingControls = false;
            }
        });
        componentPane.setDisable(exactCheck.isSelected());

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
        KeyCombination find = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        KeyCombination undoCombo = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (find.match(event)) {
                findField.requestFocus();
                findField.selectAll();
                event.consume();
            } else if (undoCombo.match(event)) {
                undo();
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
        VisibilityStateStore.captureIfAbsent(options);
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
        beforeMutation();
        soloedClass = null;
        model.setClassSelected(row.pathClass(), selected);
    }

    private void toggleComponent(ComponentRow row, boolean selected) {
        beforeMutation();
        soloedComponent = null;
        model.setComponentSelected(row.name(), selected);
        if (selected) {
            noteCoverageIfSwamping(row);
        } else {
            coverageNote = null;
        }
    }

    private void soloClass(ClassRow row) {
        if (soloedClass != null && soloedClass == row.pathClass()) {
            undo();
            return;
        }
        pushUndo(Strings.format("action.showOnly", row.displayName()));
        soloedComponent = null;
        soloedClass = row.pathClass();
        model.soloClass(row.pathClass());
        options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
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
        options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.SHOW_SELECTED);
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
            // not flash blank, but disable them so nothing can be acted on by mistake.
            classTable.setDisable(true);
            componentTable.setDisable(true);
        } else {
            countsStale = true;
        }
        spinner.setVisible(true);
        spinner.setManaged(true);
        updateStatus();
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
    }

    private void updateHeaders() {
        int classTotal = classRows.size();
        int classShown = filteredClasses.size();
        int componentTotal = componentRows.size();
        int componentShown = filteredComponents.size();
        boolean filtered = !findField.getText().isEmpty();
        if (wideProfile) {
            classHeader.setText(filtered
                    ? Strings.format("header.classes.wide.filtered", classShown, classTotal)
                    : Strings.format("header.classes.wide", classTotal));
            componentHeader.setText(filtered
                    ? Strings.format("header.components.wide.filtered", componentShown, componentTotal)
                    : Strings.format("header.components.wide", componentTotal));
        } else {
            classHeader.setText(filtered
                    ? Strings.format("header.classes.narrow.filtered", classShown, classTotal)
                    : Strings.format("header.classes.narrow", classTotal));
            componentHeader.setText(filtered
                    ? Strings.format("header.components.narrow.filtered", componentShown, componentTotal)
                    : Strings.format("header.components.narrow", componentTotal));
        }
        String countHeaderText = countsStale || !autoRefreshCheck.isSelected()
                ? Strings.get("column.count.stale")
                : Strings.get("column.count");
        setCountHeaderText(classTable, countHeaderText);
        setCountHeaderText(componentTable, countHeaderText);
    }

    private static void setCountHeaderText(TableView<?> table, String text) {
        List<? extends TableColumn<?, ?>> columns = table.getColumns();
        TableColumn<?, ?> last = columns.isEmpty() ? null : columns.get(columns.size() - 1);
        if (last != null && last.getGraphic() instanceof Label label) {
            label.setText(text);
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
        combinationLabel.setDisable(disabled);
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
            String name = displayName(entry);
            String source = switch (model.sourceOf(entry)) {
                case CLASS -> Strings.get("rules.source.class");
                case COMPONENTS_ANY -> Strings.get("rules.source.componentsAny");
                case COMPONENTS_ALL -> Strings.get("rules.source.componentsAll");
                case ELSEWHERE -> Strings.get("rules.source.elsewhere");
            };
            String status;
            if (exactCheck.isSelected() && model.sourceOf(entry) != VisibilityRuleModel.RuleSource.CLASS) {
                status = Strings.get("rules.status.exactOnly");
            } else if (present.contains(entry == null ? PathClass.NULL_CLASS : entry)) {
                status = Strings.get("rules.status.listed");
            } else if (model.sourceOf(entry) == VisibilityRuleModel.RuleSource.COMPONENTS_ALL) {
                status = Strings.get("rules.status.composite");
            } else {
                status = Strings.get("rules.status.notInImage");
            }
            rows.add(new RuleRow(entry, name, source, status));
        }
        ruleRows.setAll(rows);
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
            String name = soloedClass != null ? displayName(soloedClass) : soloedComponent;
            text = Strings.format("status.s5", name);
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

    private final class ClassOnlyCell extends TableCell<ClassRow, ClassRow> {

        private final Button button = new Button(Strings.get("column.only"));

        private ClassOnlyCell() {
            button.setOnAction(e -> {
                ClassRow row = getItem();
                if (row != null) {
                    soloClass(row);
                }
            });
            button.setTooltip(new Tooltip(Strings.get("tooltip.row.class.only")));
        }

        @Override
        protected void updateItem(ClassRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            button.setAccessibleText(Strings.format("action.showOnly", item.displayName()));
            setGraphic(button);
        }
    }

    private final class ComponentOnlyCell extends TableCell<ComponentRow, ComponentRow> {

        private final Button button = new Button(Strings.get("column.only"));

        private ComponentOnlyCell() {
            button.setOnAction(e -> {
                ComponentRow row = getItem();
                if (row != null) {
                    soloComponent(row);
                }
            });
            button.setTooltip(new Tooltip(Strings.get("tooltip.row.component.only")));
        }

        @Override
        protected void updateItem(ComponentRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            button.setAccessibleText(Strings.format("action.showOnly", item.name()));
            setGraphic(button);
        }
    }

    /** Ellipsis-with-tooltip name cell; the full name is always reachable. */
    private static final class NameCell<S> extends TableCell<S, String> {

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(item);
            setTooltip(new Tooltip(item));
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
            setText(harvesting ? "--" : COUNTS.format(extractor.applyAsLong(item)));
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
