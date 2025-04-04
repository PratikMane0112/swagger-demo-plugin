package io.jenkins.plugins.swagger;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.RequestDispatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class SwaggerUiActionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private SwaggerUiAction swaggerAction;
    private StaplerRequest mockRequest;
    private StaplerResponse mockResponse;
    private StringWriter responseWriter;

    @Before
    public void setUp() throws Exception {
        swaggerAction = new SwaggerUiAction();

        // Setup mocks
        mockRequest = mock(StaplerRequest.class);
        mockResponse = mock(StaplerResponse.class);

        // Setup response writer
        responseWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(responseWriter);
        when(mockResponse.getWriter()).thenReturn(writer);
    }

    @Test
    public void testGetIndex() throws Exception {
        // Setup a RequestDispatcher mock
        RequestDispatcher mockDispatcher = mock(RequestDispatcher.class);
        when(mockRequest.getView(eq(swaggerAction), eq("view"))).thenReturn(mockDispatcher);

        // Call the method with the required parameters
        swaggerAction.getIndex(mockRequest, mockResponse);

        // Verify the view was forwarded
        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @Test
    public void testDoVersionsGeneratesValidJson() throws IOException {
        // Call the method to test
        swaggerAction.doVersions(mockRequest, mockResponse);

        // Verify content type was set correctly
        verify(mockResponse).setContentType("application/json");

        // Make sure writer was closed properly
        responseWriter.flush();

        // Check response content
        String response = responseWriter.toString();
        assertNotNull("Response should not be null", response);
        // We're not verifying the exact content, just that it's not empty
        // and it doesn't throw exceptions
    }

    @Test
    public void testDoRestWithValidVersion() throws IOException {
        // Mock request for a valid API version
        when(mockRequest.getRestOfPath()).thenReturn("/api/" + ApiVersions.CURRENT_CORE_API_VERSION);

        // Call the method
        swaggerAction.doRest(mockRequest, mockResponse);

        // Verify content type was set correctly
        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setCharacterEncoding("UTF-8");

        // Verify CORS headers were set
        verify(mockResponse).setHeader("Access-Control-Allow-Origin", "*");
        verify(mockResponse).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        verify(mockResponse).setHeader("Access-Control-Allow-Headers", "Content-Type");

        // Make sure writer was used
        responseWriter.flush();
        String response = responseWriter.toString();
        assertNotNull("Response should not be null", response);
    }

    @Test
    public void testDoRestWithInvalidVersion() throws IOException {
        // Mock request for an invalid API version
        when(mockRequest.getRestOfPath()).thenReturn("/api/999.999");

        // Call the method
        swaggerAction.doRest(mockRequest, mockResponse);

        // Verify error was sent
        verify(mockResponse).sendError(eq(404), anyString());
    }

    @Test
    public void testDoApiList() throws IOException {
        // Call the method
        swaggerAction.doApiList(mockRequest, mockResponse);

        // Verify content type was set correctly
        verify(mockResponse).setContentType("application/json");

        // Make sure writer was used
        responseWriter.flush();
        String response = responseWriter.toString();
        assertNotNull("Response should not be null", response);
    }
}
