package io.jenkins.plugins.swagger.scanner;

import hudson.PluginWrapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Scans Jenkins plugins for REST API endpoints
 */
public class PluginApiScanner {
    private static final Logger LOGGER = Logger.getLogger(PluginApiScanner.class.getName());

    /**
     * Scans all installed plugins for REST API endpoints
     * @return Map of plugin name to OpenAPI specification
     */
    public Map<String, OpenAPI> scanInstalledPlugins() {
        Map<String, OpenAPI> pluginSpecs = new HashMap<>();

        Jenkins jenkins = Jenkins.get();
        List<PluginWrapper> plugins = jenkins.getPluginManager().getPlugins();

        LOGGER.info("Scanning " + plugins.size() + " plugins for REST APIs");

        for (PluginWrapper plugin : plugins) {
            try {
                if (plugin.isActive()) {
                    OpenAPI spec = scanSinglePlugin(plugin);
                    if (spec != null) {
                        pluginSpecs.put(plugin.getShortName(), spec);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error scanning plugin: " + plugin.getShortName(), e);
            }
        }

        return pluginSpecs;
    }

    /**
     * Scans a single plugin for REST API endpoints
     * @param plugin The plugin to scan
     * @return OpenAPI specification for the plugin
     */
    private OpenAPI scanSinglePlugin(PluginWrapper plugin) {
        LOGGER.info("Scanning plugin: " + plugin.getShortName());

        OpenAPI openAPI = new OpenAPI();

        // Set basic information
        Info info = new Info()
                .title(plugin.getDisplayName() + " REST API")
                .description("REST API endpoints provided by the " + plugin.getDisplayName() + " plugin")
                .version(plugin.getVersion());
        openAPI.setInfo(info);

        // Add server
        Server server = new Server();
        server.setUrl(Jenkins.get().getRootUrl());
        server.setDescription("Jenkins Instance");
        openAPI.addServersItem(server);

        // Scan for API endpoints
        Paths paths = new Paths();
        openAPI.setPaths(paths);

        // Get the classloader for this plugin
        ClassLoader pluginClassLoader = plugin.classLoader;

        // Scan exported beans in the plugin's package
        scanPluginClasses(plugin, pluginClassLoader, paths);

        return openAPI;
    }

    /**
     * Scans plugin classes for REST API endpoints
     */
    private void scanPluginClasses(PluginWrapper plugin, ClassLoader pluginClassLoader, Paths paths) {
        String pluginId = plugin.getShortName();
        String basePackage = determineBasePackage(plugin);

        try {
            // Use ClassGraph to scan plugin classes with @ExportedBean annotation
            LOGGER.info("Scanning plugin " + pluginId + " for classes with @ExportedBean annotation");

            try (ScanResult scanResult = new ClassGraph()
                    .enableAnnotationInfo()
                    .enableClassInfo()
                    .overrideClassLoaders(pluginClassLoader)
                    .acceptPackages(basePackage != null ? basePackage : "")
                    .scan()) {

                // Find all classes with @ExportedBean annotation
                ClassInfoList exportedBeans = scanResult.getClassesWithAnnotation(ExportedBean.class.getName());
                LOGGER.info("Found " + exportedBeans.size() + " classes with @ExportedBean annotation in plugin "
                        + pluginId);

                // Load each class and scan for API endpoints
                for (ClassInfo classInfo : exportedBeans) {
                    try {
                        Class<?> clazz = classInfo.loadClass();
                        scanClass(clazz, paths, pluginId);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to load class: " + classInfo.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error scanning plugin " + pluginId + " with ClassGraph", e);

            // Fall back to scanning just the main plugin class
            try {
                Class<?> mainClass = pluginClassLoader.loadClass(plugin.getPluginClass());
                scanClass(mainClass, paths, pluginId);
            } catch (ClassNotFoundException e2) {
                LOGGER.log(Level.FINE, "Could not load main class for plugin: " + pluginId, e2);
            }
        }
    }

    /**
     * Attempts to determine the base package for a plugin
     */
    private String determineBasePackage(PluginWrapper plugin) {
        String pluginClass = plugin.getPluginClass();
        if (pluginClass != null && pluginClass.contains(".")) {
            return pluginClass.substring(0, pluginClass.lastIndexOf('.'));
        }
        return null;
    }

    /**
     * Scans a class for API endpoints
     */
    private void scanClass(Class<?> clazz, Paths paths, String pluginId) {
        if (clazz.isAnnotationPresent(ExportedBean.class)) {
            LOGGER.info("Found ExportedBean in plugin " + pluginId + ": " + clazz.getName());

            String basePath = pluginId + "/" + convertToPath(clazz.getSimpleName());

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Exported.class)) {
                    Exported annotation = method.getAnnotation(Exported.class);
                    LOGGER.info("Found Exported method in plugin " + pluginId + ": " + clazz.getSimpleName()
                            + "." + method.getName() + " (visibility: "
                            + annotation.visibility() + ")");

                    addEndpointPath(paths, basePath, method, annotation);
                }
            }
        }
    }

    /**
     * Adds an endpoint path to the Paths object
     */
    private void addEndpointPath(Paths paths, String basePath, Method method, Exported annotation) {
        String methodPath = convertToPath(method.getName());
        String fullPath = "/" + basePath + "/" + methodPath;

        PathItem pathItem = paths.getOrDefault(fullPath, new PathItem());

        Operation operation = new Operation()
                .summary("Get " + formatMethodName(method.getName()))
                .description("REST API endpoint for " + formatMethodName(method.getName()) + " (Visibility: "
                        + annotation.visibility() + ")");

        // Add security requirements based on visibility
        addSecurityRequirements(operation, annotation);

        // Add standard parameters
        operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                .name("depth")
                .in("query")
                .description("Recursion depth for nested objects")
                .schema(new io.swagger.v3.oas.models.media.Schema<Integer>()
                        .type("integer")
                        .example(0)));

        // Add response
        io.swagger.v3.oas.models.responses.ApiResponses responses =
                new io.swagger.v3.oas.models.responses.ApiResponses();
        responses.addApiResponse(
                "200", new io.swagger.v3.oas.models.responses.ApiResponse().description("Successful Response"));
        operation.responses(responses);

        // Set HTTP method based on method name
        String httpMethod = determineHttpMethod(method);
        if ("post".equals(httpMethod)) {
            pathItem.post(operation);
        } else if ("put".equals(httpMethod)) {
            pathItem.put(operation);
        } else if ("delete".equals(httpMethod)) {
            pathItem.delete(operation);
        } else {
            pathItem.get(operation);
        }

        paths.addPathItem(fullPath, pathItem);
    }

