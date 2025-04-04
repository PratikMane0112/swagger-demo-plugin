package io.jenkins.plugins.swagger;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import io.jenkins.plugins.swagger.scanner.JenkinsApiScanner;
import io.jenkins.plugins.swagger.scanner.PluginApiScanner;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * RootAction that serves the Swagger UI and API specifications
 */
@Extension
public class SwaggerUiAction implements UnprotectedRootAction {
    private static final Logger LOGGER = Logger.getLogger(SwaggerUiAction.class.getName());

    private final JenkinsApiScanner coreScanner;
    private final PluginApiScanner pluginScanner;

    public SwaggerUiAction() {
        this.coreScanner = new JenkinsApiScanner();
        this.pluginScanner = new PluginApiScanner();
        LOGGER.info("SwaggerUiAction initialized");
        System.out.println("DEBUG: SwaggerUiAction constructor called");

        // Initialize API version URLs
        initializeApiVersions();
    }

    /**
     * Initializes API version URLs based on the current configuration
     */
    private void initializeApiVersions() {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl == null) {
            rootUrl = "";
        } else if (!rootUrl.endsWith("/")) {
            rootUrl = rootUrl + "/";
        }

        // Core API versions
        // Direct URL pattern: /rest/api/{version}
        String directCoreApiUrl = rootUrl + "rest/api/" + ApiVersions.CURRENT_CORE_API_VERSION;
        LOGGER.info("Registering direct core API URL: " + directCoreApiUrl);
        ApiVersions.updateCoreApiUrl(ApiVersions.CURRENT_CORE_API_VERSION, directCoreApiUrl);

        // Pre-scan plugins to populate plugin APIs at startup
        try {
            Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
            for (String plugin : pluginSpecs.keySet()) {
                // Direct URL pattern: /plugin/{plugin-id}/rest/api/{version}
                String directUrl = rootUrl + "plugin/" + plugin + "/rest/api/" + ApiVersions.CURRENT_PLUGIN_API_VERSION;
                LOGGER.info("Registering plugin API URL: " + directUrl + " for plugin: " + plugin);
                ApiVersions.registerPluginApi(plugin, ApiVersions.CURRENT_PLUGIN_API_VERSION, directUrl);
                LOGGER.fine("Registered plugin API: " + plugin);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error pre-scanning plugins", e);
        }
    }

    @Override
    public String getIconFileName() {
        return "/images/24x24/document.png";
    }

    @Override
    public String getDisplayName() {
        return "API Documentation";
    }

    @Override
    public String getUrlName() {
        System.out.println("DEBUG: getUrlName() called, returning 'swagger-ui'");
        LOGGER.info("DEBUG: getUrlName() called, returning 'swagger-ui'");
        return "swagger-ui";
    }

    /**
     * Handles the root URL path (/swagger-ui/)
     */
    public void getIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.info("Serving Swagger UI index at path: " + getUrlName());
        System.out.println("DEBUG: SwaggerUiAction.getIndex() was called");

        try {
            // Try to load the view file directly
            String viewPath = "io/jenkins/plugins/swagger/SwaggerUiAction/view.jelly";
            LOGGER.info("DEBUG: Looking for view at: " + viewPath);

            // Use the view.jelly file directly
            RequestDispatcher dispatcher = req.getView(this, "view");
            if (dispatcher != null) {
                LOGGER.info("DEBUG: View dispatcher found, forwarding request");
                rsp.setContentType("text/html;charset=UTF-8");
                dispatcher.forward(req, rsp);
            } else {
                LOGGER.severe("DEBUG: View dispatcher is null, view.jelly might be missing");
                rsp.sendError(500, "View not found. This could be due to a missing or inaccessible view.jelly file.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getIndex", e);
            e.printStackTrace();
            rsp.sendError(500, "Error loading view: " + e.getMessage());
        }
    }

