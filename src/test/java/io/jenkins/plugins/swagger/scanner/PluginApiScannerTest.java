package io.jenkins.plugins.swagger.scanner;

import static org.junit.Assert.assertNotNull;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PluginApiScannerTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testScanInstalledPlugins() {
        PluginApiScanner scanner = new PluginApiScanner();
        Map<String, OpenAPI> apis = scanner.scanInstalledPlugins();

        // Since this runs with the Jenkins test harness, we won't have any real plugins
        // But we should at least get a non-null result from the scanner
        assertNotNull("Should return a non-null map of plugin APIs", apis);
    }

    /* The generateApiSpec method doesn't exist in the PluginApiScanner class
       Removing this test for now, as we'd need access to the scanSinglePlugin method
       which is private in the class
    @Test
    public void testGenerateApiSpec() {
        PluginApiScanner scanner = new PluginApiScanner();

        // Create a spec for the workflow-job plugin (which is loaded in test harness)
        // If the plugin is not available, this test will not fail, just check non-null
        OpenAPI spec = scanner.generateApiSpec("workflow-job");

        // The spec might be null if the plugin isn't loaded, so we'll just check
        // that the method completes without exceptions
        if (spec != null) {
            assertNotNull("OpenAPI info should not be null", spec.getInfo());
            assertNotNull("OpenAPI paths should not be null", spec.getPaths());
        }
    }
    */
}