    /**
     * Adds security requirements based on visibility level
     */
    private void addSecurityRequirements(Operation operation, Exported annotation) {
        int visibility = annotation.visibility();
        // If visibility is above 0, it likely requires some authentication
        if (visibility > 0) {
            SecurityRequirement security = new SecurityRequirement();
            security.addList("jenkins_auth");
            operation.addSecurityItem(security);

            // Add security tag for documentation
            if (operation.getTags() == null || !operation.getTags().contains("secured")) {
                operation.addTagsItem("secured");
            }
        }
    }

    /**
     * Determines the appropriate HTTP method for a REST endpoint
     * based on method name patterns
     */
    private String determineHttpMethod(Method method) {
        String methodName = method.getName().toLowerCase();

        // Methods that modify state are typically POST/PUT
        if (methodName.startsWith("set")
                || methodName.startsWith("create")
                || methodName.startsWith("add")
                || methodName.startsWith("submit")
                || methodName.startsWith("update")
                || methodName.startsWith("save")
                || methodName.startsWith("modify")) {
            return "post";
        }

        // Methods that delete something
        if (methodName.startsWith("delete") || methodName.startsWith("remove") || methodName.startsWith("clear")) {
            return "delete";
        }

        // Methods that fully update a resource
        if (methodName.startsWith("replace")) {
            return "put";
        }

        // Default to GET for most query/accessor methods
        return "get";
    }

    /**
     * Converts a class or method name to a path segment
     */
    private String convertToPath(String name) {
        // Handle getter methods
        if (name.startsWith("get") && name.length() > 3) {
            String propName = name.substring(3);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        } else if (name.startsWith("is") && name.length() > 2) {
            String propName = name.substring(2);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        }

        // For other names, convert camel case to lowercase with hyphens
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Formats a method name for display in documentation
     */
    private String formatMethodName(String methodName) {
        // Handle getter methods
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String propName = methodName.substring(3);
            return addSpaces(propName);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String propName = methodName.substring(2);
            return "Is " + addSpaces(propName);
        }

        return addSpaces(methodName);
    }

    /**
     * Adds spaces to camel case for better readability
     */
    private String addSpaces(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
