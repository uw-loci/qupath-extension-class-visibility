package qupath.ext.classvisibility;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.SetChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
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
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
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
import qupath.lib.objects.classes.PathClass;

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
 * this extension. A docked panel is therefore closed from the toolbar toggle.</p>
 *
 * <p><b>The toolbar button carries two independent facts on two channels.</b> Its pressed state
 * means what a toggle button conventionally means -- the thing it toggles, the panel, is open.
 * Its <b>icon</b> reports whether class rules are in force: open eye, or slashed eye. Those are
 * different questions, and the one that still matters after the panel is closed is the second.
 * Putting both on the pressed state would have said one thing twice and left the other unsaid
 * outside the tooltip, which is what {@link EyeIcon} exists to fix (finding C1).</p>
 *
 * <p>The icon kept its job when the close became a full restore (0.1.1), and it is the same job:
 * rules set from QuPath's own class list are still rules, and after our close the rules in force
 * are exactly those. What did change is the <i>notification</i> beside it -- see
 * {@link #closeMessage}, which now stays quiet when the rules in force are the ones the panel
 * just put back.</p>
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

    /** The toolbar button's eye icon, whose open / slashed state follows the rules. */
    private EyeIcon toolbarIcon;

    /** The Extensions-menu entry, whose label tracks whether the panel is on screen. */
    private MenuItem showHideMenuItem;

    /** The Extensions-menu restore entry, whose label tracks whether a snapshot exists. */
    private MenuItem restoreStateMenuItem;

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
                reconcileStartupVisibility(OverlayOptions.getSharedInstance());
                registerMenuItems();
                installShutdownGuard();
                installStateWatchers();
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
        showHideMenuItem = new MenuItem(Strings.get("menu.show"));
        showHideMenuItem.setOnAction(e -> toggleFromMenu());

        // The three recovery actions live here as well as on the toolbar button's context menu.
        // Toolbar insertion is best effort -- a ten-attempt retry against a layout we do not own,
        // which warns and skips if it fails -- so putting the ONLY route to a recovery control on
        // the component most likely to be missing is the wrong dependency (finding N3). This menu
        // is always present.
        restoreStateMenuItem = new MenuItem(Strings.get("menu.restoreState"));
        restoreStateMenuItem.setOnAction(e -> restoreVisibilityState());
        MenuItem resetAllItem = new MenuItem(Strings.get("menu.resetAll"));
        resetAllItem.setOnAction(e -> resetAllVisibility());

        // The one switch for the attention pulse on the Any / All control. It lives here rather
        // than in the panel because it is a setting, not a rule: putting a checkbox for it beside
        // the control it teaches would add permanent clutter to buy a hint that fires once per
        // session. It is not on the toolbar button's context menu either -- that menu is the
        // recovery route, and mixing a preference into it would dilute what it is for.
        CheckMenuItem highlightItem = new CheckMenuItem(Strings.get("menu.highlightNewControls"));
        highlightItem.selectedProperty().bindBidirectional(
                ClassVisibilityPreferences.highlightNewControlsProperty());

        MenuItem helpItem = new MenuItem(Strings.get("menu.help"));
        helpItem.setOnAction(e -> showHelp());
        menu.getItems().addAll(showHideMenuItem, new SeparatorMenuItem(),
                restoreStateMenuItem, resetAllItem,
                new SeparatorMenuItem(), highlightItem, helpItem);
        // The labels are recomputed as the menu opens, for the same reason the toolbar tooltip is:
        // "is the panel visible" also changes when the user selects another analysis tab or
        // collapses the analysis pane, and neither of those runs any code of ours.
        menu.setOnShowing(e -> syncMenuItemText());
        syncMenuItemText();
        logger.info("Registered menu items: Extensions > {}", EXTENSION_NAME);
    }

    /** Keep the Extensions-menu items saying what they will actually do next. */
    private void syncMenuItemText() {
        if (showHideMenuItem != null) {
            showHideMenuItem.setText(isPanelVisible() ? Strings.get("menu.hide") : Strings.get("menu.show"));
        }
        if (restoreStateMenuItem != null) {
            boolean hasSnapshot = VisibilityStateStore.hasSnapshot();
            restoreStateMenuItem.setText(hasSnapshot
                    ? Strings.get("menu.restoreState")
                    : Strings.get("menu.restoreState.empty"));
            restoreStateMenuItem.setDisable(!hasSnapshot);
        }
    }

    // ------------------------------------------------------------------------------------------
    // The three recovery actions, shared by the Extensions menu and the toolbar context menu
    // ------------------------------------------------------------------------------------------

    /**
     * Put every visibility setting back to the snapshot -- ours and QuPath's alike. One snapshot
     * is taken automatically before the panel's first change in a session, which is what makes
     * this a recovery route rather than a power-user feature.
     */
    private void restoreVisibilityState() {
        if (VisibilityStateStore.restore(OverlayOptions.getSharedInstance())) {
            Dialogs.showInfoNotification(Strings.get("notify.title"), Strings.get("notify.stateRestored"));
        } else {
            Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("notify.noStateSaved"));
        }
    }

    /** Mirrors QuPath's own restoreClassVisibilityDefaults(): mode, exact flag and set, in order. */
    private void resetAllVisibility() {
        OverlayOptions options = OverlayOptions.getSharedInstance();
        boolean wouldChange = !options.selectedClassesProperty().isEmpty()
                || options.getUseExactSelectedClasses()
                || options.getSelectedClassVisibilityMode()
                        != OverlayOptions.ClassVisibilityMode.HIDE_SELECTED;
        VisibilityStateStore.captureIfAbsent(options);
        options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
        options.setUseExactSelectedClasses(false);
        options.selectedClassesProperty().clear();
        // Reset from an already-default state changes nothing on screen. Saying so is the
        // difference between "this control is broken" and "there was nothing to do".
        Dialogs.showInfoNotification(Strings.get("notify.title"), wouldChange
                ? Strings.get("notify.resetApplied")
                : Strings.get("notify.resetNoChange"));
    }

    /**
     * The Extensions-menu entry point, with the toolbar button's three outcomes: open a closed
     * panel, raise an open-but-buried one, close a visible one. Before this it always called
     * {@code showPanel()} -- so with the panel already in front the menu item said "Show panel"
     * and moved the caret, which is the defect a user reported in the context menu, surviving in
     * the one place it was not fixed.
     */
    private void toggleFromMenu() {
        if (isOpen() && isPanelVisible()) {
            closePanel();
        } else {
            showPanel();
        }
        syncMenuItemText();
    }

    /**
     * The R2 guard at startup -- the third and last place it runs.
     *
     * <p>The panel hides every object the moment it opens, and puts the mode back on close. Both
     * halves of that are ours, and between them they leave nothing behind. What is not ours is a
     * crash or a force quit <b>while the panel is open</b>: QuPath binds
     * {@code selectedClassVisibilityMode} to {@code PathPrefs} but not the class set
     * ({@code OverlayOptions.java:131-141}), so the next launch reads back "show only checked
     * classes" with nothing checked -- every object in every image invisible, no panel open, no
     * cause on screen. The close guard cannot reach that path; nothing of ours ran.</p>
     *
     * <p>So it is reconciled here instead, once, before any UI exists. The rule the user gave is
     * that nothing this panel does may persist unless they pressed the button, and a mode left
     * over from a session that never closed is exactly that: a leftover. Resetting it costs a
     * user who <i>deliberately</i> quit in a show-only view nothing but a dropdown they can set
     * again -- their rule set was never persisted anyway.</p>
     *
     * <p>Package-private and static so it can be tested against a real {@link OverlayOptions}
     * with no QuPath instance and no JavaFX toolkit.</p>
     *
     * @param options the options to reconcile
     * @return true when the mode was reset
     */
    static boolean reconcileStartupVisibility(OverlayOptions options) {
        if (ClassVisibilityPane.applyCloseGuard(options)) {
            logger.info("Class visibility: 'show only checked classes' was left set with no "
                    + "checked classes, which hides every object. Reset at startup.");
            return true;
        }
        return false;
    }

    /**
     * Closing the panel, and the R2 guard, at QuPath shutdown.
     *
     * <p><b>Quitting with the panel open is a close.</b> It is the one dismissal route the panel
     * cannot see: QuPath's quit sequence ends in {@code System.exit(0)}, so our window is never
     * hidden and no handler of ours fires. Left alone, a user who quit with the panel up would
     * get none of the restore that every other close gives them -- their pre-panel rules,
     * opacity and object types discarded by a session they never got to end. So the panel is
     * closed here, through the same {@code closePanel()} every other route uses, which restores
     * the snapshot and runs the guard on the result.</p>
     *
     * <p>Then the guard runs again for the panel-closed case. "Show only checked classes"
     * persists across restarts but the rule set does not, so leaving that pair behind means every
     * object in every image is invisible at the next launch, with no panel open and no obvious
     * cause. With the panel closed that pair can only have come from QuPath's own class list, and
     * nothing of ours would otherwise look at it. The guard is idempotent, so running it after a
     * close that already ran it costs nothing.</p>
     *
     * <p><b>WINDOW_CLOSE_REQUEST, as an event filter, and both halves of that matter.</b> This
     * was registered on {@code WINDOW_HIDING} until Phase 5, where it never ran once: QuPath
     * never hides its main stage. {@code QuPathGUI.handleCloseMainStageRequest}, installed with
     * {@code stage.setOnCloseRequest}, runs the entire quit sequence inline and finishes with
     * {@code Platform.exit()} and {@code System.exit(0)}, so the JVM is gone before any hide
     * event exists (finding B1, proved with a standalone JavaFX probe). A JVM shutdown hook would
     * be no better: that same handler calls {@code PathPrefs.savePreferences()} before exiting,
     * so the guard has to write <i>before</i> it. A filter runs in the capturing phase, ahead of
     * the {@code onCloseRequest} property handler -- verified by probe, not assumed.</p>
     *
     * <p>QuPath can still cancel the quit (unsaved viewers, a running script, the script editor),
     * and every one of those paths consumes the event and returns <i>before</i> preferences are
     * saved. A cancelled quit therefore leaves the panel closed and the view restored, as though
     * the user had closed it themselves -- visible, undone by pressing the toolbar button again,
     * and the price of a filter that has to have written before {@code savePreferences()} runs.
     * The alternative, restoring the view but leaving the panel open, would leave a panel on
     * screen whose rules had all been undone underneath it. The guard's own half is unchanged:
     * it only ever flips the mode out of the state in which the user is looking at a completely
     * empty viewer, so it is a rescue rather than a control moving under their hand.</p>
     */
    private void installShutdownGuard() {
        Stage stage = qupath.getStage();
        if (stage == null) {
            return;
        }
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
            // Nothing in here may throw. A filter runs in the capturing phase of QuPath's own
            // close handling, so an exception escaping it could refuse the user their quit.
            try {
                if (isOpen()) {
                    closePanel();
                }
                if (ClassVisibilityPane.applyCloseGuard(OverlayOptions.getSharedInstance())) {
                    logger.info("Class visibility guard fired at shutdown");
                    notifyQuietly(Strings.get("notify.guard"), false);
                }
            } catch (RuntimeException ex) {
                logger.warn("Class visibility: closing the panel at shutdown failed: {}",
                        ex.getMessage(), ex);
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Surface state machine
    // ------------------------------------------------------------------------------------------

    /**
     * Keep the toolbar button honest about state nothing of ours drives: rules written by
     * QuPath's own class list, and the analysis pane being collapsed or re-expanded. Without
     * these the eye icon and the accessible name were recomputed only on transitions we caused,
     * so both could sit stale indefinitely (findings C1, N12).
     */
    private void installStateWatchers() {
        OverlayOptions options = OverlayOptions.getSharedInstance();
        options.selectedClassesProperty().addListener(
                (SetChangeListener<PathClass>) change -> onFxThread(this::syncToolbarState));
        options.selectedClassVisibilityModeProperty().addListener(
                (obs, oldValue, newValue) -> onFxThread(this::syncToolbarState));
        qupath.showAnalysisPaneProperty().addListener(
                (obs, oldValue, newValue) -> onFxThread(this::syncToolbarState));
    }

    private static void onFxThread(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

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
        // Selecting an already-selected tab, or raising an already-frontmost window, changes
        // nothing the user can see. Moving the caret into Find does, and it is where they were
        // going anyway -- so "Show panel" is never a click that appears to do nothing.
        Platform.runLater(pane::focusFind);
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
        // The window's own close button, and any other hide of it. Docking hides this stage too,
        // which is what the reparenting flag is for: a re-parented panel is still running, and
        // running it through closePanel() would restore the opening state and throw away rules
        // the user is in the middle of using.
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
        // Selecting another analysis tab changes what the button will do on the next click, and
        // therefore what its accessible name should say. Nothing of ours runs on that transition
        // otherwise, so the accessible name could sit stale saying "close" where the tooltip --
        // recomputed in setOnShowing -- would have said "bring to the front" (finding N12).
        tab.selectedProperty().addListener((obs, oldValue, newValue) -> syncToolbarState());
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
     * Close the panel from whichever surface it is in, put back the state it opened onto, and run
     * the R2 guard.
     *
     * <p><b>The panel is a session</b> (user, 2026-08-28). It hides every object as it opens, and
     * closing it replays the snapshot taken then -- rules, mode, exact flag, object predicate,
     * opacity, cell display mode and the per-type booleans -- so QuPath ends up exactly where the
     * user left it. Every dismissal route lands here: the window's close button, <i>Hide
     * panel</i>, the Extensions-menu toggle, the toolbar button, and QuPath quitting. Docking and
     * undocking do not, and must not: they re-parent a running panel, and the user's rules
     * survive the move.</p>
     *
     * <p>The guard still runs afterwards. "Show only checked classes" with an empty rule set
     * hides every object in every image, and QuPath persists that mode while not persisting the
     * set -- so if that pair is what the user <i>had</i> before opening the panel, restoring it
     * faithfully would hand them an empty viewer at the next launch with no panel open and no
     * visible cause.</p>
     *
     * <p>Listeners are detached before the restore, not after. The restore is a burst of option
     * changes -- one uncoalesced overlay-cache clear per rule -- and a panel on its way out has
     * no business rebuilding its tables for a view nobody will see.</p>
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
        boolean userChanged = closing.hasUserChanges();
        closing.dispose();

        ClassVisibilityPane.RestoreOutcome outcome = closing.restoreOpeningState();
        boolean restored = outcome == ClassVisibilityPane.RestoreOutcome.RESTORED;
        // Asked after the restore and before the guard: it is the guard that would move the mode
        // away from the state we just put back.
        boolean restoredExactly = restored && closing.matchesOpeningState();
        boolean guarded = closing.applyCloseGuard();
        int remaining = OverlayOptions.getSharedInstance().selectedClassesProperty().size();
        switch (closeMessage(restoredExactly, !restored, guarded, userChanged, remaining)) {
            case RESTORE_FAILED -> notifyQuietly(Strings.get("notify.restoreFailed"), true);
            case GUARD -> notifyQuietly(Strings.get("notify.guard"), false);
            case RULES_ACTIVE -> notifyQuietly(remaining == 1
                    ? Strings.get("notify.rulesStillActive.one")
                    : Strings.format("notify.rulesStillActive.many", remaining), false);
            case NONE -> { }
        }
        syncToolbarState();
        logger.info("Closed the Class visibility panel");
    }

    /** What a close has to tell the user, if anything. */
    enum CloseMessage {
        /** Nothing worth saying: the user is back where they were. */
        NONE,
        /** The restore did not run, or threw. The user is somewhere they did not choose. */
        RESTORE_FAILED,
        /** The R2 guard changed the mode, and the mode it changed was the user's own. */
        GUARD,
        /** Class rules are in force that are not the ones we put back. */
        RULES_ACTIVE
    }

    /**
     * What to say after a close. Package-private and static so the decision can be tested without
     * a toolkit, a QuPath instance or a panel.
     *
     * <p><b>The C1 notification changed job in 0.1.1.</b> It was written when closing the panel
     * left our rules in force: "the panel is closed, but N class rules are still in force" was
     * then news the user needed. Closing now restores the state the panel opened onto, so firing
     * it after a successful restore would announce the user's <i>own</i> pre-existing rules back
     * at them, every single close, until they stopped reading it -- noise, not safety. It is
     * suppressed exactly when the rules in force are the ones we just put back, and still fires
     * otherwise: a restore that failed, or one that did not land, can leave rules of ours behind,
     * and that is the case it was built for. The eye icon is unaffected either way -- rules set
     * from QuPath's own class list are still rules, and the icon reports them whether or not this
     * panel put them there.</p>
     *
     * <p>The guard message is gated differently after a restore than before one. It used to be
     * gated on the user having changed something, because without a restore the guard's usual
     * job was tidying up the panel's own opening default -- announcing that every time would have
     * trained the user to dismiss the one notification that matters. After a successful restore
     * the guard can only be undoing a state the user themselves had before opening the panel, so
     * it is always worth saying.</p>
     *
     * @param restoredExactly the snapshot was replayed and the rules in force are now its own
     * @param restoreFailed no snapshot was replayed, or replaying it threw
     * @param guarded the R2 guard changed the visibility mode
     * @param userChanged the user changed a rule while the panel was open
     * @param rulesInForce how many class rules are in force now
     * @return the one message to show, or {@link CloseMessage#NONE}
     */
    static CloseMessage closeMessage(boolean restoredExactly, boolean restoreFailed,
            boolean guarded, boolean userChanged, int rulesInForce) {
        if (restoreFailed) {
            // Said in preference to the guard message even when the guard also fired: "we could
            // not put your view back" is the bigger fact, and two notifications for one close is
            // how a user learns to dismiss both.
            return CloseMessage.RESTORE_FAILED;
        }
        if (guarded) {
            return restoredExactly || userChanged ? CloseMessage.GUARD : CloseMessage.NONE;
        }
        if (restoredExactly) {
            return CloseMessage.NONE;
        }
        return rulesInForce > 0 ? CloseMessage.RULES_ACTIVE : CloseMessage.NONE;
    }

    /**
     * Show a notification that can never fail the caller.
     *
     * <p>Every close path runs through here, and one of them is QuPath quitting: this is called
     * from inside a {@code WINDOW_CLOSE_REQUEST} filter, where a thrown exception would propagate
     * into QuPath's own close handling. A message to the user is a courtesy; refusing them their
     * quit is not.</p>
     *
     * @param message the text to show
     * @param warning true for a warning notification rather than an informational one
     */
    private static void notifyQuietly(String message, boolean warning) {
        try {
            if (warning) {
                Dialogs.showWarningNotification(Strings.get("notify.title"), message);
            } else {
                Dialogs.showInfoNotification(Strings.get("notify.title"), message);
            }
        } catch (RuntimeException ex) {
            logger.debug("Could not show a Class visibility notification: {}", ex.getMessage());
        }
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

    /**
     * @return whether anything is being hidden by class right now -- including the
     *         "show only checked classes with nothing checked" state, which hides everything
     *         while holding no rules at all. Drives the eye icon's slash.
     */
    private static boolean rulesAreActive() {
        OverlayOptions options = OverlayOptions.getSharedInstance();
        return !options.selectedClassesProperty().isEmpty()
                || options.getSelectedClassVisibilityMode()
                        == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED;
    }

    /**
     * Keep both of the toolbar button's channels current: the pressed state says whether the
     * panel is open, the icon says whether rules are in force.
     *
     * <p>Closing the panel makes every rule one the user cannot see, and until Phase 5 nothing
     * outside the panel said so: the button went back to exactly its no-filter appearance while a
     * view showing 37 of 40 classes stayed in force. A blank viewer announces itself; a filtered
     * one looks entirely normal and is the one a reviewer reads as complete (finding C1). The
     * <b>icon</b> is what answers that now -- a slashed eye survives the panel closing, and it
     * leaves the pressed state free to go on meaning what a toggle button's pressed state
     * conventionally means. The tooltip and accessible text state both facts in words.</p>
     */
    private void syncToolbarState() {
        if (toolbarButton != null) {
            toolbarButton.setSelected(isOpen());
            // The accessible text carries the same fact the slashed eye carries, because state
            // shown only in colour and only in a glyph is state a screen-reader user cannot read.
            // toolbarTooltipText() ends in the rules sentence, so this is covered by construction.
            toolbarButton.setAccessibleText(toolbarTooltipText());
            if (toolbarIcon != null) {
                toolbarIcon.setRulesActive(rulesAreActive());
            }
        }
        syncMenuItemText();
    }

    /**
     * What the toolbar button will do on the <b>next</b> click, in words.
     *
     * <p>Three outcomes, not two: the button opens, raises, or closes. A tooltip that only
     * admitted to raising is what sent a user hunting through the context menu for a hide control
     * the button already had.</p>
     *
     * @return the tooltip text for the current state
     */
    private String toolbarTooltipText() {
        String action;
        if (!isOpen()) {
            action = Strings.get("tooltip.toolbar.closed");
        } else {
            action = isPanelVisible()
                    ? Strings.get("tooltip.toolbar.close")
                    : Strings.get("tooltip.toolbar.raise");
        }
        return action + " " + rulesSummary();
    }

    /** @return one sentence saying what is currently hidden by class, whatever the panel is doing. */
    private static String rulesSummary() {
        OverlayOptions options = OverlayOptions.getSharedInstance();
        int count = options.selectedClassesProperty().size();
        if (count == 0) {
            return options.getSelectedClassVisibilityMode() == OverlayOptions.ClassVisibilityMode.SHOW_SELECTED
                    ? Strings.get("tooltip.toolbar.rules.allHidden")
                    : Strings.get("tooltip.toolbar.rules.none");
        }
        return count == 1
                ? Strings.get("tooltip.toolbar.rules.one")
                : Strings.format("tooltip.toolbar.rules.many", count);
    }

    private void showHelp() {
        // One implementation, shared with the panel's own "?" button.
        ClassVisibilityPane.showHelpDialog();
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
        // The text is recomputed every time the tooltip is about to appear. Setting it only from
        // syncToolbarState() would go stale, because "is the panel visible" also changes when the
        // user selects another tab or collapses the analysis pane -- neither of which runs any
        // code of ours.
        Tooltip tip = new Tooltip();
        tip.setOnShowing(e -> tip.setText(toolbarTooltipText()));
        button.setTooltip(tip);
        button.setAccessibleText(Strings.get("tooltip.toolbar.closed"));
        button.getStyleClass().add("toolbar-button");
        toolbarIcon = new EyeIcon(button);
        button.setGraphic(toolbarIcon);
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
        boolean hasSnapshot = VisibilityStateStore.hasSnapshot();

        CustomMenuItem restore = menuItem(
                hasSnapshot ? Strings.get("menu.restoreState")
                            : Strings.get("menu.restoreState.empty"),
                hasSnapshot ? Strings.get("tooltip.menu.restoreState")
                            : Strings.get("tooltip.menu.restoreState.empty"),
                !hasSnapshot);
        restore.setOnAction(e -> restoreVisibilityState());

        CustomMenuItem resetAll = menuItem(Strings.get("menu.resetAll"),
                Strings.get("tooltip.menu.resetAll"), false);
        resetAll.setOnAction(e -> resetAllVisibility());

        // One dynamic item, because the menu is rebuilt on every right-click: "Show panel" when
        // the panel is closed is a real action, and when it is open it is not -- which is exactly
        // how a Show item that never became Hide read as a broken build.
        CustomMenuItem showHide = isOpen()
                ? menuItem(Strings.get("menu.hide"), Strings.get("tooltip.menu.hide"), false)
                : menuItem(Strings.get("menu.show"), Strings.get("tooltip.menu"), false);
        // Hiding routes through closePanel() rather than a bespoke close path, so the session
        // restore and the R2 guard both fire whichever way the user dismisses the panel. A
        // second dismissal route that skipped them would leave the user in a view they did not
        // choose -- which is the footgun this extension exists to prevent.
        showHide.setOnAction(e -> {
            if (isOpen()) {
                closePanel();
            } else {
                showPanel();
            }
        });

        CustomMenuItem help = menuItem(Strings.get("menu.help"), null, false);
        help.setOnAction(e -> showHelp());

        menu.getItems().addAll(
                restore,
                new SeparatorMenuItem(),
                resetAll,
                new SeparatorMenuItem(),
                showHide);

        // The surface move is offered only when there is a panel to move. With the panel closed
        // it would be an item that does nothing at all -- the same defect in a different place.
        boolean canDock = isOpen() && !isDocked();
        boolean canUndock = isOpen() && isDocked() && tab.getTabPane() != null;
        if (canDock || canUndock) {
            CustomMenuItem surfaceItem = canUndock
                    ? menuItem(Strings.get("menu.undockToWindow"),
                            Strings.get("tooltip.menu.undockToWindow"), false)
                    : menuItem(Strings.get("menu.dockAsTab"),
                            Strings.get("tooltip.menu.dockAsTab"), false);
            surfaceItem.setOnAction(e -> {
                if (canUndock) {
                    undockToWindow();
                } else {
                    dockAsTab();
                }
            });
            menu.getItems().add(surfaceItem);
        }
        menu.getItems().add(help);
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
     * Toolbar icon: an eye, open or slashed, drawn from shapes.
     *
     * <p><b>Why an eye, and why not the glyph.</b> QuPath's own class list marks visibility with
     * FontAwesome {@code EYE} / {@code EYE_SLASH} at 14px ({@code PathClassPane.java:791-797}), so
     * the vocabulary is already learned. The glyph itself is single-coloured, and a single colour
     * has to choose between reading well on a near-white toolbar and reading well on a near-black
     * one. This is built from shapes so that the parts that must follow the theme can, and the
     * parts that must NOT follow it do not.</p>
     *
     * <p><b>Which parts do which.</b> The almond outline and the slash stroke follow
     * {@code button.getTextFill()}, exactly as the previous icon did -- that is what makes the
     * icon sit correctly in either theme and in the toolbar's own hover and pressed states. The
     * iris, pupil and catchlight are FIXED colours: a saturated mid-tone blue holds contrast
     * against both a near-white and a near-black background, which no theme-following colour can
     * do without maintaining two palettes. The sclera is deliberately near-transparent rather
     * than white, because a white sclera vanishes on a light toolbar.</p>
     *
     * <p><b>Open eye versus slashed eye is not decoration.</b> It reports whether class rules are
     * in force, which is the visible half of the Clinical persona's C1 finding: closing the panel
     * used to leave every rule standing with nothing on screen saying so. The iris also switches
     * to a warning tone while rules are active -- but the slash, not the colour, is what carries
     * the meaning, and the button's accessible text and tooltip state it in words as well.</p>
     *
     * <p>It is also what lets the button's pressed state go on meaning "the panel is open". The
     * two facts are independent -- rules outlive the panel -- so they get one channel each rather
     * than both crowding onto the pressed state and leaving the panel's own state unsaid.</p>
     */
    private static final class EyeIcon extends Group {

        /** Saturated mid-tone: legible on a near-white and a near-black toolbar alike. */
        private static final Color IRIS_CALM = Color.web("#2E86C1");

        /** Warning tone, shown with the slash -- never as the only signal. */
        private static final Color IRIS_ACTIVE = Color.web("#E67E22");

        private static final Color PUPIL_COLOR = Color.web("#17202A");

        private final Circle iris = new Circle(8, 8, 3.4, IRIS_CALM);
        private final Path almond;
        private final Line slashHalo;
        private final Line slash;
        private final Group slashGroup;

        private EyeIcon(ToggleButton button) {
            almond = new Path(
                    new MoveTo(1, 8),
                    new QuadCurveTo(8, 2.5, 15, 8),
                    new QuadCurveTo(8, 13.5, 1, 8),
                    new ClosePath());
            almond.setStrokeWidth(1.3);
            almond.setStrokeLineJoin(StrokeLineJoin.ROUND);
            almond.setFill(Color.web("#FFFFFF", 0.10));

            Circle pupil = new Circle(8, 8, 1.7, PUPIL_COLOR);
            // Small, and load-bearing: without it the pupil reads as a dark blob on a dark theme.
            Circle catchlight = new Circle(6.9, 6.9, 0.7, Color.WHITE);

            // Two strokes, not one: the halo is drawn in the toolbar's own background tone so the
            // slash stays legible where it crosses the iris, which is the one place a single
            // stroke disappears.
            slashHalo = new Line(2.5, 13.5, 13.5, 2.5);
            slashHalo.setStrokeWidth(3.0);
            slashHalo.setStrokeLineCap(StrokeLineCap.ROUND);
            slash = new Line(2.5, 13.5, 13.5, 2.5);
            slash.setStrokeWidth(1.4);
            slash.setStrokeLineCap(StrokeLineCap.ROUND);
            slashGroup = new Group(slashHalo, slash);
            slashGroup.setVisible(false);
            slashGroup.setManaged(false);

            getChildren().addAll(almond, iris, pupil, catchlight, slashGroup);
            setMouseTransparent(true);
            for (Node child : getChildren()) {
                child.setMouseTransparent(true);
            }

            applyPalette(button);
            button.textFillProperty().addListener((obs, oldFill, newFill) -> applyPalette(button));
        }

        /**
         * Follow the button's text fill for the theme-dependent strokes.
         *
         * @param button the button whose text fill drives the palette
         */
        private void applyPalette(ToggleButton button) {
            Paint fill = button.getTextFill();
            Color color = fill instanceof Color c ? c : Color.GRAY;
            almond.setStroke(color);
            slash.setStroke(color);
            // The background is not readable from here, but it is the other end of the contrast
            // that produced this text fill: a bright text fill means a dark toolbar.
            slashHalo.setStroke(color.getBrightness() > 0.5
                    ? Color.web("#17202A", 0.85)
                    : Color.web("#FFFFFF", 0.85));
        }

        /**
         * @param active whether class rules are in force. Slashed eye and warning iris when they
         *               are, open eye and calm iris when they are not -- mirroring QuPath's own
         *               EYE / EYE_SLASH pair.
         */
        private void setRulesActive(boolean active) {
            slashGroup.setVisible(active);
            iris.setFill(active ? IRIS_ACTIVE : IRIS_CALM);
        }
    }
}
