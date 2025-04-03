# Swagger Demo Plugin for Jenkins

## Introduction

This plugin provides an interactive OpenAPI/Swagger UI for exploring and testing the Jenkins REST APIs. It documents both Jenkins core and plugin REST endpoints in a user-friendly interface. 
It is created by following the guideline in [create a plugin tutorial from jenkins.io](https://www.jenkins.io/doc/developer/tutorial/create/)

> **Note:** This is a prototype implementation developed as part of a Google Summer of Code (GSoC) proposal. The current version demonstrates core functionality, but requires performance optimizations and enhancements which will be implemented during the GSoC period if selected.

## Installation
1. Download the [swagger-demo.hpi](swagger-demo.hpi) file from this repository
2. In Jenkins, navigate to "Manage Jenkins" → "Manage Plugins" → "Advanced" tab
3. Under "Upload Plugin", select the downloaded .hpi file and click "Upload"
4. Restart Jenkins when prompted

## Usage
1. After installation, access the Swagger UI at: `http://your-jenkins-server/swagger-ui/`
2. Select an API type (Core or Plugin) from the dropdown
3. Choose a version (currently 1.0 is available)
4. Click "Load API" to view the API documentation
5. Expand endpoints to see available methods, parameters, and response types
6. Try out API calls directly from the UI 

## Testing
You can test this plugin in development mode:
```
mvn hpi:run
```
Then access `http://localhost:8080/jenkins/swagger-ui/`

## Limitations of Current Prototype
- Core API documentation is currently hardcoded and needs to be dynamically generated
- Performance optimizations are needed for large Jenkins installations
- API scanning depth is limited to prevent stack overflow errors
- Limited authentication options in the UI
- Swagger UI is currently iframe tag based in jelly file

To build:
```
mvn clean package
```

The plugin HPI file will be generated at `target/swagger-demo.hpi`

## Note on .hpi File
The [swagger-demo.hpi](swagger-demo.hpi) file in the root of this repository is a pre-built version for easy testing. This file will be updated when significant functional changes are made to the plugin, but not necessarily for every minor dependency update.

---

This plugin is being developed as part of a Google Summer of Code proposal for the Jenkins project as to gain clarity for understanding the [GSoC Project Idea](https://www.jenkins.io/projects/gsoc/2025/project-ideas/swagger-openapi-for-jenkins-rest-api/)

