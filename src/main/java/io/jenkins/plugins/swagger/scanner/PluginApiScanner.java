package io.jenkins.plugins.swagger.scanner;

import hudson.PluginWrapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Scans installed Jenkins plugins for REST API endpoints
 */
public class PluginApiScanner {
    private static final Logger LOGGER = Logger.getLogger(PluginApiScanner.class.getName());

    /**
     * Generates OpenAPI specifications for installed plugins
     * @return Map of plugin name to OpenAPI specification
     */
    public Map<String, OpenAPI> scanInstalledPlugins() {
        LOGGER.info("Scanning installed Jenkins plugins for REST APIs");

        Map<String, OpenAPI> pluginSpecs = new HashMap<>();

        Jenkins jenkins = Jenkins.get();
        for (PluginWrapper plugin : jenkins.getPluginManager().getPlugins()) {
            if (plugin.isActive()) {
                LOGGER.info("Scanning plugin: " + plugin.getShortName());

                OpenAPI openAPI = new OpenAPI();

                // Set basic information
                Info info = new Info()
                        .title(plugin.getDisplayName() + " REST API")
                        .description("REST API endpoints provided by " + plugin.getDisplayName())
                        .version(plugin.getVersion());
                openAPI.setInfo(info);

                // TODO: Implement actual scanning logic
                // This would need to scan plugin classes for API endpoints

                pluginSpecs.put(plugin.getShortName(), openAPI);
            }
        }

        return pluginSpecs;
    }
}
