package io.jenkins.plugins.swagger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages API versions for Jenkins core and plugins.
 */
public class ApiVersions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiVersions.class);

    // Core API versions
    public static final String CURRENT_CORE_API_VERSION = "1.0";
    private static final Map<String, String> CORE_API_VERSIONS = new HashMap<>();

    // Plugin API versions
    public static final String CURRENT_PLUGIN_API_VERSION = "1.0";
    private static final Map<String, Map<String, String>> PLUGIN_API_VERSIONS = new HashMap<>();

    static {
        // Initialize core API versions
        CORE_API_VERSIONS.put(CURRENT_CORE_API_VERSION, null); // URL will be set at runtime
    }

    /**
     * Gets all available core API versions.
     * @return A map of version numbers to URLs.
     */
    public static Map<String, String> getCoreApiVersions() {
        return new HashMap<>(CORE_API_VERSIONS);
    }

    /**
     * Gets all available versions for a specific plugin.
     * @param pluginId The plugin ID.
     * @return A map of version numbers to URLs, or null if plugin not found.
     */
    public static Map<String, String> getPluginApiVersions(String pluginId) {
        return PLUGIN_API_VERSIONS.containsKey(pluginId) ? new HashMap<>(PLUGIN_API_VERSIONS.get(pluginId)) : null;
    }

    /**
     * Gets all plugins that have API versions.
     * @return A set of plugin IDs.
     */
    public static Set<String> getPluginsWithApis() {
        return new TreeSet<>(PLUGIN_API_VERSIONS.keySet());
    }

    /**
     * Registers a new plugin API.
     * @param pluginId The plugin ID.
     * @param version The version string.
     * @param url The URL to the API specification.
     */
    public static void registerPluginApi(String pluginId, String version, String url) {
        // Ensure the URL has the proper format with slashes
        if (!url.startsWith("http")) {
            // For relative URLs, ensure they start with a slash
            if (!url.startsWith("/")) {
                url = "/" + url;
            }
        } else {
            // For absolute URLs, ensure there's a slash after the domain
            if (url.matches("https?://[^/]+[^/]")) {
                url = url + "/";
            }
        }

        // Replace any double slashes that aren't part of the protocol
        url = url.replaceAll("(?<!:)//", "/");

        Map<String, String> versions = PLUGIN_API_VERSIONS.computeIfAbsent(pluginId, k -> new HashMap<>());
        versions.put(version, url);

        // Register direct REST API URLs with /rest/ prefix
        String directRestUrl = url.replace("/swagger-ui/plugin/", "/plugin/");

        // Register direct API URLs without /rest/ prefix
        String directApiUrl = directRestUrl.replace("/rest/api/", "/api/");

        // Store the direct URLs for logging purposes
        LOGGER.info("Registered direct plugin API URL with rest prefix: " + directRestUrl + " for plugin: " + pluginId);
        LOGGER.info(
                "Registered direct plugin API URL without rest prefix: " + directApiUrl + " for plugin: " + pluginId);
    }

    /**
     * Updates the URL for a core API version.
     * @param version The version string.
     * @param url The URL to the API specification.
     */
    public static void updateCoreApiUrl(String version, String url) {
        // Ensure the URL has the proper format with slashes
        if (!url.startsWith("http")) {
            // For relative URLs, ensure they start with a slash
            if (!url.startsWith("/")) {
                url = "/" + url;
            }
        } else {
            // For absolute URLs, ensure there's a slash after the domain
            if (url.matches("https?://[^/]+[^/]")) {
                url = url + "/";
            }
        }

        // Replace any double slashes that aren't part of the protocol
        url = url.replaceAll("(?<!:)//", "/");

        if (CORE_API_VERSIONS.containsKey(version)) {
            CORE_API_VERSIONS.put(version, url);

            // Also register direct REST API URLs
            String directRestUrl = url.replace("/swagger-ui/rest/api/", "/rest/api/");
            directRestUrl = directRestUrl.replace("/swagger-ui/api/", "/rest/api/");

            // Register direct API URLs without the /rest/ prefix
            String directApiUrl = url.replace("/swagger-ui/rest/api/", "/api/");
            directApiUrl = directApiUrl.replace("/swagger-ui/api/", "/api/");

            // Store the direct URLs for logging purposes
            LOGGER.info("Registered direct core API URL with rest prefix: " + directRestUrl);
            LOGGER.info("Registered direct core API URL without rest prefix: " + directApiUrl);
        }
    }

    /**
     * Checks if a core API version is valid.
     * @param version The version to check.
     * @return true if the version is valid, false otherwise.
     */
    public static boolean isValidCoreVersion(String version) {
        return CORE_API_VERSIONS.containsKey(version);
    }

    /**
     * Checks if a plugin API version is valid.
     * @param pluginId The plugin ID.
     * @param version The version to check.
     * @return true if the version is valid, false otherwise.
     */
    public static boolean isValidPluginVersion(String pluginId, String version) {
        Map<String, String> versions = PLUGIN_API_VERSIONS.get(pluginId);
        return versions != null && versions.containsKey(version);
    }
}
