package io.jenkins.plugins.swagger.scanner;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Scans Jenkins for REST API endpoints
 */
public class JenkinsApiScanner {
    private static final Logger LOGGER = Logger.getLogger(JenkinsApiScanner.class.getName());
    private final Map<Class<?>, Schema<?>> schemaCache = new HashMap<>();

    /**
     * Generates OpenAPI specification for Jenkins core
     * @return OpenAPI specification object
     */
    public OpenAPI scanJenkinsCore() {
        LOGGER.info("Scanning Jenkins core for REST APIs");

        OpenAPI openAPI = new OpenAPI();

        // Set basic information
        Info info = new Info()
                .title("Jenkins Core REST API")
                .description("REST API endpoints provided by Jenkins core")
                .version("1.0.0");
        openAPI.setInfo(info);

        // Add server
        Server server = new Server();
        server.setUrl(Jenkins.get().getRootUrl());
        server.setDescription("Jenkins Instance");
        openAPI.addServersItem(server);

        // Create paths object
        Paths paths = new Paths();
        openAPI.setPaths(paths);

        // Scan for exported annotations and add to paths
        scanForExportedAnnotations(paths);

        return openAPI;
    }

    /**
     * Scans for @Exported and @ExportedBean annotations
     */
    private void scanForExportedAnnotations(Paths paths) {
        // Get all classes that might have REST API endpoints
        List<Class<?>> apiClasses = findPotentialApiClasses();

        for (Class<?> clazz : apiClasses) {
            // Check if class has @ExportedBean annotation
            if (clazz.isAnnotationPresent(ExportedBean.class)) {
                ExportedBean exportedBean = clazz.getAnnotation(ExportedBean.class);
                LOGGER.info("Found ExportedBean: " + clazz.getName());

                // Determine base path
                String basePath = convertClassNameToPath(clazz.getSimpleName());

                // Scan methods for @Exported annotation
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Exported.class)) {
                        Exported annotation = method.getAnnotation(Exported.class);
                        LOGGER.info("Found Exported method: " + method.getName() + " visibility: "
                                + annotation.visibility());

                        // Create OpenAPI path for this method
                        addPathForExportedMethod(paths, basePath, clazz, method, annotation);
                    }
                }
            }
        }
    }

    /**
     * Adds a path for an exported method
     */
    private void addPathForExportedMethod(
            Paths paths, String basePath, Class<?> clazz, Method method, Exported annotation) {
        // Determine path based on method name
        String methodPath = convertMethodNameToPath(method.getName());
        String fullPath = "/" + basePath + "/" + methodPath;

        // Create path item if it doesn't exist
        PathItem pathItem = paths.getOrDefault(fullPath, new PathItem());

        // Create operation
        Operation operation =
                new Operation().summary(getMethodSummary(method)).description(getMethodDescription(method, annotation));

        // Add method parameters
        addParametersToOperation(operation, method);

        // Add security requirements
        addSecurityRequirements(operation, method, annotation);

        // Add response
        addResponseToOperation(operation, method);

        // Set the operation on the path item based on the determined HTTP method
        String httpMethod = determineHttpMethod(method);
        if ("post".equals(httpMethod)) {
            pathItem.post(operation);
        } else if ("put".equals(httpMethod)) {
            pathItem.put(operation);
        } else if ("delete".equals(httpMethod)) {
            pathItem.delete(operation);
        } else {
            // Default to GET for most methods
            pathItem.get(operation);
        }

        // Add or update the path
        paths.addPathItem(fullPath, pathItem);
    }

    /**
     * Adds parameters to an operation
     */
    private void addParametersToOperation(Operation operation, Method method) {
        // Add common parameters used in Jenkins REST API
        Parameter depthParam = new Parameter()
                .name("depth")
                .in("query")
                .description("Recursion depth for nested objects")
                .schema(new Schema<Integer>().type("integer").example(0));
        operation.addParametersItem(depthParam);

        Parameter treeParam = new Parameter()
                .name("tree")
                .in("query")
                .description("Specify which fields to include using dot notation (e.g. jobs[name,url])")
                .schema(new Schema<String>().type("string"));
        operation.addParametersItem(treeParam);

        // Add wrapper parameter if supported by the method
        if (method.getReturnType() != null && !method.getReturnType().isPrimitive()) {
            Parameter wrapperParam = new Parameter()
                    .name("wrapper")
                    .in("query")
                    .description("Wrap the response in a specific element")
                    .schema(new Schema<String>().type("string"));
            operation.addParametersItem(wrapperParam);
        }
    }

    /**
     * Adds response to an operation
     */
    private void addResponseToOperation(Operation operation, Method method) {
        ApiResponses responses = new ApiResponses();

        // Create 200 OK response with return type information
        ApiResponse okResponse = new ApiResponse().description("Successful Response");

        // Add response schema based on return type
        Content content = new Content();
        MediaType jsonMedia = new MediaType();

        // Create schema based on return type and its generic parameters
        Schema<?> schema = createSchemaForType(method.getGenericReturnType());
        jsonMedia.schema(schema);

        content.addMediaType("application/json", jsonMedia);
        okResponse.content(content);

        responses.addApiResponse("200", okResponse);

        // Add error responses
        ApiResponse errorResponse = new ApiResponse().description("Authentication Error");
        responses.addApiResponse("401", errorResponse);

        ApiResponse notFoundResponse = new ApiResponse().description("Resource Not Found");
        responses.addApiResponse("404", notFoundResponse);

        operation.responses(responses);
    }

    /**
     * Creates a schema for a given Java type, handling generic types
     */
    private Schema<?> createSchemaForType(Type type) {
        return createSchemaForType(type, 0);
    }

    /**
     * Creates a schema for a given Java type, handling generic types with recursion limit
     * @param type The Java type to create a schema for
     * @param depth Current recursion depth to avoid infinite recursion
     * @return A schema representing the type
     */
    private Schema<?> createSchemaForType(Type type, int depth) {
        // Limit recursion depth to prevent stack overflow
        if (depth > 3) {
            Schema<?> schema = new Schema<>().type("object");
            schema.description("Recursion limit reached - object details omitted");
            return schema;
        }

        // For basic Class types, use the existing createSchemaForType method
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            // Check cache first
            if (schemaCache.containsKey(clazz)) {
                return schemaCache.get(clazz);
            }

            // Create new schema for this type
            Schema<?> schema = createBasicSchemaForClass(clazz, depth);
            // Add to cache before recursive calls to prevent infinite loops
            schemaCache.put(clazz, schema);
            return schema;
        }

        // Handle parameterized types (generics)
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            if (rawType instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) rawType;

                // Handle List<T>, Set<T> and other collection types
                if (Collection.class.isAssignableFrom(rawClass)) {
                    Schema<?> arraySchema = new Schema<>().type("array");
                    if (typeArguments.length > 0) {
                        Type itemType = typeArguments[0];
                        arraySchema.items(createSchemaForType(itemType, depth + 1));
                    } else {
                        arraySchema.items(new Schema<>().type("object"));
                    }
                    return arraySchema;
                }

                // Handle Map<K,V>
                if (Map.class.isAssignableFrom(rawClass)) {
                    Schema<?> mapSchema = new Schema<>().type("object");
                    if (typeArguments.length > 1) {
                        Type valueType = typeArguments[1];
                        Schema<?> additionalProperties = createSchemaForType(valueType, depth + 1);
                        mapSchema.additionalProperties(additionalProperties);
                    } else {
                        mapSchema.additionalProperties(new Schema<>().type("object"));
                    }
                    return mapSchema;
                }
            }
        }

        // Default to generic object for unsupported types
        return new Schema<>().type("object");
    }

    /**
     * Creates a basic schema for a class
     * @param clazz the class to create a schema for
     * @param depth current recursion depth
     * @return A schema representing the class
     */
    private Schema<?> createBasicSchemaForClass(Class<?> clazz, int depth) {
        // Handle primitive types and common Java types
        if (clazz.isPrimitive()
                || clazz.equals(String.class)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.equals(clazz)) {
            return createPrimitiveSchema(clazz);
        }

        // For arrays, return an array schema
        if (clazz.isArray()) {
            Schema<?> arraySchema = new Schema<>().type("array");
            Class<?> componentType = clazz.getComponentType();
            arraySchema.items(createSchemaForType(componentType, depth + 1));
            return arraySchema;
        }

        // For enums, create an enum schema
        if (clazz.isEnum()) {
            Schema<?> enumSchema = new Schema<>().type("string");
            enumSchema.description("Enum: " + clazz.getSimpleName());
            return enumSchema;
        }

        // For all other objects, create an object schema
        Schema<?> objectSchema = new Schema<>().type("object");
        objectSchema.setProperties(new HashMap<>());

        // Check for @ExportedBean and add exported fields
        if (clazz.isAnnotationPresent(ExportedBean.class)) {
            ExportedBean exportedBean = clazz.getAnnotation(ExportedBean.class);
            objectSchema.description("Bean: " + exportedBean.defaultVisibility());

            // Only proceed if we haven't exceeded recursion limit
            if (depth < 3) {
                // Add properties based on exported methods
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Exported.class)) {
                        addPropertyFromExportedMethod(objectSchema, method, depth);
                    }
                }
            }
        }

        return objectSchema;
    }

    /**
     * Adds a property to an object schema based on an exported method
     * @param objectSchema The schema to add the property to
     * @param method The method with the @Exported annotation
     * @param depth Current recursion depth
     */
    private void addPropertyFromExportedMethod(Schema<?> objectSchema, Method method, int depth) {
        Exported annotation = method.getAnnotation(Exported.class);
        String methodName = method.getName();

        // Handle getter methods (getXxx, isXxx)
        String propertyName = methodName;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            propertyName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            propertyName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }

        // Name from annotation takes precedence
        if (!annotation.name().isEmpty()) {
            propertyName = annotation.name();
        }

        // Add the property to the schema
        Schema<?> propertySchema;
        if (depth >= 3) {
            propertySchema = new Schema<>().type("object");
            propertySchema.description("Recursion limit reached");
        } else {
            propertySchema = createSchemaForType(method.getGenericReturnType(), depth + 1);
        }

        objectSchema.getProperties().put(propertyName, propertySchema);
    }

    /**
     * Create schema for a primitive or simple type
     */
    private Schema<?> createPrimitiveSchema(Class<?> clazz) {
        Schema<?> schema = new Schema<>();

        if (clazz.equals(String.class)) {
            schema.type("string");
        } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            schema.type("boolean");
        } else if (Number.class.isAssignableFrom(clazz)
                || clazz.equals(int.class)
                || clazz.equals(long.class)
                || clazz.equals(float.class)
                || clazz.equals(double.class)
                || clazz.equals(short.class)
                || clazz.equals(byte.class)) {
            schema.type("number");

            // Add format for specific number types
            if (clazz.equals(Integer.class)
                    || clazz.equals(int.class)
                    || clazz.equals(Short.class)
                    || clazz.equals(short.class)
                    || clazz.equals(Byte.class)
                    || clazz.equals(byte.class)) {
                schema.format("int32");
            } else if (clazz.equals(Long.class) || clazz.equals(long.class)) {
                schema.format("int64");
            } else if (clazz.equals(Float.class) || clazz.equals(float.class)) {
                schema.format("float");
            } else if (clazz.equals(Double.class) || clazz.equals(double.class)) {
                schema.format("double");
            }
        } else {
            // Default to object for unknown types
            schema.type("object");
        }

        return schema;
    }

    /**
     * Attempts to extract Javadoc description from a method
     * Note: This is a simplified implementation as runtime Javadoc
     * extraction requires additional libraries
     */
    private String extractJavadocDescription(Method method) {
        // In a real implementation, this would use a library like
        // com.github.therapi:therapi-runtime-javadoc to extract Javadoc
        // For now, we'll return null
        return null;
    }

    /**
     * Extracts property name from getter method
     */
    private String extractPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String propName = methodName.substring(3);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String propName = methodName.substring(2);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        }
        return null;
    }

    /**
     * Gets a summary for a method
     */
    private String getMethodSummary(Method method) {
        return "Get " + convertMethodNameToTitle(method.getName());
    }

    /**
     * Gets a description for a method
     */
    private String getMethodDescription(Method method, Exported annotation) {
        StringBuilder desc = new StringBuilder();

        // Try to get Javadoc description first
        String javadocDesc = extractJavadocDescription(method);
        if (javadocDesc != null && !javadocDesc.isEmpty()) {
            desc.append(javadocDesc);
        } else {
            desc.append("REST API endpoint for ").append(convertMethodNameToTitle(method.getName()));
        }

        desc.append(" (Visibility: ").append(annotation.visibility()).append(")");

        if (!method.getReturnType().equals(Void.TYPE)) {
            desc.append("\n\nReturns: ").append(method.getReturnType().getSimpleName());

            // Add generic type information if available
            if (method.getGenericReturnType() instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) method.getGenericReturnType();
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    desc.append("<");
                    for (int i = 0; i < typeArgs.length; i++) {
                        if (i > 0) desc.append(", ");
                        desc.append(getTypeName(typeArgs[i]));
                    }
                    desc.append(">");
                }
            }
        }

        return desc.toString();
    }

    /**
     * Gets a simplified name for a type
     */
    private String getTypeName(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        }
        return type.toString();
    }

    /**
     * Converts a class name to a path segment
     */
    private String convertClassNameToPath(String className) {
        // Convert camel case to lowercase with hyphens
        return className.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Converts a method name to a path segment
     */
    private String convertMethodNameToPath(String methodName) {
        // Handle getter methods
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String propName = methodName.substring(3);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String propName = methodName.substring(2);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        }

        // For other methods, convert camel case to lowercase with hyphens
        return methodName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Converts a method name to a title (for documentation)
     */
    private String convertMethodNameToTitle(String methodName) {
        // Handle getter methods
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String propName = methodName.substring(3);
            return addSpacesToCamelCase(propName);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String propName = methodName.substring(2);
            return "Is " + addSpacesToCamelCase(propName);
        }

        return addSpacesToCamelCase(methodName);
    }

    /**
     * Adds spaces to camel case for better readability
     */
    private String addSpacesToCamelCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Finds classes that might have REST API endpoints
     */
    private List<Class<?>> findPotentialApiClasses() {
        List<Class<?>> result = new ArrayList<>();
        System.out.println("DEBUG: Finding potential API classes using ClassGraph");

        try {
            // Use ClassGraph to scan the classpath for classes with the @ExportedBean annotation
            try (ScanResult scanResult = new ClassGraph()
                    .enableAnnotationInfo()
                    .acceptPackages("hudson", "jenkins", "org.jenkins", "io.jenkins")
                    .scan()) {

                System.out.println("DEBUG: ClassGraph scan completed");
                ClassInfoList exportedBeanClasses = scanResult.getClassesWithAnnotation(ExportedBean.class.getName());
                System.out.println(
                        "DEBUG: Found " + exportedBeanClasses.size() + " classes with @ExportedBean annotation");

                for (ClassInfo classInfo : exportedBeanClasses) {
                    try {
                        Class<?> clazz = classInfo.loadClass();
                        result.add(clazz);
                        System.out.println("DEBUG: Added class " + clazz.getName());
                    } catch (Exception e) {
                        System.out.println(
                                "WARNING: Failed to load class " + classInfo.getName() + ": " + e.getMessage());
                        LOGGER.log(Level.WARNING, "Failed to load class " + classInfo.getName(), e);
                    }
                }
            }

            // If we found any classes, return them
            if (!result.isEmpty()) {
                System.out.println("DEBUG: Successfully found " + result.size() + " API classes");
                return result;
            } else {
                System.out.println(
                        "WARNING: No API classes found with ClassGraph scanning, falling back to hardcoded list");
            }
        } catch (Exception e) {
            System.out.println("ERROR: ClassGraph scanning failed: " + e.getMessage());
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, "Failed to scan for @ExportedBean classes", e);
        }

        // Fallback to a hardcoded list of core Jenkins classes known to have REST APIs
        System.out.println("DEBUG: Using fallback list of core Jenkins classes");
        try {
            result.add(Class.forName("hudson.model.Hudson"));
            result.add(Class.forName("hudson.model.User"));
            result.add(Class.forName("hudson.model.Job"));
            result.add(Class.forName("hudson.model.Run"));
            result.add(Class.forName("hudson.model.View"));
            result.add(Class.forName("hudson.model.Node"));
            result.add(Class.forName("jenkins.model.Jenkins"));
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR: Failed to load fallback classes: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to load hardcoded Jenkins classes", e);
        }

        System.out.println("DEBUG: Returning " + result.size() + " potential API classes");
        return result;
    }

    /**
     * Adds security requirements based on visibility level
     */
    private void addSecurityRequirements(Operation operation, Method method, Exported annotation) {
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
}