    /**
     * Also handles the root URL path (/swagger-ui/) - used for direct root access
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        LOGGER.info("doDynamic called with path: " + path);
        System.out.println("DEBUG: doDynamic called with path: " + path);

        try {
            if (path == null || path.isEmpty() || path.equals("/")) {
                LOGGER.info("Serving Swagger UI via doDynamic at path: " + getUrlName());
                // Call getIndex method directly to render the UI
                getIndex(req, rsp);
                return;
            }

            // Check for a malformed plugin API path (no plugin ID)
            if (path.equals("/plugin/rest/api")
                    || path.startsWith("/plugin/rest/api/")
                    || path.equals("/plugin/api")
                    || path.startsWith("/plugin/api/")) {
                LOGGER.warning("Invalid plugin API request without plugin ID: " + path);
                rsp.sendError(400, "Invalid plugin API request. Plugin ID is required.");
                return;
            }

            // Check if it's a valid plugin API request
            if (path.startsWith("/plugin/") && (path.contains("/rest/api/") || path.contains("/api/"))) {
                String[] parts = path.split("/");
                if (parts.length >= 4) {
                    String pluginId = parts[2];
                    if (pluginId == null || pluginId.isEmpty() || pluginId.equals("rest") || pluginId.equals("api")) {
                        LOGGER.warning("Malformed plugin API path: " + path);
                        rsp.sendError(400, "Invalid plugin API path. Missing plugin ID.");
                        return;
                    }

                    LOGGER.info("Forwarding plugin API request for plugin: " + pluginId);
                    // Forward to the doPlugin method
                    req.getRequestDispatcher(
                                    req.getContextPath() + "/swagger-ui/plugin" + path.substring("/plugin".length()))
                            .forward(req, rsp);
                    return;
                } else {
                    LOGGER.warning("Malformed plugin API path: " + path);
                    rsp.sendError(400, "Invalid plugin API path");
                    return;
                }
            }

            // Check if it's a REST API request
            if (path.startsWith("/rest/api/") || path.startsWith("/api/")) {
                LOGGER.info("Forwarding REST API request: " + path);
                // Forward to the doRest method
                req.getRequestDispatcher(req.getContextPath() + "/swagger-ui/rest" + path.substring("/rest".length()))
                        .forward(req, rsp);
                return;
            }

            // If not the root path or API request, return a 404
            rsp.sendError(404, "Path not found: " + path);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in doDynamic", e);
            System.out.println("DEBUG: Error in doDynamic: " + e.getMessage());
            e.printStackTrace();
            rsp.sendError(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Serves a list of available API versions
     */
    public void doVersions(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving API versions list");
        try {
            Map<String, Object> versions = new HashMap<>();

            // Add core API versions
            versions.put("core", ApiVersions.getCoreApiVersions());

            // Add plugin API versions
            Map<String, Map<String, String>> pluginVersions = new HashMap<>();
            for (String pluginId : ApiVersions.getPluginsWithApis()) {
                pluginVersions.put(pluginId, ApiVersions.getPluginApiVersions(pluginId));
            }

            // If no plugins have been registered yet, scan them now
            if (pluginVersions.isEmpty()) {
                Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
                String rootUrl = Jenkins.get().getRootUrl();
                if (rootUrl == null) {
                    rootUrl = "";
                } else if (!rootUrl.endsWith("/")) {
                    rootUrl = rootUrl + "/";
                }

                for (String plugin : pluginSpecs.keySet()) {
                    Map<String, String> pluginVerMap = new HashMap<>();
                    String url = rootUrl + "plugin/" + plugin + "/rest/api/" + ApiVersions.CURRENT_PLUGIN_API_VERSION;
                    pluginVerMap.put(ApiVersions.CURRENT_PLUGIN_API_VERSION, url);
                    pluginVersions.put(plugin, pluginVerMap);

                    // Register the plugin API
                    ApiVersions.registerPluginApi(plugin, ApiVersions.CURRENT_PLUGIN_API_VERSION, url);
                    LOGGER.fine("Registered plugin API: " + plugin);
                }
            }

            versions.put("plugins", pluginVersions);

            // Set CORS headers to allow cross-origin requests
            rsp.setContentType("application/json");
            rsp.setHeader("Access-Control-Allow-Origin", "*");
            rsp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
            rsp.setHeader("Access-Control-Allow-Headers", "Content-Type");

            Json.mapper().writeValue(rsp.getWriter(), versions);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error generating API versions list", e);
            rsp.sendError(500, "Error generating API versions list: " + e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unexpected error generating API versions list", e);
            rsp.sendError(500, "Unexpected error generating API versions list: " + e.getMessage());
        }
    }

    /**
     * Serves the OpenAPI specification for Jenkins core with versioning
     * URL patterns:
     * - /rest/api/{version} (old pattern)
     * - /api/{version} (new pattern)
     */
    public void doRest(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String path = req.getRestOfPath();
        LOGGER.info("REST API request: " + path);
        System.out.println("DEBUG: REST API request: " + path);

        if (path.startsWith("/api/")) {
            // New pattern: /api/{version}
            String version = path.substring("/api/".length());
            processApiVersion(req, rsp, version);
        } else if (path.startsWith("/rest/api/")) {
            // Old pattern: /rest/api/{version}
            String version = path.substring("/rest/api/".length());
            processApiVersion(req, rsp, version);
        } else {
            rsp.sendError(404, "API endpoint not found: " + path);
        }
    }

    /**
     * Direct API access without the /rest/ prefix
     */
    public void doApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String path = req.getRestOfPath();
        LOGGER.info("Direct API request: " + path);
        System.out.println("DEBUG: Direct API request: " + path);

        // Path will be /{version} or empty
        String version = "";
        if (path.length() > 1) {
            version = path.substring(1); // Skip leading /
        }

        processApiVersion(req, rsp, version);
    }

