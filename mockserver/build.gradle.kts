plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("nl.jordy.iptv.mock.MockXtreamServerKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
