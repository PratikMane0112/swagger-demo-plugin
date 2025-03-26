package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ExportedBean
@Extension
public class ProjectInfoAction implements RootAction {
    private List<ProjectDetail> projects = new ArrayList<>();

    public ProjectInfoAction() {
        // Add some sample projects
        projects.add(new ProjectDetail("Sample Project 1", "Description 1"));
        projects.add(new ProjectDetail("Sample Project 2", "Description 2"));
    }

    @Override
    public String getIconFileName() {
        return "/plugin/your-plugin-shortname/icons/notepad.png"; // Adjust path as needed
    }

    @Override
    public String getDisplayName() {
        return "Project Information";
    }

    @Override
    public String getUrlName() {
        return "project-info";
    }

    // Ensure this method is present for Stapler to recognize the action
    public Object getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
        return this;
    }

    @Exported
    public List<ProjectDetail> getProjects() {
        return projects;
    }

    @WebMethod(name = "add")
    public void doAdd(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        JSONObject json = req.getSubmittedForm();
        String name = json.getString("name");
        String description = json.getString("description");
        projects.add(new ProjectDetail(name, description));
        rsp.sendRedirect(Jenkins.get().getRootUrl() + getUrlName());
    }

    @ExportedBean
    public static class ProjectDetail {
        private String name;
        private String description;

        public ProjectDetail(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Exported
        public String getName() {
            return name;
        }

        @Exported
        public String getDescription() {
            return description;
        }
    }
}