    /**
     * Process API version request
     */
    private void processApiVersion(StaplerRequest req, StaplerResponse rsp, String version) throws IOException {
        if (version.isEmpty()) {
            // List available API versions
            rsp.setContentType("application/json");
            rsp.setHeader("Access-Control-Allow-Origin", "*");
            rsp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
            rsp.setHeader("Access-Control-Allow-Headers", "Content-Type");
            Json.mapper().writeValue(rsp.getWriter(), ApiVersions.getCoreApiVersions());
            return;
        }

        // Check if this is a supported version
        if (ApiVersions.isValidCoreVersion(version)) {
            serveCoreApi(req, rsp, version);
            return;
        }

        rsp.sendError(404, "API version not found: " + version);
    }

    /**
     * Serves the OpenAPI specification for Jenkins core (for backward compatibility)
     */
    public void doCoreApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving Core API specification (direct endpoint)");
        System.out.println("DEBUG: doCoreApi method called");

        // Directly serve the core API with the current version
        serveCoreApi(req, rsp, ApiVersions.CURRENT_CORE_API_VERSION);
    }

    /**
     * Internal method to serve the core API spec
     */
    private void serveCoreApi(StaplerRequest req, StaplerResponse rsp, String version) throws IOException {
        System.out.println("DEBUG: serveCoreApi method called for version: " + version);

        // Get the Jenkins root URL for server definition
        String jenkinsRootUrl = Jenkins.get().getRootUrl();
        if (jenkinsRootUrl == null) {
            jenkinsRootUrl = req.getContextPath();
        }

        // Create enhanced OpenAPI 3.0 spec with more endpoints
        String jsonSpec = "{"
                + "  \"openapi\": \"3.0.1\","
                + "  \"info\": {"
                + "    \"title\": \"Jenkins Core API\","
                + "    \"description\": \"Jenkins core REST API endpoints\","
                + "    \"version\": \"" + version + "\""
                + "  },"
                + "  \"servers\": ["
                + "    {"
                + "      \"url\": \"" + jenkinsRootUrl + "\","
                + "      \"description\": \"Jenkins Instance\""
                + "    }"
                + "  ],"
                + "  \"paths\": {"
                + "    \"/api/json\": {"
                + "      \"get\": {"
                + "        \"summary\": \"Jenkins Root API\","
                + "        \"description\": \"Basic Jenkins API endpoint\","
                + "        \"operationId\": \"getJenkinsInfo\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"depth\","
                + "            \"in\": \"query\","
                + "            \"description\": \"Recursion depth for nested objects\","
                + "            \"schema\": { \"type\": \"integer\" }"
                + "          },"
                + "          {"
                + "            \"name\": \"tree\","
                + "            \"in\": \"query\","
                + "            \"description\": \"Filter expression for returned properties\","
                + "            \"schema\": { \"type\": \"string\" }"
                + "          }"
                + "        ],"
                + "        \"responses\": {"
                + "          \"200\": {"
                + "            \"description\": \"Successful operation\","
                + "            \"content\": {"
                + "              \"application/json\": {"
                + "                \"schema\": {"
                + "                  \"type\": \"object\","
                + "                  \"properties\": {"
                + "                    \"name\": {"
                + "                      \"type\": \"string\","
                + "                      \"example\": \"Jenkins\""
                + "                    },"
                + "                    \"version\": {"
                + "                      \"type\": \"string\","
                + "                      \"example\": \"2.x\""
                + "                    },"
                + "                    \"nodes\": {"
                + "                      \"type\": \"array\","
                + "                      \"description\": \"List of Jenkins nodes (agents)\","
                + "                      \"items\": {"
                + "                        \"type\": \"object\""
                + "                      }"
                + "                    },"
                + "                    \"views\": {"
                + "                      \"type\": \"array\","
                + "                      \"description\": \"List of Jenkins views\","
                + "                      \"items\": {"
                + "                        \"type\": \"object\""
                + "                      }"
                + "                    },"
                + "                    \"jobs\": {"
                + "                      \"type\": \"array\","
                + "                      \"description\": \"List of Jenkins jobs\","
                + "                      \"items\": {"
                + "                        \"type\": \"object\""
                + "                      }"
                + "                    }"
                + "                  }"
                + "                }"
                + "              }"
                + "            }"
                + "          }"
                + "        }"
                + "      }"
                + "    },"
                + "    \"/job/{jobName}/api/json\": {"
                + "      \"get\": {"
                + "        \"summary\": \"Job API\","
                + "        \"description\": \"Jenkins job API endpoint\","
                + "        \"operationId\": \"getJobInfo\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"jobName\","
                + "            \"in\": \"path\","
                + "            \"description\": \"Job name\","
                + "            \"required\": true,"
                + "            \"schema\": { \"type\": \"string\" }"
                + "          },"
                + "          {"
                + "            \"name\": \"depth\","
                + "            \"in\": \"query\","
                + "            \"description\": \"Recursion depth for nested objects\","
                + "            \"schema\": { \"type\": \"integer\" }"
                + "          },"
                + "          {"
                + "            \"name\": \"tree\","
                + "            \"in\": \"query\","
                + "            \"description\": \"Filter expression for returned properties\","
                + "            \"schema\": { \"type\": \"string\" }"
                + "          }"
                + "        ],"
                + "        \"responses\": {"
                + "          \"200\": {"
                + "            \"description\": \"Successful operation\","
                + "            \"content\": {"
                + "              \"application/json\": {"
                + "                \"schema\": {"
                + "                  \"type\": \"object\""
                + "                }"
                + "              }"
                + "            }"
                + "          }"
                + "        }"
                + "      },"
                + "      \"post\": {"
                + "        \"summary\": \"Build Job\","
                + "        \"description\": \"Trigger a build for the specified job\","
                + "        \"operationId\": \"buildJob\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"jobName\","
                + "            \"in\": \"path\","
                + "            \"description\": \"Job name\","
                + "            \"required\": true,"
                + "            \"schema\": { \"type\": \"string\" }"
                + "          },"
                + "          {"
                + "            \"name\": \"delay\","
                + "            \"in\": \"query\","
                + "            \"description\": \"Delay in seconds before starting the build\","
                + "            \"schema\": { \"type\": \"integer\" }"
                + "          }"
                + "        ],"
                + "        \"requestBody\": {"
                + "          \"content\": {"
                + "            \"application/x-www-form-urlencoded\": {"
                + "              \"schema\": {"
                + "                \"type\": \"object\","
                + "                \"properties\": {"
                + "                  \"parameter\": {"
                + "                    \"type\": \"array\","
                + "                    \"description\": \"Build parameters\","
                + "                    \"items\": {"
                + "                      \"type\": \"string\""
                + "                    }"
                + "                  }"
                + "                }"
                + "              }"
                + "            }"
                + "          }"
                + "        },"
                + "        \"responses\": {"
                + "          \"201\": {"
                + "            \"description\": \"Build triggered successfully\","
                + "            \"headers\": {"
                + "              \"Location\": {"
                + "                \"description\": \"URL to the queued build\","
                + "                \"schema\": { \"type\": \"string\" }"
                + "              }"
                + "            }"
                + "          },"
                + "          \"404\": {"
                + "            \"description\": \"Job not found\""
                + "          },"
                + "          \"403\": {"
                + "            \"description\": \"Insufficient permissions\""
                + "          }"
                + "        }"
                + "      }"
                + "    },"
                + "    \"/job/{jobName}/config.xml\": {"
                + "      \"get\": {"
                + "        \"summary\": \"Get Job Configuration\","
                + "        \"description\": \"Get the configuration XML for a job\","
                + "        \"operationId\": \"getJobConfig\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"jobName\","
                + "            \"in\": \"path\","
                + "            \"description\": \"Job name\","
                + "            \"required\": true,"
                + "            \"schema\": { \"type\": \"string\" }"
                + "          }"
                + "        ],"
                + "        \"responses\": {"
                + "          \"200\": {"
                + "            \"description\": \"Successful operation\","
                + "            \"content\": {"
                + "              \"application/xml\": {"
                + "                \"schema\": {"
                + "                  \"type\": \"string\""
                + "                }"
                + "              }"
                + "            }"
                + "          },"
                + "          \"404\": {"
                + "            \"description\": \"Job not found\""
                + "          }"
                + "        }"
                + "      },"
                + "      \"post\": {"
                + "        \"summary\": \"Update Job Configuration\","
                + "        \"description\": \"Update the configuration of a job\","
                + "        \"operationId\": \"updateJobConfig\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"jobName\","
                + "            \"in\": \"path\","
                + "            \"description\": \"Job name\","
                + "            \"required\": true,"
                + "            \"schema\": { \"type\": \"string\" }"
                + "          }"
                + "        ],"
                + "        \"requestBody\": {"
                + "          \"content\": {"
                + "            \"application/xml\": {"
                + "              \"schema\": {"
                + "                \"type\": \"string\""
                + "              }"
                + "            }"
                + "          },"
                + "          \"required\": true"
                + "        },"
                + "        \"responses\": {"
                + "          \"200\": {"
                + "            \"description\": \"Configuration updated successfully\""
                + "          },"
                + "          \"400\": {"
                + "            \"description\": \"Invalid configuration XML\""
                + "          },"
                + "          \"404\": {"
                + "            \"description\": \"Job not found\""
                + "          }"
                + "        }"
                + "      }"
                + "    },"
                + "    \"/job/{jobName}/delete\": {"
                + "      \"post\": {"
                + "        \"summary\": \"Delete Job\","
                + "        \"description\": \"Delete a job\","
                + "        \"operationId\": \"deleteJob\","
                + "        \"parameters\": ["
                + "          {"
                + "            \"name\": \"jobName\","
                + "            \"in\": \"path\","
                + "            \"description\": \"Job name\","
                + "            \"required\": true,"
                + "            \"schema\": { \"type\": \"string\" }"
                + "          }"
                + "        ],"
                + "        \"responses\": {"
                + "          \"302\": {"
                + "            \"description\": \"Job deleted successfully\""
                + "          },"
                + "          \"404\": {"
                + "            \"description\": \"Job not found\""
                + "          }"
                + "        }"
                + "      }"
                + "    }"
                + "  }"
                + "}";

        try {
            // Set response headers
            rsp.setContentType("application/json");
            rsp.setCharacterEncoding("UTF-8");

            // Set CORS headers to allow browser access
            rsp.setHeader("Access-Control-Allow-Origin", "*");
            rsp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
            rsp.setHeader("Access-Control-Allow-Headers", "Content-Type");

            // Write the raw JSON string
            rsp.getWriter().write(jsonSpec);
            System.out.println("DEBUG: Raw JSON spec written to response");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to send API spec: " + e.getMessage());
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, "Error sending core API spec", e);
            rsp.sendError(500, "Error sending core API spec: " + e.getMessage());
        }
    }

    /**
     * Serves the OpenAPI specification for a specific plugin with versioning
     * URL pattern: /plugin/{pluginId}/rest/api/{version} or /plugin/{pluginId}/api/{version}
     */
    public void doPlugin(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String path = req.getRestOfPath();
        LOGGER.info("Plugin API request: " + path);
        System.out.println("DEBUG: Plugin API request: " + path);

        // Expected patterns:
        // - /pluginId/rest/api/version (old pattern with rest prefix)
        // - /pluginId/api/version (new pattern without rest prefix)
        String[] parts = path.split("/");

        // Debug the path parts
        System.out.println("DEBUG: Plugin path parts length: " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            System.out.println("DEBUG: Plugin path part " + i + ": " + parts[i]);
        }

        // Ensure path has enough parts
        if (parts.length < 3) {
            rsp.sendError(400, "Invalid plugin API path: parts length is " + parts.length);
            return;
        }

        // Extract plugin ID (should be in parts[1])
        String pluginId = parts[1];
        if (pluginId == null || pluginId.isEmpty()) {
            rsp.sendError(400, "Missing plugin ID in path");
            return;
        }

        LOGGER.info("Processing API request for plugin: " + pluginId);
        String version;

        // Check which pattern we're using
        if (parts.length >= 5 && parts[2].equals("rest") && parts[3].equals("api")) {
            // Old pattern: /pluginId/rest/api/version
            version = parts[4];
        } else if (parts.length >= 4 && parts[2].equals("api")) {
            // New pattern: /pluginId/api/version
            version = parts[3];
        } else {
            rsp.sendError(400, "Invalid plugin API path format");
            return;
        }

        // If the plugin hasn't been registered yet, do so now
        if (ApiVersions.getPluginApiVersions(pluginId) == null) {
            // Register the current plugin version
            String rootUrl = Jenkins.get().getRootUrl();
            if (rootUrl == null) {
                rootUrl = "";
            } else if (!rootUrl.endsWith("/")) {
                rootUrl = rootUrl + "/";
            }

            String url =
                    rootUrl + getUrlName() + "/plugin/" + pluginId + "/api/" + ApiVersions.CURRENT_PLUGIN_API_VERSION;
            ApiVersions.registerPluginApi(pluginId, ApiVersions.CURRENT_PLUGIN_API_VERSION, url);
        }

        // Check if this is a supported version
        if (ApiVersions.isValidPluginVersion(pluginId, version)) {
            servePluginApi(pluginId, req, rsp, version);
            return;
        }

        rsp.sendError(404, "Plugin API version not found");
    }

    /**
     * Internal method to serve a plugin API spec
     */
    private void servePluginApi(String pluginId, StaplerRequest req, StaplerResponse rsp, String version)
            throws IOException {
        LOGGER.info("Serving Plugin API specification for: " + pluginId + ", version: " + version);

        try {
            Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
            OpenAPI pluginApi = pluginSpecs.get(pluginId);

            if (pluginApi == null) {
                rsp.sendError(404, "Plugin specification not found: " + pluginId);
                return;
            }

            // Update the version info
            if (pluginApi.getInfo() != null) {
                pluginApi.getInfo().setVersion(version);
            }

            // Set proper CORS headers
            rsp.setContentType("application/json");
            rsp.setHeader("Access-Control-Allow-Origin", "*");
            rsp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
            rsp.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");
            rsp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            rsp.setHeader("Pragma", "no-cache");

            // Add server info if missing
            if (pluginApi.getServers() == null || pluginApi.getServers().isEmpty()) {
                String rootUrl = Jenkins.get().getRootUrl();
                if (rootUrl == null) {
                    rootUrl = req.getContextPath();
                }
                pluginApi.addServersItem(new io.swagger.v3.oas.models.servers.Server()
                        .url(rootUrl)
                        .description("Jenkins Instance"));
            }

            Json.mapper().writeValue(rsp.getWriter(), pluginApi);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating plugin API spec", e);
            rsp.sendError(500, "Error generating plugin API spec: " + e.getMessage());
        }
    }

    /**
     * Serves the OpenAPI specification for a specific plugin (for backward compatibility)
     */
    public void doPluginApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String pluginName = req.getParameter("plugin");
        LOGGER.info("Serving Plugin API specification (legacy endpoint) for: " + pluginName);

        if (pluginName == null || pluginName.isEmpty()) {
            rsp.sendError(400, "Missing plugin parameter");
            return;
        }

        servePluginApi(pluginName, req, rsp, ApiVersions.CURRENT_PLUGIN_API_VERSION);
    }

    /**
     * Lists available API specifications
     */
    public void doApiList(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving API list");
        try {
            Map<String, String> apiList = new HashMap<>();
            String rootUrl = Jenkins.get().getRootUrl();
            if (rootUrl == null) {
                rootUrl = "";
            } else if (!rootUrl.endsWith("/")) {
                rootUrl = rootUrl + "/";
            }

            // Use the swagger-ui URL pattern for core API instead of direct
            apiList.put("core", rootUrl + "swagger-ui/rest/api/" + ApiVersions.CURRENT_CORE_API_VERSION);

            Map<String, OpenAPI> pluginSpecs = pluginScanner.scanInstalledPlugins();
            for (String plugin : pluginSpecs.keySet()) {
                // Use the swagger-ui URL pattern for plugin API instead of direct
                String apiUrl =
                        rootUrl + "swagger-ui/plugin/" + plugin + "/rest/api/" + ApiVersions.CURRENT_PLUGIN_API_VERSION;
                apiList.put(plugin, apiUrl);

                // Register the plugin API if not already registered
                if (ApiVersions.getPluginApiVersions(plugin) == null) {
                    ApiVersions.registerPluginApi(plugin, ApiVersions.CURRENT_PLUGIN_API_VERSION, apiUrl);
                }
            }

            rsp.setContentType("application/json");
            Json.mapper().writeValue(rsp.getWriter(), apiList);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating API list", e);
            rsp.sendError(500, "Error generating API list: " + e.getMessage());
        }
    }

    /**
     * Serves a simple test OpenAPI specification
     * This is useful for testing the Swagger UI integration
     */
    public void doTestApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.info("Serving Test API specification");
        System.out.println("DEBUG: doTestApi method called");
        try {
            // Create a simple OpenAPI spec
            OpenAPI api = new OpenAPI();
            api.info(new io.swagger.v3.oas.models.info.Info()
                    .title("Test API")
                    .description("This is a test API for verifying Swagger UI integration")
                    .version("1.0.0"));

            // Add a simple endpoint
            io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
            io.swagger.v3.oas.models.PathItem pathItem = new io.swagger.v3.oas.models.PathItem();

            // Simple GET operation
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation()
                    .summary("Get test data")
                    .description("Returns test data to verify API integration");

            // Response
            io.swagger.v3.oas.models.responses.ApiResponses responses =
                    new io.swagger.v3.oas.models.responses.ApiResponses();
            io.swagger.v3.oas.models.responses.ApiResponse response =
                    new io.swagger.v3.oas.models.responses.ApiResponse().description("Successful operation");

            // Add a simple schema
            io.swagger.v3.oas.models.media.Schema<Object> schema =
                    new io.swagger.v3.oas.models.media.Schema<>().type("object");
            schema.addProperty("message", new io.swagger.v3.oas.models.media.Schema<>().type("string"));

            response.content(new io.swagger.v3.oas.models.media.Content()
                    .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType().schema(schema)));

            responses.addApiResponse("200", response);
            operation.responses(responses);
            pathItem.get(operation);

            paths.addPathItem("/test", pathItem);
            api.paths(paths);

            // Add a server
            api.addServersItem(new io.swagger.v3.oas.models.servers.Server()
                    .url(Jenkins.get().getRootUrl())
                    .description("Jenkins Instance"));

            rsp.setContentType("application/json");
            System.out.println("DEBUG: About to serialize test API to JSON");
            Json.mapper().writeValue(rsp.getWriter(), api);
            System.out.println("DEBUG: Test API JSON serialization completed");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to generate test API spec: " + e.getMessage());
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, "Error generating test API spec", e);
            rsp.sendError(500, "Error generating test API spec: " + e.getMessage());
        }
    }

    /**
     * Directly serve API spec from /swagger-ui/rest/api/1.0 path
     */
    public void doRestApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String path = req.getRestOfPath();
        LOGGER.info("Direct access to REST API at /swagger-ui/rest/api: " + path);
        System.out.println("DEBUG: Direct access to REST API at /swagger-ui/rest/api: " + path);

        // Extract version from path
        String version = "1.0"; // Default version
        if (path.length() > 1) {
            version = path.substring(1); // Skip leading slash
        }

        // Serve the core API directly
        serveCoreApi(req, rsp, version);
    }
}
