# Swagger Demo Plugin for Jenkins

## Introduction

This plugin provides an interactive OpenAPI/Swagger UI for exploring and testing the Jenkins REST APIs. It documents both Jenkins core and plugin REST endpoints in a user-friendly interface. 
It is created by following the guideline in [create a plugin tutorial from jenkins.io](https://www.jenkins.io/doc/developer/tutorial/create/)

> **Note:** This is a prototype implementation developed as part of a Google Summer of Code (GSoC) proposal. The current version demonstrates core functionality, but requires performance optimizations and enhancements which will be implemented during the GSoC period if selected.

## Features
- Interactive Swagger UI for browsing and testing Jenkins REST APIs
- Proper REST API URL versioning (e.g., `/swagger-ui/rest/api/1.0`)
- Support for multiple HTTP methods (GET, POST, DELETE) in API documentation
- Display of request/response bodies for complete API reference
- Plugin API scanning for discovering endpoints in all installed plugins
- Automatic validation of API specifications

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
6. Try out API calls directly from the UI (authentication required for secured endpoints)

## Testing
You can test this plugin in development mode:
```
mvn hpi:run
```
Then access `http://localhost:8080/jenkins/swagger-ui/`

## Technical Details
- The plugin uses the Swagger UI library to render API documentation
- Core API documentation is currently provided with a static OpenAPI schema (will be replaced with dynamic scanning in GSoC)
- Plugin APIs are discovered through scanning for `@Exported` and `@ExportedBean` annotations
- The plugin adds CORS headers to support browser access to API endpoints

## Dependency Management
This repository uses Dependabot for automated dependency updates. Some dependency update PRs may be pending as the focus is on core functionality for the GSoC proposal. Critical security updates will be prioritized if applicable.

## Limitations of Current Prototype
- Core API documentation is currently hardcoded and needs to be dynamically generated
- Performance optimizations are needed for large Jenkins installations
- API scanning depth is limited to prevent stack overflow errors
- Limited authentication options in the UI

## Future Improvements (Planned for GSoC)
If selected for GSoC, I plan to enhance this plugin.

## Getting started

## Development
This plugin follows standard Jenkins plugin development practices:
- Java 11+ compatibility
- Maven for building and testing
- GitHub Actions for CI/CD
- SpotBugs for static code analysis

To build:
```
mvn clean package
```

The plugin HPI file will be generated at `target/swagger-demo.hpi`

## Note on .hpi File
The [swagger-demo.hpi](swagger-demo.hpi) file in the root of this repository is a pre-built version for easy testing. This file will be updated when significant functional changes are made to the plugin, but not necessarily for every minor dependency update.


## LICENSE
Licensed under MIT, see [LICENSE](LICENSE.md)

---

This plugin is being developed as part of a Google Summer of Code proposal for the Jenkins project.

