package qupath.ext.classvisibility;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.core.VisibilityStateStore;
import qupath.ext.classvisibility.preferences.ClassVisibilityPreferences;
import qupath.ext.classvisibility.ui.ClassVisibilityPane;
import qupath.ext.classvisibility.ui.ClassVisibilityStage;
import qupath.ext.classvisibility.ui.Strings;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.viewer.OverlayOptions;

/**
 * QuPath extension entry point for the Class Visibility panel.
 *
 * <p><b>The panel is a floating window first, and a tab only if the user asks.</b> Installing
 * this extension adds nothing to anybody's analysis pane -- that pane already has five tabs, and
 * permanently taking a sixth from every user who installs an extension is not ours to do.</p>
 *
 * <p>Three states, every transition user-driven:</p>
 * <pre>
 *   CLOSED  --[toolbar button]--&gt;  FLOATING (a Stage)  --[Dock as tab]--&gt;  DOCKED (a Tab)
 *      ^                                |                                        |
 *      +--------[close]-----------------+           [Undock to window] &lt;---------+
 * </pre>
 *
 * <p>Docking and undocking <b>re-parent the same {@link ClassVisibilityPane} instance</b>; nothing
 * is rebuilt, so the user's rules, filter text, sort order and scroll position survive the move.
 * That is the whole reason the Pane is self-contained and surface-agnostic.</p>
 *
 * <p>Once docked, {@code FXUtils.makeTabUndockable(tab)} is applied so QuPath's own undock
 * gesture works too. That is a bonus, not the mechanism -- and it is a second route out of DOCKED
 * that this class does not control, so the state tracking tolerates QuPath moving the tab behind
 * its back ({@code tab.getTabPane() == null} detects it).</p>
 *
 * <p>The analysis {@code TabPane}'s closing policy is {@code UNAVAILABLE} and must not be
 * changed -- doing so would make QuPath's own five tabs closable as a side effect of installing
 * this extension. A docked panel is therefore closed from the toolbar toggle, whose pressed state
 * tracks whether the panel is open at all.</p>
 */
