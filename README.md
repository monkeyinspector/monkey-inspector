# Monkey Inspector

Monkey Inspector is a lightweight runtime inspector for
[jMonkeyEngine](https://jmonkeyengine.org/) applications. It runs inside the
game and serves a local browser UI without a separate agent or web framework.

## Features

- Live Scene Graph for `rootNode` and `guiNode`
- Spatial transforms, culling and render information, and mesh statistics
- Attached `Control` instances and live reflected fields
- AppState discovery and arbitrary watched objects
- Runtime class, inheritance, interface, and field-dependency graphs
- Explicit workflow tracing with call counts and timings
- jMonkeyEngine frame and pipeline profiling
- Local-only HTTP server by default
- No runtime dependency beyond jMonkeyEngine

## Requirements

- Java 17 or newer
- jMonkeyEngine 3.9

## Installation

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.monkeyinspector:monkeyinspector:0.3.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.monkeyinspector</groupId>
    <artifactId>monkeyinspector</artifactId>
    <version>0.3.0</version>
</dependency>
```

## Usage

Attach `InspectorState` during application initialization:

```java
import com.jme3.app.SimpleApplication;
import io.github.monkeyinspector.InspectorState;

public final class Game extends SimpleApplication {
    @Override
    public void simpleInitApp() {
        InspectorState inspector = new InspectorState()
                .watch("NPC manager", npcManager)
                .watch(worldManager);

        stateManager.attach(inspector);
        System.out.println(inspector.getInspectorUrl());
    }
}
```

Open `http://127.0.0.1:7331/` while the game is running.

The default server only listens on the loopback interface. If you bind it to
another address, protect access at the network boundary because the inspector
exposes runtime state.

## Configuration

`InspectorConfig` is immutable and provides copy methods for common options:

```java
InspectorConfig config = InspectorConfig.defaults()
        .withPort(8080)
        .withSnapshotInterval(0.5f)
        .withMaxSceneNodes(10_000)
        .withMaxFieldsPerObject(32)
        .withEngineProfiling(true);

stateManager.attach(new InspectorState(config));
```

## Workflow tracing

Wrap code with `InspectorTrace` to populate the Workflow graph:

```java
InspectorTrace.runSpan("NPCState.update", () -> {
    InspectorTrace.runSpan("NpcManager.update", npcManager::update);
});

Path path = InspectorTrace.callSpan(
        "Pathfinder.findPath",
        () -> pathfinder.findPath(from, to)
);
```

For manual spans, use try-with-resources:

```java
try (InspectorTrace.Span span = InspectorTrace.begin("load-world")) {
    loadWorld();
}
```

`runSpan` and `callSpan` automatically record unchecked failures. A manual
span can be marked with `span.failed()` before it is closed.

## How it works

The jMonkeyEngine update thread snapshots runtime state into immutable JSON.
The HTTP thread only serves the latest JSON value, so it never traverses the
Scene Graph concurrently.

```text
jME update thread                 browser
      |                              ^
      | SnapshotBuilder              |
      v                              |
AtomicReference<String> -----> local HTTP server
```

## License

Licensed under the [Apache License 2.0](LICENSE).
