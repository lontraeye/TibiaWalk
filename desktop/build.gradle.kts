plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    applicationName = "tibiawalk-desktop"
    mainClass.set("org.tibiawalk.desktop.DesktopApp")
}

// Um único jar executável (duplo clique ou java -jar desktop/build/libs/tibiawalk-desktop.jar)
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Gera um jar com todas as dependências."
    archiveFileName.set("tibiawalk-desktop.jar")
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Depuração: ./gradlew :desktop:snapshot -Passets=... -Pactions=...
tasks.register<JavaExec>("snapshot") {
    group = "tibiawalk"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.tibiawalk.desktop.WindowSnapshot")
    val assets = providers.gradleProperty("assets").orElse(providers.environmentVariable("TIBIA_ASSETS"))
    // -Pactions="click:Aleatório|wait:2000|shot:janela.png" (ações separadas por |)
    args(listOf(assets.getOrElse("")) + providers.gradleProperty("actions").getOrElse("shot:janela.png").split("|"))
}
