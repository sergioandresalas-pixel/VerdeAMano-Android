plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
// Android and backend are independent builds; their outputs must not overwrite each other.
layout.buildDirectory.set(layout.projectDirectory.dir("build/${rootProject.name}"))
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
