plugins {
    id("java")
}

group = "com.folumo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// JavaFX is only used to play the ending video inside the Swing window.
val fxVersion = "25.0.5"
val fxPlatform = System.getProperty("os.name").lowercase().let { os ->
    when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
}

dependencies {
    listOf("base", "graphics", "swing", "media").forEach { implementation("org.openjfx:javafx-$it:$fxVersion:$fxPlatform") }
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Two apps from one codebase: gradlew runEditor / runGame, and editorJar / gameJar for runnable jars.
val apps = mapOf("Editor" to "com.folumo.editor.EditorApp", "Game" to "com.folumo.game.GameApp")
apps.forEach { (name, main) ->
    tasks.register<JavaExec>("run$name") {
        group = "application"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(main)
        workingDir = rootDir
    }
    val jar = tasks.register<Jar>("${name.lowercase()}Jar") {
        group = "build"
        archiveFileName.set("${rootProject.name}-${name.lowercase()}.jar")
        from(sourceSets["main"].output)
        // bundle JavaFX so `java -jar` works on its own (jars are built for the OS they're built on)
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/*.SF", "META-INF/*.RSA")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes("Main-Class" to main) }
    }
    tasks.assemble { dependsOn(jar) }
}
