// Compiles the game's Kotlin sources against android.jar (no Android SDK / AGP needed).
plugins { kotlin("jvm") version "2.0.21" }
repositories { maven("https://repo1.maven.org/maven2/"); mavenCentral() }
dependencies { compileOnly(files("../.cache/android.jar")) }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
sourceSets { main { kotlin.srcDir("../../../app/src/main/java") } }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
