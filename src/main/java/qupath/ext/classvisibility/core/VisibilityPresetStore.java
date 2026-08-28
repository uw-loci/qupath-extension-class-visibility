package qupath.ext.classvisibility.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.projects.Project;
import qupath.lib.projects.ResourceManager;

import java.io.IOException;
import java.util.List;

/**
 * Named visibility presets, stored in the open project.
 *
 * <p>A thin wrapper over QuPath's own {@link ResourceManager}, which is how the Brightness &amp;
 * Contrast dialog stores its saved settings ({@code DisplaySettingUtils.java:213}). Using it
 * means presets land beside the project's other resources, are written as readable JSON, survive
 * a project move, and travel with the project when it is shared -- none of which a preference
 * blob would do.</p>
 *
 * <p><b>No project, no presets.</b> Every method degrades quietly to an empty list or a false
 * return when there is no project or the project does not support resources, and the panel
 * disables its controls and says why rather than failing at the click. A preference-backed
 * fallback was considered and rejected: the request was for presets saved <i>to the project</i>,
 * and a preset that silently lived somewhere else would be worse than one that is honestly
 * unavailable.</p>
 */
public final class VisibilityPresetStore {

    private static final Logger logger = LoggerFactory.getLogger(VisibilityPresetStore.class);

    /** Where presets live inside the project directory. */
    public static final String PROJECT_LOCATION = "resources/class-visibility";

    private VisibilityPresetStore() {
        // Utility class.
    }

    /**
     * @param project the current project, or null
     * @return the resource manager for presets, or null when there is nowhere to store them
     */
    public static ResourceManager.Manager<VisibilityPreset> managerFor(Project<?> project) {
        if (project == null) {
            return null;
        }
        return project.getResources(PROJECT_LOCATION, VisibilityPreset.class, "json");
    }

    /**
     * @param project the current project, or null
     * @return the saved preset names, sorted, or an empty list. Never throws: a listing that
     *         fails is a disabled combo box, not a dialog in the user's way.
     */
    public static List<String> names(Project<?> project) {
        ResourceManager.Manager<VisibilityPreset> manager = managerFor(project);
        if (manager == null) {
            return List.of();
        }
        try {
            return manager.getNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IOException e) {
            logger.warn("Could not list visibility presets: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
