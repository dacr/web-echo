# Agent Guide for Web-Echo

This document provides context and instructions for AI agents working on the `web-echo` project.

## Project Overview
`web-echo` is an immutable JSON data recording service. It allows users to create recorders that capture data via webhooks or WebSockets. It uses a blockchain-like approach (hashing) to ensure data integrity.

## Tech Stack
- **Language**: Scala 3
- **Build Tool**: sbt (and scala-cli for quick starts)
- **Web Framework**: Pekko HTTP (formerly Akka HTTP)
- **API Definition**: sttp Tapir (generates OpenAPI specs)
- **JSON**: jsoniter-scala
- **Templating**: Twirl (Play Framework's template engine)
- **Frontend**: Bootstrap 5, jQuery (via WebJars)
- **Configuration**: PureConfig
- **Testing**: ScalaTest

## Project Structure
- `src/main/scala/webecho`: Core application logic.
    - `Main.scala`: Application entry point.
    - `Service.scala`: Main service wiring.
    - `ServiceConfig.scala`: Configuration case classes.
    - `ServiceDependencies.scala`: Dependency injection wiring.
    - `apimodel/`: API data models (Tapir schemas).
    - `model/`: Internal domain models.
    - `routing/`: HTTP routes and API endpoint definitions.
        - `ApiEndpoints.scala`: Tapir endpoint definitions.
        - `ApiRoutes.scala`: Logic implementing the endpoints.
    - `dependencies/`: implementations of stores and bots.
- `src/main/twirl`: HTML templates.
- `src/main/resources`: Configuration (`reference.conf`, `logback.xml`) and static assets.
- `src/test`: Unit and integration tests.

## Key Commands
- **Run Application**: `sbt run`
- **Run Tests**: `sbt test`
- **Generate OpenAPI Specs**: `make openapi` (or `sbt "run --just-generate-openapi-specs docs/web-echo-api-docs.json"`)
- **Package**: `sbt stage` or `sbt dist`

## Architectural Patterns
- **Dependency Injection**: Manual DI via `ServiceDependencies` trait and object.
- **Routing**: Split between UI routes (`HomeRouting`, `AssetsRouting`) and API routes (`ApiRoutes`).
- **Business Logic**: Core application logic is encapsulated in `WebEchoBusinessLogic` to ensure DRYness and consistency between API and UI routes.
- **Data Storage**: Abstracted via `EchoStore` trait. Implementations include `EchoStoreFileSystem` (default) and `EchoStoreMemOnly`.
- **API Design**: Endpoint definitions (`ApiEndpoints`) are separated from business logic (`ApiRoutes`).

## Development Guidelines
1.  **OpenAPI**: When modifying the API, update `ApiEndpoints.scala` and ensure the OpenAPI spec is regenerated using `make openapi`.
2.  **Configuration**: Add new configuration parameters to `ServiceConfig.scala` and `reference.conf` (or `application.conf`).
3.  **Templates**: UI changes should be made in `src/main/twirl`.
4.  **Formatting**: Follow existing code style.

## Important Notes
- The project supports Nix for reproducible builds (`flake.nix`).
- The logo is an SVG located at `images/logo.svg` and `src/main/resources/webecho/static-content/images/logo.svg`.
