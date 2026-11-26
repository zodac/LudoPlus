plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}

// Two JDK 24+ warnings printed by every unit-test JVM, both from libraries' code rather than ours,
// and neither possible on ART, where the app actually runs - so the test JVMs alone allow what they
// do explicitly instead of warning on every run:
// - DataStore's bundled protobuf reads memory through sun.misc.Unsafe (JEP 498) - drop that flag
//   once it stops;
// - Robolectric's native graphics runtime is loaded with System.load (JEP 472).
// Robolectric's SDK 36+ runtimes also set a FileDescriptor's raw fd through jdk.internal.access, which
// java.base doesn't export - without the --add-exports, every test fails before it starts.
// Compact object headers (JEP 519) are on for the test JVMs too: smaller heap, same behaviour.
subprojects {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--sun-misc-unsafe-memory-access=allow",
            "--enable-native-access=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "-XX:+UseCompactObjectHeaders",
        )
        // A failing test's whole message and stack in the log, not just its class and line: CI's log is all there is to go on.
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Every dependency any task here could want - each project's resolvable configurations, AGP's lint and Kotlin's compiler
// among them - downloaded without building anything, by `./gradlew resolveDependencies` (each project's own, as a project
// may only resolve its own). CI's cache writers run it (.github/scripts/resolve_dependencies.sh), so the jobs after them,
// which only read the cache, find everything there rather than each fetching it from Maven Central at once. Lenient: a configuration no single variant answers without a task's own attributes is skipped rather
// than failing the build. The files are resolved as the configuration cache stores the task.
allprojects {
    tasks.register("resolveDependencies") {
        group = "build setup"
        description = "Downloads this project's dependencies, for CI's Gradle cache."
        val dependencies = files({
            configurations.filter { it.isCanBeResolved }.map { configuration ->
                configuration.incoming.artifactView { lenient(true) }.files
            }
        })
        doLast {
            logger.lifecycle("Resolved ${dependencies.files.size} files.")
        }
    }
}
