package qupath.ext.classvisibility.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.classvisibility.core.ClassHarvester;
import qupath.ext.classvisibility.core.VisibilityRuleModel;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Persistent preferences for the Class Visibility extension.
 *
 * <p>What is here is view state: which profile divider the user dragged where, how they like the
 * lists scoped and sorted, and whether the tab comes back at startup.</p>
 *
 * <p><b>What is deliberately NOT here, so nobody adds it later as a nice touch:</b></p>
 * <ul>
 *   <li><b>The search text.</b> Reopening onto a filtered list that hides most classes, with no
 *       memory of having typed the filter, is a self-inflicted version of the bug this extension
 *       exists to prevent.</li>
 *   <li><b>{@code selectedClasses}.</b> QuPath deliberately does not persist it. Persisting it
 *       ourselves would make this panel the only thing in QuPath capable of hiding objects
 *       across a restart, which widens rather than narrows the failure mode.</li>
 *   <li><b>The visibility mode and the exact-match flag.</b> QuPath already persists both. We
 *       read and write the live properties and never keep a second copy.</li>
 * </ul>
 *
 * <p>Pattern source: {@code qupath-extension-confusion-matrix/preferences/CMPreferences.java}.</p>
 */
public final class ClassVisibilityPreferences {

    private static final Logger logger = LoggerFactory.getLogger(ClassVisibilityPreferences.class);

    /** Preference-key namespace; collision-free within QuPath's flat key space. */
    private static final String PREFIX = "classvisibility.";

    private static BooleanProperty showTabAtStartupProperty;
    private static DoubleProperty wideDividerProperty;
    private static DoubleProperty narrowDividerProperty;
    private static BooleanProperty rulesExpandedProperty;
    private static BooleanProperty autoRefreshCountsProperty;
    private static BooleanProperty includeEmptyClassesProperty;
    private static DoubleProperty coverageThresholdProperty;
    private static ObjectProperty<ClassHarvester.Scope> scopeProperty;
    private static ObjectProperty<VisibilityRuleModel.Combination> combinationProperty;

    private static volatile boolean installed = false;

    private ClassVisibilityPreferences() {
        // Utility class.
    }

    /** Install the persistent preferences. Idempotent; later calls are no-ops. */
    public static synchronized void installPreferences() {
        if (installed) {
            return;
        }
        logger.info("Installing Class Visibility preferences");

        showTabAtStartupProperty = PathPrefs.createPersistentPreference(
                PREFIX + "showTabAtStartup", false);
        wideDividerProperty = PathPrefs.createPersistentPreference(
                PREFIX + "wideDivider", 0.55);
        narrowDividerProperty = PathPrefs.createPersistentPreference(
                PREFIX + "narrowDivider", 0.55);
        rulesExpandedProperty = PathPrefs.createPersistentPreference(
                PREFIX + "rulesExpanded", false);
        autoRefreshCountsProperty = PathPrefs.createPersistentPreference(
                PREFIX + "autoRefreshCounts", true);
        includeEmptyClassesProperty = PathPrefs.createPersistentPreference(
                PREFIX + "includeEmptyClasses", false);
        // The fraction of an image's classes a component must appear in before its spread ratio
        // is emphasised. 0.8 is a design starting point, not a measured constant -- it is a
        // preference so it can be retuned from bench data without a release.
        coverageThresholdProperty = PathPrefs.createPersistentPreference(
                PREFIX + "coverageThreshold", 0.8);
        scopeProperty = PathPrefs.createPersistentPreference(
                PREFIX + "scope", ClassHarvester.Scope.DETECTIONS, ClassHarvester.Scope.class);
        // Any (OR) is the first-run default: it matches the script this extension ports, and an
        // All default makes a first-timer's second checked component empty the viewer, which
        // reads as the panel being broken rather than as a powerful operation.
        combinationProperty = PathPrefs.createPersistentPreference(
                PREFIX + "combination", VisibilityRuleModel.Combination.ANY,
                VisibilityRuleModel.Combination.class);

        installed = true;
        logger.info("Class Visibility preferences installed");
    }

    private static void ensureInstalled() {
        if (!installed) {
            installPreferences();
        }
    }

    /** @return whether the tab is restored automatically when QuPath starts. */
    public static BooleanProperty showTabAtStartupProperty() {
        ensureInstalled();
        return showTabAtStartupProperty;
    }

    /** @return the split divider position used in the wide (undocked) profile. */
    public static DoubleProperty wideDividerProperty() {
        ensureInstalled();
        return wideDividerProperty;
    }

    /** @return the split divider position used in the narrow (docked) profile. */
    public static DoubleProperty narrowDividerProperty() {
        ensureInstalled();
        return narrowDividerProperty;
    }

    /** @return whether the Active rules expander is open. */
    public static BooleanProperty rulesExpandedProperty() {
        ensureInstalled();
        return rulesExpandedProperty;
    }

    /** @return whether counts re-harvest automatically when the hierarchy changes. */
    public static BooleanProperty autoRefreshCountsProperty() {
        ensureInstalled();
        return autoRefreshCountsProperty;
    }

    /** @return whether project classes with no objects in this image are listed too. */
    public static BooleanProperty includeEmptyClassesProperty() {
        ensureInstalled();
        return includeEmptyClassesProperty;
    }

    /** @return the coverage fraction above which a component's spread ratio is emphasised. */
    public static DoubleProperty coverageThresholdProperty() {
        ensureInstalled();
        return coverageThresholdProperty;
    }

    /** @return the list scope: which objects are listed and counted. */
    public static ObjectProperty<ClassHarvester.Scope> scopeProperty() {
        ensureInstalled();
        return scopeProperty;
    }

    /** @return how two or more checked components combine. */
    public static ObjectProperty<VisibilityRuleModel.Combination> combinationProperty() {
        ensureInstalled();
        return combinationProperty;
    }
}
