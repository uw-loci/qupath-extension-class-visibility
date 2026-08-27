package qupath.ext.classvisibility.ui;

import java.util.ResourceBundle;

/**
 * Access to the extension's display strings.
 *
 * <p>Every user-visible string in this extension comes from here, so the whole shipped
 * vocabulary can be read, reviewed and grepped in one file. Singular and plural are separate
 * complete format strings; names substitute into complete sentences via {@code %s} rather than
 * being concatenated onto fragments.</p>
 */
public final class Strings {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("qupath.ext.classvisibility.ui.strings");

    private Strings() {
        // Utility class.
    }

    /**
     * @param key the resource key
     * @return the string for that key
     */
    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * @param key the resource key
     * @param args format arguments
     * @return the formatted string
     */
    public static String format(String key, Object... args) {
        return String.format(BUNDLE.getString(key), args);
    }
}
