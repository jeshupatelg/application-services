# Portfolio Orchestrator

A lightweight, high-performance modular microservice that orchestrates financial portfolio holdings and values, designed to deploy seamlessly in Kubernetes and Docker container environments.

---

## Architecture Overview

This repository is structured as a **multi-module Maven project** to maintain separate concerns and clean component isolation, mirroring the architecture of the `api-gateway` project:

1. **`app`**: The application parent wrapper module.
   - **`common`**: The **entry execution module** that compiles the main class, houses the configurations, and builds the final fat JAR.
   - **`orchestrator`**: A **library module** containing core controllers, DTOs, and business logic that `common` imports.
2. **`helm`**: The Kubernetes packaging module containing the Helm chart configurations.
3. **`docker`**: The Docker configuration module containing containerization files.

---

## Quick Start

### Prerequisites
- **Java 21** or higher
- **Maven 3.9+** (embedded wrapper provided)
- **Docker & Docker Compose** (for containerized runs)

### Local Build
To clean compile the codebase, run tests, and package the executable JAR, execute:
```bash
./mvnw clean install
```
The runnable Spring Boot fat JAR will be packaged in the entry module target folder:
`app/common/target/portfolio-orchestrator.jar`

### Running the Microservice
You can run the application directly using the Maven plugin from the root:
```bash
./mvnw -pl app/common spring-boot:run
```
Alternatively, execute the packaged JAR:
```bash
java -jar app/common/target/portfolio-orchestrator.jar
```

---

## Containerization (Docker)

To build and run the application locally inside Docker containers, follow these instructions:

### Build JAR & Run with Docker Compose
1. Clean package the Java application to build the target fat JAR:
   ```bash
   ./mvnw clean package
   ```
2. Launch the containerized service using Docker Compose:
   ```bash
   docker compose -f docker/docker-compose.yml up --build
   ```

### Manual Docker Build
If you prefer building the image manually from the repository root:
```bash
docker build -t jpg/portfolio-orchestrator:latest -f docker/Dockerfile .
docker run -d -p 8080:8080 --name portfolio-orchestrator jpg/portfolio-orchestrator:latest
```

---

## REST API Endpoints

Once the application is running, the following endpoints are exposed on port `8080`:

| HTTP Method | Path | Description |
|---|---|---|
| **GET** | `/api/v1/portfolio/items` | Retrieves a mock list of portfolio investments and assets. |
| **GET** | `/actuator/health` | Overall system health status. |
| **GET** | `/actuator/health/liveness` | Kubernetes/Docker Liveness Probe endpoint. |
| **GET** | `/actuator/health/readiness` | Kubernetes/Docker Readiness Probe endpoint. |

---

## Kubernetes Deployment (Helm)

To deploy the **Portfolio Orchestrator** inside a Kubernetes cluster, configure the target namespace and run:

```bash
helm upgrade --install portfolio-orchestrator ./helm/portfolio-orchestrator \
  --namespace default \
  --values ./helm/portfolio-orchestrator/values.yaml
```

### Configurable Deployment Values
Review and update `helm/portfolio-orchestrator/values.yaml` to configure:
- **`replicaCount`**: Number of running pod instances.
- **`image`**: Repository, tag, and pullPolicy details.
- **`resources`**: Memory/CPU requests and limits.
- **`ingress`**: Domain hosts and SSL routing policies.

---

## Validation Architecture & Design Decisions

To ensure premium application security and clean coding practices, we employ a custom validation framework:

### Self-Registering Validation Chain
We utilize a **Validation Chain** resembling Spring Security's `FilterChain`. 
- **Decoupled Architecture**: Individual validation rules (e.g. `ZipBombValidation`, `StaticFileOnlyValidation`) are implemented as self-contained Spring `@Component` beans.
- **Self-Registration**: The central `ValidationChain` automatically autowires all available `ValidationStep` beans in the Spring context.
- **Ordered Execution**: The beans declare their priority order using Spring's standard `@Order` annotation (e.g., executing critical Zip Bomb validation first).
- **Dynamic Scoping**: Each bean specifies which scenario (`UPLOAD`, `ACTIVATE`, `DELETE`) it applies to via a `supports(ValidationScenario)` method, allowing the chain to filter and execute the correct steps on the fly.

### Architectural Implications & Future Scope
- **Current Choice**: Leveraging the `supports()` mechanism in the chain provides a simple, clean, and extensible approach that requires zero manual bean mapping or factory wiring.
- **Future Scope**: As scenario-based complexities grow or require conditional runtime parameters (such as environment-dependent step bypasses), we can seamlessly migrate to a `ValidationChainFactory` that builds custom chain lists dynamically at runtime. This can be done without modifying any existing `ValidationStep` implementations.
