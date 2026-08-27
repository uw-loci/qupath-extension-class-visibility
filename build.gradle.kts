plugins {
    // To optionally create a shadow/fat jar that bundles up dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

// Configure your extension here
qupathExtension {
    name = "qupath-extension-class-visibility"
    group = "io.github.michaelsnelson"
    version = "0.1.0"
    description = "Show or hide QuPath objects by class or by class component, from an undockable analysis-pane tab."
    automaticModule = "io.github.michaelsnelson.extension.classvisibility"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val javafxVersion = "17.0.2"

dependencies {
    // Main dependencies for QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    // Required from Gradle 9: the launcher is no longer added to the test
    // runtime classpath automatically, and its absence reports as
    // "Failed to load JUnit Platform" rather than a missing dependency.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation(libs.bundles.logging)
    testImplementation(libs.qupath.fxtras)
    testImplementation("org.openjfx:javafx-base:$javafxVersion")
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion")
    testImplementation("org.openjfx:javafx-controls:$javafxVersion")
}

// For troubleshooting deprecation warnings
tasks.withType<JavaCompile> {
    options.release.set(21) // QuPath 0.7 runs on Java 21; pin bytecode target so any build JDK emits loadable classes
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.test {
    useJUnitPlatform()
    // Most tests (ClassHarvester, ClassCensus, VisibilityRuleModel) are
    // deliberately JavaFX-free -- qupath-core types only. TWO are exceptions, and
    // neither starts the JavaFX toolkit, which is why both run here with JavaFX on
    // the plain classpath:
    //   ViewerVisibilityContractTest constructs a real OverlayOptions to assert on
    //     isHidden(PathObject), the predicate the painter consults. That touches
    //     javafx.base observable collections and properties.
    //   CloseGuardTest calls the static ClassVisibilityPane.applyCloseGuard, which
    //     LOADS a javafx.scene.layout.BorderPane subclass -- javafx.graphics and
    //     javafx.controls, not just javafx.base. Loading those classes is fine;
    //     INSTANTIATING a Control is not, and would need the toolkit. That is the
    //     line, and it is why the guard is a static method rather than an instance
    //     one: QuPath shutdown has to run it with no panel alive anyway.
    // Do not add --add-modules on the strength of that: these are classpath jars,
    // not modules, and the flag would fail to resolve them. If a future test needs
    // a real toolkit (Stage, Scene, Platform.startup), add:
    //   "--add-modules", "javafx.base,javafx.graphics,javafx.controls",
    //   "--add-opens", "javafx.graphics/javafx.stage=ALL-UNNAMED"
    // and configure the openjfx Gradle plugin so the modules land on the
    // module path rather than the classpath.
}
// QuPath 0.7.0's maven artifacts are published as requiring JVM 25 (org.gradle.jvm.version=25),
// even though the QuPath app runs on Java 21. options.release=21 makes Gradle resolve a
// JVM-21-compatible classpath, which then rejects those JVM-25 artifacts on a clean build. Force
// the resolvable classpaths to request JVM 25 so the deps resolve; bytecode target (21) is
// unaffected, so the jar still loads on Java 21. (Upstream QuPath metadata bug; remove if fixed.)
configurations.configureEach {
    if (isCanBeResolved) {
        attributes {
            attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
        }
    }
}
