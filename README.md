# Monkey Inspector 0.1

A tiny runtime inspector for **jMonkeyEngine**. It runs inside the game and serves a local browser UI.

## What works in this MVP

- Live Scene Graph (`rootNode` + `guiNode`)
- `Spatial` transforms, culling/render info, triangle/vertex counts
- Attached `Control`s
- Runtime Java class inheritance + implemented interfaces
- AppState discovery (best-effort reflection over `AppStateManager`)
- Arbitrary watched subsystem/service objects
- Explicit runtime workflow tracing with call counts and elapsed time
- Local-only server by default (`127.0.0.1:7331`)
- No Ktor/Netty/Jackson dependency; only jME is needed

## Add it to a game

Build/publish the library, or include this project as a Gradle composite/module. Then:

```kotlin
import io.github.monkeyinspector.InspectorState

class Game : SimpleApplication() {
    override fun simpleInitApp() {
        val inspector = InspectorState()
            .watch(myNpcManager)
            .watch(myWorldManager)

        stateManager.attach(inspector)
        // Open http://127.0.0.1:7331/
    }
}
```

If you paste the source directly into the game project, the same code works without publishing a jar.

## Runtime workflow trace

Java:

```java
InspectorTrace.runSpan("NPCState.update", () -> {
    InspectorTrace.runSpan("NpcManager.update", npcManager::update);
});
```

Kotlin:

```kotlin
InspectorTrace.runSpan("NPCState.update") {
    InspectorTrace.runSpan("NpcManager.update") {
        npcManager.update()
    }
}
```

For return values:

```kotlin
val path = InspectorTrace.callSpan("Pathfinder.findPath") {
    pathfinder.findPath(from, to)
}
```

The browser turns parent/child spans into a live workflow graph.

## Design

The jME render thread snapshots the scene every 250 ms into immutable JSON. The HTTP thread only serves that JSON, so it never walks the Scene Graph concurrently.

```text
jME update thread                 browser
      |                              ^
      | SnapshotBuilder              |
      v                              |
AtomicReference<String> -----> local HTTP server
```

## Next milestones

1. HTTP commands: pause, select, trace-clear, enable/disable AppState.
2. Property editing: transforms and safe primitive fields.
3. Scene diff protocol instead of full snapshots.
4. Search/filter and object pinning.
5. Java Agent + Byte Buddy for automatic method-call instrumentation.
6. IntelliJ tool window that embeds the same UI and attaches to a running JVM.

## Compatibility

The build file targets jMonkeyEngine `3.9.0-stable` and Java 17. The inspector code intentionally sticks to long-lived Scene Graph APIs, so adapting it to nearby jME 3.x versions should be small.
