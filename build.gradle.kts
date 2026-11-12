plugins {
    id("java")
}

group = "com.folumo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
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
        manifest { attributes("Main-Class" to main) }
    }
    tasks.assemble { dependsOn(jar) }
}
