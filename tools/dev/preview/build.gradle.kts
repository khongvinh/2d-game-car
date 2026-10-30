// Runs the real game code on the JVM with tiny Java2D-backed stand-ins for
// android.graphics / android.media / android.content, to take screenshots,
// export the synthesised music as WAV and simulate the car physics.
plugins { kotlin("jvm") version "2.0.21" }
repositories { maven("https://repo1.maven.org/maven2/"); mavenCentral() }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
sourceSets { main { kotlin.srcDir("src/main/kotlin"); kotlin.srcDir("../../../app/src/main/java"); kotlin.exclude("**/GameView.kt", "**/MainActivity.kt") } }
val outDir = project.findProperty("out") ?: "${rootDir}/../out/shots"
tasks.register<JavaExec>("shots") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.ShotsKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("sim") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.SimRunKt") }
tasks.register<JavaExec>("ram") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.RamRunKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("boss") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.BossRunKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("themes") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.ThemeRunKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("garage") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.GarageRunKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("roads") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.RoadRunKt"); jvmArgs("-Djava.awt.headless=true"); args(outDir) }
tasks.register<JavaExec>("drive") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.DriveRunKt"); jvmArgs("-Djava.awt.headless=true"); args((project.findProperty("st") ?: "").toString().split(" ").filter { it.isNotEmpty() }) }
tasks.register<JavaExec>("walk") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.WalkTestKt"); jvmArgs("-Djava.awt.headless=true") }
tasks.register<JavaExec>("inside") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.InsideTestKt"); jvmArgs("-Djava.awt.headless=true") }
tasks.register<JavaExec>("rooms") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.RoomsTestKt"); jvmArgs("-Djava.awt.headless=true"); args((project.findProperty("a") ?: "").toString().split(" ").filter { it.isNotEmpty() }) }
tasks.register<JavaExec>("camp") { classpath = sourceSets["main"].runtimeClasspath; mainClass.set("shot.CampTestKt"); jvmArgs("-Djava.awt.headless=true") }
