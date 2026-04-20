# Jmix Minesweeper

Composite [Jmix](https://www.jmix.io/) workspace: a **Minesweeper Flow UI add-on** and a **demo application** that uses it. The root `settings.gradle` wires both with `includeBuild`, so Gradle resolves the demo’s dependency against your local add-on sources.

## Two parts

| Part | Directory | Role |
|------|-----------|------|
| **Add-on** | `minesweeper-addon/` | Reusable library: Flow UI game view, starter, auto-configuration. Publish to Maven Central (Sonatype) or depend on it from another Jmix app. |
| **Demo app** | `demo/` | Minimal Jmix **2.8.x** web app that pulls in `minesweeper-starter` so you can run and try the game without integrating into your own project first. |

## Using the add-on in your application

1. Add the **starter** to your app’s `dependencies` (same Jmix BOM line as the add-on, currently **2.8.x**):

   ```gradle
   implementation 'io.github.digitilius.minesweeper:minesweeper-starter:<version>'
   ```

2. If you consume a published artifact from Maven Central, add **`mavenCentral()`** (see the add-on README).

3. In this repository, opening the **composite root** is enough: the included `minesweeper-addon` build satisfies the demo’s `minesweeper-starter` dependency without publishing first.

The add-on registers the standard view and menu entry. Optional `@Route` wiring, `application.properties` keys, and publishing details are documented in [`minesweeper-addon/README.md`](minesweeper-addon/README.md).

## Using the demo app

1. Open the **repository root** in IntelliJ IDEA (Jmix composite project) so both `demo` and `minesweeper-addon` are on the classpath.
2. Run the **Demo Jmix Application** configuration, **or** from a shell:

   ```bash
   cd demo
   ./gradlew bootRun
   ```

3. Open [http://localhost:8080](http://localhost:8080) and sign in with **admin** / **admin** (defaults from the generated demo).

Use the demo as a reference for view descriptors, message bundles, and any custom route class next to your own package layout.

## Composite project note

This root project only aggregates included builds (`includeBuild` in `settings.gradle`). To add more apps or add-ons, use *New > Subproject* in the Jmix tool window or add further `includeBuild` entries. See [Composite projects](https://docs.jmix.io/jmix/2.8/studio/composite-projects.html) in the Jmix documentation.
