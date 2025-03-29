package io.jenkins.plugins.swagger;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.swagger.scanner.JenkinsApiScanner;
import io.jenkins.plugins.swagger.scanner.PluginApiScanner;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * RootAction that serves the Swagger UI and API specifications
 */
@Extension
public class SwaggerUiAction implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(SwaggerUiAction.class.getName());

    private final JenkinsApiScanner coreScanner;
    private final PluginApiScanner pluginScanner;

    public SwaggerUiAction() {
        this.coreScanner = new JenkinsApiScanner();
        this.pluginScanner = new PluginApiScanner();
        LOGGER.info("SwaggerUiAction initialized");
    }

    @Override
    public String getIconFileName() {
        return "/plugin/jenkins-swagger-api/images/swagger-logo.png";
    }

    @Override
    public String getDisplayName() {
        return "API Documentation";
    }

    @Override
    public String getUrlName() {
        return "swagger-ui";
    }

    /**
     * Serves the OpenAPI specification for Jenkins core
     */
    public void doCoreApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving Core API specification");
        try {
            OpenAPI coreApi = coreScanner.scanJenkinsCore();

            rsp.setContentType("application/json");
            Json.mapper().writeValue(rsp.getWriter(), coreApi);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating core API spec", e);
            rsp.sendError(500, "Error generating core API spec: " + e.getMessage());
        }
    }

    /**
     * Serves the OpenAPI specification for a specific plugin
     */
    public void doPluginApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String pluginName = req.getParameter("plugin");
        LOGGER.info("Serving Plugin API specification for: " + pluginName);

        if (pluginName == null || pluginName.isEmpty()) {
            rsp.sendError(400, "Missing plugin parameter");
            return;
        }

        try {
            Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
            OpenAPI pluginApi = pluginSpecs.get(pluginName);

            if (pluginApi == null) {
                rsp.sendError(404, "Plugin specification not found: " + pluginName);
                return;
            }

            rsp.setContentType("application/json");
            Json.mapper().writeValue(rsp.getWriter(), pluginApi);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating plugin API spec", e);
            rsp.sendError(500, "Error generating plugin API spec: " + e.getMessage());
        }
    }

    /**
     * Lists available API specifications
     */
    public void doApiList(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving API list");
        try {
            Map<String, String> apiList = new java.util.HashMap<>();
            apiList.put("core", Jenkins.get().getRootUrl() + getUrlName() + "/core-api");

            Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
            for (String plugin : pluginSpecs.keySet()) {
                apiList.put(plugin, Jenkins.get().getRootUrl() + getUrlName() + "/plugin-api?plugin=" + plugin);
            }

            rsp.setContentType("application/json");
            Json.mapper().writeValue(rsp.getWriter(), apiList);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating API list", e);
            rsp.sendError(500, "Error generating API list: " + e.getMessage());
        }
    }
}