public class ClassVisibilityExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityExtension.class);

    private static final String EXTENSION_NAME = Strings.get("name");
    private static final String EXTENSION_DESCRIPTION = Strings.get("description");
    private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.7.0");

    private static final GitHubRepo EXTENSION_REPOSITORY =
            GitHubRepo.create(EXTENSION_NAME, "uw-loci", "qupath-extension-class-visibility");

    /** Re-entry guard. Installing twice would create two panels fighting over one overlay set. */
    private boolean installed = false;

    private QuPathGUI qupath;

    /** The one Pane instance for the session. Null exactly when the panel is CLOSED. */
    private ClassVisibilityPane pane;

    /** Non-null exactly in the FLOATING state. */
    private ClassVisibilityStage window;

    /** Non-null exactly in the DOCKED state, whether or not QuPath has undocked it since. */
    private Tab tab;

    private ToggleButton toolbarButton;

    /**
     * True while a dock or undock is in flight. Both operations hide a Stage or remove a Tab, and
     * without this the teardown handlers would read those as the user closing the panel and
     * dispose the Pane mid-move.
     */
    private boolean reparenting = false;

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    @Override
    public Version getQuPathVersion() {
        return EXTENSION_QUPATH_VERSION;
    }

    @Override
    public GitHubRepo getRepository() {
        return EXTENSION_REPOSITORY;
    }

    @Override
    public void installExtension(QuPathGUI qupathGui) {
        if (installed) {
            logger.debug("ClassVisibilityExtension.installExtension called twice; ignoring");
            return;
        }
        installed = true;
        this.qupath = qupathGui;

        logger.info("Installing extension: {}", EXTENSION_NAME);
        ClassVisibilityPreferences.installPreferences();

        Platform.runLater(() -> {
            try {
                registerMenuItems();
                installShutdownGuard();
                // Defer the toolbar lookup so QuPath finishes building its toolbar first.
                Platform.runLater(() -> Platform.runLater(() -> tryInsertToolbarButton(0)));
            } catch (Exception ex) {
                logger.warn("Failed to install Class Visibility UI hooks: {}", ex.getMessage(), ex);
            }
        });
        // Nothing is shown here. The panel starts CLOSED in every session -- no window, and above
        // all no tab.
    }

    private void registerMenuItems() {
        var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
        MenuItem showItem = new MenuItem(Strings.get("menu.show"));
        showItem.setOnAction(e -> showPanel());
        MenuItem helpItem = new MenuItem(Strings.get("menu.help"));
        helpItem.setOnAction(e -> showHelp());
        menu.getItems().addAll(showItem, helpItem);
        logger.info("Registered menu items: Extensions > {}", EXTENSION_NAME);
    }

    /**
     * The R2 guard at QuPath shutdown. "Show only checked classes" persists across restarts but
     * the rule set does not, so leaving that pair behind means every object in every image is
     * invisible at the next launch, with no panel open and no obvious cause.
     */
    private void installShutdownGuard() {
        Stage stage = qupath.getStage();
        if (stage == null) {
            return;
        }
        stage.addEventHandler(WindowEvent.WINDOW_HIDING, e -> {
            if (ClassVisibilityPane.applyCloseGuard(OverlayOptions.getSharedInstance())) {
                logger.info("Class visibility guard fired at shutdown");
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Surface state machine
    // ------------------------------------------------------------------------------------------

    /** @return true when the panel exists in either surface. */
    private boolean isOpen() {
        return pane != null;
    }

    /** @return true when the panel lives in a Tab, whether or not QuPath has undocked that tab. */
    private boolean isDocked() {
        return tab != null;
    }

    /**
     * Open the panel, or bring the existing one forward. From CLOSED this creates the floating
     * window -- never a tab.
     */
    private synchronized void showPanel() {
        if (!isOpen()) {
            pane = new ClassVisibilityPane(qupath);
            attachToWindow();
            return;
        }
        if (isDocked()) {
            if (tab.getTabPane() == null) {
                // QuPath's own undock gesture moved the tab into a window of its own.
                raisePaneWindow();
            } else {
                // A docked tab inside a collapsed analysis pane is invisible, and selecting it
                // alone would look like the button did nothing.
                if (!qupath.showAnalysisPaneProperty().get()) {
                    qupath.showAnalysisPaneProperty().set(true);
                }
                tab.getTabPane().getSelectionModel().select(tab);
            }
        } else if (window != null) {
            window.show();
        }
        syncToolbarState();
    }

    /** Put the Pane in a floating window and wire that surface's gating and dock control. */
    private void attachToWindow() {
        window = new ClassVisibilityStage(qupath.getStage(), pane);
        // A Pane in a visible window is always on screen, so its updates gate on nothing more
        // than whether the window is showing.
        pane.visibleForUpdatesProperty().unbind();
        pane.visibleForUpdatesProperty().bind(window.getStage().showingProperty());
        pane.setSurfaceToggle(Strings.get("menu.dockAsTab"),
                Strings.get("tooltip.menu.dockAsTab"), this::dockAsTab);
        window.getStage().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> {
            if (!reparenting) {
                closePanel();
            }
        });
        window.show();
        Platform.runLater(pane::focusFind);
        syncToolbarState();
        logger.info("Opened the Class visibility window");
    }

    /**
     * Move the Pane out of its window and into a new tab in QuPath's analysis pane. The Pane
     * instance is re-parented, not rebuilt, so nothing the user has set is lost.
     */
    private synchronized void dockAsTab() {
        if (!isOpen() || isDocked() || window == null) {
            return;
        }
        TabPane tabPane = qupath.getAnalysisTabPane();
        if (tabPane == null) {
            // Not defensive decoration: getAnalysisTabPane() returns null whenever QuPath's main
            // pane manager has not been built. The panel stays in its window, which still works.
            Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("notify.noTabPane"));
            return;
        }
        reparenting = true;
        try {
            ClassVisibilityPane released = window.releasePane();
            window = null;
            attachToTab(tabPane, released);
        } finally {
            reparenting = false;
        }
        logger.info("Docked the Class visibility panel as an analysis-pane tab");
    }

    private void attachToTab(TabPane tabPane, ClassVisibilityPane content) {
        tab = new Tab(Strings.get("tab.text"), content);
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(Bindings.createStringBinding(
                () -> Strings.format("tab.tooltip", content.titleProperty().get()),
                content.titleProperty()));
        tab.setTooltip(tooltip);

        // Update gating for the docked surface. Both halves of QuPath's own idiom matter:
        // getTabPane() == null IS the QuPath-undocked case, and dropping it would freeze the
        // panel in that window. The showAnalysisPane term closes the leak QuPath's own
        // "TODO: Handle analysis pane being entirely hidden" admits to, using core's property
        // rather than a size heuristic of ours.
        BooleanBinding visibleForUpdates = Bindings.createBooleanBinding(
                () -> tab.getTabPane() == null
                        || (tab.isSelected() && qupath.showAnalysisPaneProperty().get()),
                tab.tabPaneProperty(), tab.selectedProperty(), qupath.showAnalysisPaneProperty());
        content.visibleForUpdatesProperty().unbind();
        content.visibleForUpdatesProperty().bind(visibleForUpdates);

        // QuPath can undock this tab itself. While it has, our own undock control would be
        // meaningless -- the panel is already in a window, and stealing the content would leave
        // QuPath holding an empty stage.
        tab.tabPaneProperty().addListener((obs, oldValue, newValue) -> {
            updateSurfaceToggleForTab();
            syncToolbarState();
        });
        updateSurfaceToggleForTab();

        tabPane.getTabs().add(tab);
        FXUtils.makeTabUndockable(tab);
        if (!qupath.showAnalysisPaneProperty().get()) {
            qupath.showAnalysisPaneProperty().set(true);
        }
        tabPane.getSelectionModel().select(tab);
        syncToolbarState();
    }

    private void updateSurfaceToggleForTab() {
        if (pane == null || tab == null) {
            return;
        }
        if (tab.getTabPane() == null) {
            pane.hideSurfaceToggle();
        } else {
            pane.setSurfaceToggle(Strings.get("menu.undockToWindow"),
                    Strings.get("tooltip.menu.undockToWindow"), this::undockToWindow);
        }
    }

    /** Move the Pane out of its tab and back into a floating window. */
    private synchronized void undockToWindow() {
        if (!isOpen() || !isDocked()) {
            return;
        }
        TabPane tabPane = tab.getTabPane();
        if (tabPane == null) {
            // QuPath already undocked it into a window of its own; ours would be a second one.
            raisePaneWindow();
            return;
        }
        reparenting = true;
        try {
            ClassVisibilityPane content = (ClassVisibilityPane) tab.getContent();
            tab.setContent(null);
            tab.setTooltip(null);
            tabPane.getTabs().remove(tab);
            tab = null;
            pane = content;
            attachToWindow();
        } finally {
            reparenting = false;
        }
        logger.info("Undocked the Class visibility panel into its own window");
    }

    /**
     * Close the panel from whichever surface it is in and run the R2 guard.
     *
     * <p>"Show only checked classes" with an empty rule set hides every object in every image,
     * and QuPath persists that mode while not persisting the set -- so the state comes back at
     * the next launch with no panel open and no visible cause.</p>
     */
    private synchronized void closePanel() {
        if (!isOpen()) {
            return;
        }
        ClassVisibilityPane closing = pane;
        pane = null;

        if (tab != null) {
            TabPane tabPane = tab.getTabPane();
            tab.setContent(null);
            tab.setTooltip(null);
            if (tabPane != null) {
                tabPane.getTabs().remove(tab);
            }
            tab = null;
        }
        if (window != null) {
            reparenting = true;
            try {
                window.hide();
            } finally {
                reparenting = false;
            }
            window = null;
        }

        closing.visibleForUpdatesProperty().unbind();
        boolean guarded = closing.applyCloseGuard();
        closing.dispose();
        if (guarded) {
            Dialogs.showInfoNotification(Strings.get("notify.title"), Strings.get("notify.guard"));
        }
        syncToolbarState();
        logger.info("Closed the Class visibility panel");
    }

    /** Raise whichever window currently holds the Pane -- ours, or QuPath's undocked tab window. */
    private void raisePaneWindow() {
        if (pane == null || pane.getScene() == null) {
            return;
        }
        Window paneWindow = pane.getScene().getWindow();
        if (paneWindow instanceof Stage stage && stage != qupath.getStage()) {
            stage.toFront();
            stage.requestFocus();
        }
    }

    /** @return whether the panel is currently on screen where the user can see it. */
    private boolean isPanelVisible() {
        if (!isOpen()) {
            return false;
        }
        if (isDocked()) {
            return tab.getTabPane() == null
                    || (tab.isSelected() && qupath.showAnalysisPaneProperty().get());
        }
        return window != null && window.isShowing();
    }

    private void syncToolbarState() {
        if (toolbarButton != null) {
            toolbarButton.setSelected(isOpen());
            toolbarButton.setTooltip(new Tooltip(isOpen()
                    ? Strings.get("tooltip.toolbar.shown")
                    : Strings.get("tooltip.toolbar.hidden")));
        }
    }

    private void showHelp() {
        Dialogs.showMessageDialog(Strings.get("help.title"), Strings.get("help.body"));
    }

    // ------------------------------------------------------------------------------------------
    // Toolbar button
    // ------------------------------------------------------------------------------------------

    /**
     * Best-effort toolbar-button insertion. Walks the toolbar looking for the brightness/contrast
     * button by ControlsFX action identity and inserts immediately after it. Retries up to ten
     * times to accommodate toolbar build sequencing on slow startups; if it never appears, the
     * menu item still works.
     */
    private void tryInsertToolbarButton(int attempt) {
        ToolBar toolBar = qupath.getToolBar();
        if (toolBar == null) {
            logger.warn("Cannot inject Class Visibility toolbar button: toolbar is null");
            return;
        }
        Action bcAction = brightnessContrastAction();
        if (bcAction == null) {
            if (attempt < 10) {
                Platform.runLater(() -> tryInsertToolbarButton(attempt + 1));
                return;
            }
            logger.warn("Brightness/Contrast action not found after {} attempts; skipping the "
                    + "Class Visibility toolbar button (the Extensions menu item still works)", attempt);
            return;
        }
        int index = findActionButtonIndex(toolBar, bcAction);
        if (index < 0) {
            if (attempt < 10) {
                Platform.runLater(() -> tryInsertToolbarButton(attempt + 1));
                return;
            }
            logger.warn("Brightness/Contrast toolbar button not found after {} attempts; skipping the "
                    + "Class Visibility toolbar button (the Extensions menu item still works)", attempt);
            return;
        }
        toolbarButton = buildToolbarButton();
        toolBar.getItems().add(index + 1, toolbarButton);
        syncToolbarState();
        logger.info("Inserted Class Visibility toolbar button at index {}", index + 1);
    }

    private Action brightnessContrastAction() {
        try {
            CommonActions actions = qupath.getCommonActions();
            return actions != null ? actions.BRIGHTNESS_CONTRAST : null;
        } catch (Exception ex) {
            logger.debug("CommonActions not yet available: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Walk the toolbar and return the index of the button whose stored Action matches. QuPath
     * stores the action under {@link ActionTools#getActionProperty(Node)}, not under the raw
     * ControlsFX key -- an assumption that was wrong the first time this pattern was written.
     */
    private static int findActionButtonIndex(ToolBar toolBar, Action action) {
        var items = toolBar.getItems();
        for (int i = 0; i < items.size(); i++) {
            ButtonBase b = findButton(items.get(i));
            if (b == null) {
                continue;
            }
            if (ActionTools.getActionProperty(b) == action) {
                return i;
            }
        }
        return -1;
    }

    private static ButtonBase findButton(Node node) {
        if (node instanceof ButtonBase b) {
            return b;
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                ButtonBase found = findButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ToggleButton buildToolbarButton() {
        ToggleButton button = new ToggleButton();
        button.setTooltip(new Tooltip(Strings.get("tooltip.toolbar.hidden")));
        button.setAccessibleText(Strings.get("tooltip.toolbar.hidden"));
        button.getStyleClass().add("toolbar-button");
        button.setGraphic(buildIcon(button));
        button.setOnAction(e -> {
            // The pressed state means "the panel is open", in either surface, so the toolbar
            // always tells the truth. Pressing it opens the panel, brings a hidden-but-open one
            // forward, or closes a visible one -- which is the only way to close a DOCKED panel,
            // since the analysis TabPane forbids close buttons.
            if (!isOpen()) {
                showPanel();
            } else if (isPanelVisible()) {
                closePanel();
            } else {
                showPanel();
            }
            syncToolbarState();
        });
        addContextMenuDecoration(button);
        button.setOnContextMenuRequested(e -> {
            buildContextMenu().show(button, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return button;
    }

    /**
     * The right-click context menu on the toolbar button.
     *
     * <p><i>Restore visibility state</i> is a second recovery route, independent of the panel's
     * own {@code Reset all}: it restores the state the user <b>had</b>, not the state QuPath
     * ships. An automatic snapshot is taken before the panel's first change in a session, which
     * is what makes it a recovery route rather than a power-user feature -- the person who needs
     * it did not plan ahead. Both live here rather than only in the panel because the panel may
     * not be open, and a user whose viewer has gone blank should not have to open it first.</p>
     */
    private ContextMenu buildContextMenu() {
        ContextMenu menu = new ContextMenu();
        OverlayOptions options = OverlayOptions.getSharedInstance();
        boolean hasSnapshot = VisibilityStateStore.hasSnapshot();

        CustomMenuItem restore = menuItem(Strings.get("menu.restoreState"),
                hasSnapshot ? Strings.get("tooltip.menu.restoreState")
                            : Strings.get("tooltip.menu.restoreState.empty"),
                !hasSnapshot);
        restore.setOnAction(e -> {
            if (VisibilityStateStore.restore(options)) {
                Dialogs.showInfoNotification(Strings.get("notify.title"),
                        Strings.get("notify.stateRestored"));
            } else {
                Dialogs.showWarningNotification(Strings.get("notify.title"),
                        Strings.get("notify.noStateSaved"));
            }
        });

        CustomMenuItem save = menuItem(Strings.get("menu.saveState"),
                Strings.get("tooltip.menu.saveState"), false);
        save.setOnAction(e -> {
            VisibilityStateStore.save(options);
            Dialogs.showInfoNotification(Strings.get("notify.title"), Strings.get("notify.stateSaved"));
        });

        CustomMenuItem resetAll = menuItem(Strings.get("menu.resetAll"),
                Strings.get("tooltip.menu.resetAll"), false);
        resetAll.setOnAction(e -> {
            // Mirrors QuPath's own restoreClassVisibilityDefaults(): mode, exact flag and set,
            // all three, in that order.
            VisibilityStateStore.captureIfAbsent(options);
            options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
            options.setUseExactSelectedClasses(false);
            options.selectedClassesProperty().clear();
        });

        // Whichever move is available from the current state, and neither when the panel is shut.
        boolean canDock = isOpen() && !isDocked();
        boolean canUndock = isOpen() && isDocked() && tab.getTabPane() != null;
        CustomMenuItem surfaceItem = canUndock
                ? menuItem(Strings.get("menu.undockToWindow"),
                        Strings.get("tooltip.menu.undockToWindow"), false)
                : menuItem(Strings.get("menu.dockAsTab"),
                        Strings.get("tooltip.menu.dockAsTab"), !canDock);
        surfaceItem.setOnAction(e -> {
            if (canUndock) {
                undockToWindow();
            } else if (canDock) {
                dockAsTab();
            }
        });

        CustomMenuItem show = menuItem(Strings.get("menu.show"), Strings.get("tooltip.menu"), false);
        show.setOnAction(e -> showPanel());

        CustomMenuItem help = menuItem(Strings.get("menu.help"), null, false);
        help.setOnAction(e -> showHelp());

        menu.getItems().addAll(
                restore,
                save,
                new SeparatorMenuItem(),
                resetAll,
                new SeparatorMenuItem(),
                show,
                surfaceItem,
                help);
        return menu;
    }

    /**
     * Build one context-menu entry.
     *
     * <p>{@link CustomMenuItem} rather than a plain {@link MenuItem} because a plain
     * {@code MenuItem} shows no tooltip, and because a <i>disabled</i> node shows no tooltip
     * either -- so an item that is greyed out precisely in order to explain why it is unavailable
     * cannot be built with {@code setDisable}. The unavailable entry is therefore dimmed and made
     * inert while its label stays live enough to carry the explanation.</p>
     *
     * @param text the item text
     * @param tooltip the hover explanation, or null for none
     * @param unavailable true to render the item as unavailable
     * @return the menu item
     */
    private static CustomMenuItem menuItem(String text, String tooltip, boolean unavailable) {
        Label label = new Label(text);
        label.setPadding(new Insets(2, 24, 2, 2));
        label.setMaxWidth(Double.MAX_VALUE);
        if (unavailable) {
            label.setOpacity(0.5);
        }
        if (tooltip != null) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setWrapText(true);
            tip.setMaxWidth(340);
            Tooltip.install(label, tip);
        }
        CustomMenuItem item = new CustomMenuItem(label);
        item.setHideOnClick(true);
        return item;
    }

    /**
     * Add the right-click indicator triangle as a ControlsFX {@link GraphicDecoration} anchored
     * at the button's bottom-right corner. Geometry, rotation and opacity follow QuPath's own
     * {@code ToolBarComponent.addContextMenuDecoration}, so the triangle sits flush with the
     * button edge rather than floating inside the icon.
     *
     * <p>ControlsFX decorations require the node to be in a scene and are lost when it leaves
     * one, hence the {@code sceneProperty} listener as well as the eager application.</p>
     */
    private void addContextMenuDecoration(ToggleButton button) {
        double width = 6;
        Path triangle = new Path(
                new MoveTo(0, 0),
                new LineTo(width, 0),
                new LineTo(width / 2.0, Math.sqrt(width * width / 2.0)),
                new ClosePath());
        triangle.setTranslateX(-width);
        triangle.setTranslateY(-width);
        triangle.setRotate(-90);
        triangle.setStroke(null);
        triangle.setOpacity(0.5);
        triangle.fillProperty().bind(button.textFillProperty());
        triangle.setOnMouseClicked(e -> {
            buildContextMenu().show(button, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        GraphicDecoration decoration = new GraphicDecoration(triangle, Pos.BOTTOM_RIGHT);
        button.sceneProperty().addListener((obs, oldScene, newScene) -> Platform.runLater(() -> {
            if (newScene != null) {
                Decorator.addDecoration(button, decoration);
            } else {
                Decorator.removeDecoration(button, decoration);
            }
        }));
        Platform.runLater(() -> Decorator.addDecoration(button, decoration));
    }

    /**
     * Toolbar icon: three stacked bars, the top one drawn hollow, evoking a list of classes with
     * one hidden. Vector-only -- no font and no image asset -- so it stays crisp at any display
     * scale, and its fill follows the button's theme-driven text fill so it needs no palette of
     * ours in either QuPath theme.
     */
    private static Node buildIcon(ToggleButton button) {
        VBox stack = new VBox(2.2);
        stack.setAlignment(Pos.CENTER);
        stack.setMouseTransparent(true);
        Rectangle[] bars = new Rectangle[3];
        for (int i = 0; i < bars.length; i++) {
            Rectangle bar = new Rectangle(14.5, 3.6);
            bar.setArcWidth(2.6);
            bar.setArcHeight(2.6);
            bar.setMouseTransparent(true);
            bars[i] = bar;
            stack.getChildren().add(bar);
        }
        Runnable applyPalette = () -> {
            Paint fill = button.getTextFill();
            Color color = fill instanceof Color c ? c : Color.GRAY;
            bars[0].setFill(Color.TRANSPARENT);
            bars[0].setStroke(color);
            bars[0].setStrokeWidth(1.0);
            bars[1].setFill(color);
            bars[2].setFill(color);
        };
        applyPalette.run();
        button.textFillProperty().addListener((obs, oldFill, newFill) -> applyPalette.run());
        return stack;
    }
}
