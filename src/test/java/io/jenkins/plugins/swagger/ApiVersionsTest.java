package io.jenkins.plugins.swagger;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class ApiVersionsTest {

    private static final String TEST_PLUGIN_ID = "test-plugin";
    private static final String TEST_VERSION = "1.0";
    private static final String TEST_URL = "http://jenkins/swagger-ui/plugin/test-plugin/rest/api/1.0";

    @Before
    public void setUp() {
        // Register a test plugin API
        ApiVersions.registerPluginApi(TEST_PLUGIN_ID, TEST_VERSION, TEST_URL);

        // Set a core API URL
        ApiVersions.updateCoreApiUrl(ApiVersions.CURRENT_CORE_API_VERSION, "http://jenkins/swagger-ui/rest/api/1.0");
    }

    @Test
    public void testGetCoreApiVersions() {
        Map<String, String> coreVersions = ApiVersions.getCoreApiVersions();
        assertNotNull("Core versions map should not be null", coreVersions);
        assertTrue(
                "Core versions map should contain the current version",
                coreVersions.containsKey(ApiVersions.CURRENT_CORE_API_VERSION));
    }

    @Test
    public void testGetPluginApiVersions() {
        Map<String, String> pluginVersions = ApiVersions.getPluginApiVersions(TEST_PLUGIN_ID);
        assertNotNull("Plugin versions map should not be null", pluginVersions);
        assertTrue("Plugin versions map should contain the test version", pluginVersions.containsKey(TEST_VERSION));
        assertEquals("Plugin URL should match the registered URL", TEST_URL, pluginVersions.get(TEST_VERSION));
    }

    @Test
    public void testGetPluginsWithApis() {
        Set<String> plugins = ApiVersions.getPluginsWithApis();
        assertNotNull("Plugins set should not be null", plugins);
        assertTrue("Plugins set should contain the test plugin", plugins.contains(TEST_PLUGIN_ID));
    }

    @Test
    public void testRegisterPluginApi() {
        String newPluginId = "another-plugin";
        String newVersion = "2.0";
        String newUrl = "http://jenkins/swagger-ui/plugin/another-plugin/rest/api/2.0";

        ApiVersions.registerPluginApi(newPluginId, newVersion, newUrl);

        Map<String, String> pluginVersions = ApiVersions.getPluginApiVersions(newPluginId);
        assertNotNull("New plugin versions map should not be null", pluginVersions);
        assertTrue(
                "New plugin versions map should contain the registered version",
                pluginVersions.containsKey(newVersion));
        assertEquals("New plugin URL should match the registered URL", newUrl, pluginVersions.get(newVersion));
    }

    @Test
    public void testIsValidCoreVersion() {
        assertTrue(
                "Current core version should be valid",
                ApiVersions.isValidCoreVersion(ApiVersions.CURRENT_CORE_API_VERSION));
        assertFalse("Non-existent core version should be invalid", ApiVersions.isValidCoreVersion("999.999"));
    }

    @Test
    public void testIsValidPluginVersion() {
        assertTrue(
                "Test plugin version should be valid", ApiVersions.isValidPluginVersion(TEST_PLUGIN_ID, TEST_VERSION));
        assertFalse(
                "Non-existent plugin version should be invalid",
                ApiVersions.isValidPluginVersion(TEST_PLUGIN_ID, "999.999"));
        assertFalse(
                "Version for non-existent plugin should be invalid",
                ApiVersions.isValidPluginVersion("non-existent-plugin", TEST_VERSION));
    }
}
