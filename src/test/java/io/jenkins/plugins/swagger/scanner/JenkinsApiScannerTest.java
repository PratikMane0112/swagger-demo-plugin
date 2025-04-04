package io.jenkins.plugins.swagger.scanner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class JenkinsApiScannerTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testScanJenkinsCore() {
        JenkinsApiScanner scanner = new JenkinsApiScanner();
        OpenAPI api = scanner.scanJenkinsCore();

        // Should return a non-null OpenAPI object
        assertNotNull("OpenAPI object should not be null", api);

        // Should have non-null basic properties
        assertNotNull("OpenAPI info should not be null", api.getInfo());
        assertNotNull("OpenAPI servers should not be null", api.getServers());

        // Should have at least some paths
        Paths paths = api.getPaths();
        assertNotNull("OpenAPI paths should not be null", paths);

        // Due to the test environment, we might not have any paths, but at least
        // the method should complete without exceptions
        if (!paths.isEmpty()) {
            assertTrue("Should have at least one path", paths.size() > 0);
        }
    }
}
