# CapellaAgent

CapellaAgent is an Eclipse/Capella plugin prototype for integrating an LLM assistant into the MBSE workflow. The project adds a chat panel to the Capella environment, captures the currently selected model element as context, sends requests to a language model, and supports controlled model changes in the Operational Analysis layer.

The repository is designed as a proof of concept for AI-assisted engineering work in Capella, with emphasis on safe, reviewable edits rather than direct uncontrolled model mutation.

## Purpose

The main goal of the project is to help engineers work with Capella models using natural-language requests, while keeping the model editing process traceable and explicit.

It includes:

- a Capella-side chat view integrated into the Eclipse UI;
- context extraction from the selected EMF model element;
- LLM interaction for analysis and tool invocation;
- controlled write flow with preview and confirmation before applying changes;
- model creation helpers for Operational Analysis elements such as operational activities and entities.

## Key features

### 1. Capella UI integration

The plugin registers an Eclipse view and menu action that appear in the Capella workbench. The assistant panel listens to selected elements in the model and displays their metadata such as:

- element ID;
- element type;
- element name.

### 2. Context-aware model interaction

Selected Capella objects are converted into structured model context and sent to the LLM together with the current user request. The project uses EMF reflection to work with Capella model elements without hard-coding specific model classes.

### 3. LLM orchestration

The assistant uses a multi-step loop with:

- intent classification;
- model context assembly;
- LLM request generation;
- tool execution when the model should be changed;
- follow-up response handling.

### 4. Controlled write / review flow

The system is designed to separate the proposal and execution of changes. Instead of modifying the Capella model immediately, it builds a change proposal, shows it in the UI, and waits for explicit confirmation before applying the change.

This is especially important in engineering tools where model integrity and traceability matter.

### 5. Capella model modification support

The code includes transaction-based modification routines for Capella elements, including:

- creation of operational activities;
- creation of operational entities;
- insertion into appropriate model containers;
- use of EMF transactions to ensure safe updates.

## Architecture

The project follows a simple plugin-oriented architecture.

### Main Java classes

- `ChatView.java` — SWT UI panel for chat and selected context
- `DialogController.java` — main orchestration logic for user messages and tool execution
- `Router.java` — classification of user intent
- `ContextBuilder.java` — builds LLM context from the selected model element
- `CapellaModelAdapter.java` — EMF-based access to Capella objects and transactional creation logic
- `ToolRegistry.java` — tool registration and execution
- `GigaChatAdapter.java` — communication layer with the LLM backend
- `LLMAdapter.java` — abstraction for model-provider communication
- `ConfirmationManager.java` — handles approval or cancel flow for proposed changes
- `ChangeSetManager.java` and `ChangeSet.java` — change tracking and review logic
- `LocalToolEndpoint.java` — local endpoint used for tool invocation flow
- `AuditLogger.java` — logging and trace support
- `RichTextSanitizer.java` — sanitization of model descriptions before presenting them to the LLM

### Eclipse plugin metadata

- `plugin.xml` — registers the view, command, and menu contributions
- `build.properties` — Eclipse PDE build configuration
- `META-INF/` — plugin metadata

## Requirements

To build and run this project, you need:

- Java 17+
- Eclipse IDE for RCP and RAP Developers or an Eclipse environment compatible with Capella plugin development
- Capella or a Capella-compatible target platform
- EMF and Eclipse plugin dependencies available in the target platform
- access to an LLM backend, such as GigaChat, configured in the application code

## Build and run

This project is not a standalone Java application. It is an Eclipse plugin for the Capella environment.

### Typical workflow

1. Open Eclipse with the required Capella tooling installed.
2. Import the project as an Eclipse plugin project.
3. Make sure the target platform includes Capella dependencies.
4. Build the project with the Eclipse PDE workflow.
5. Launch a runtime Eclipse instance with Capella.
6. Open the assistant panel from the Capella UI.

## Usage

Once the plugin is running inside Capella:

1. Select a model element in the Capella project tree or diagram.
2. Open the AI Assistant view.
3. Enter a natural-language request.
4. The assistant reads the context and proposes an answer or a model change.
5. Review the generated proposal.
6. Apply or cancel the change explicitly.

The workflow is intended to be safe and reviewable, especially for MBSE and model-driven engineering scenarios.

## Notes

This repository is a research and prototype project. It is not a production-ready generic Capella AI product, and it is tightly coupled to the Capella/Eclipse environment and the specific workflows used in the Operational Analysis layer.

The implementation focuses on:

- fast prototyping;
- integration with model tooling;
- controlled model modification;
- practical exploration of LLM-assisted engineering work.

## Repository status

This project is best understood as an experimental integration layer between:

- Capella/EMF model editing;
- the Eclipse RCP workbench;
- an LLM API for reasoning and tool invocation.

## License

The project does not currently declare a license file. If this repository is intended for public reuse, it should be reviewed and a license should be added before redistribution.

## Future work

Potential follow-up work includes:

- better validation of generated model operations;
- broader support for more Capella element types;
- safer prompt and tool schemas;
- structured change sets and auditing;
- integration with enterprise LLM and authentication flows;
- model diff review before execution.

