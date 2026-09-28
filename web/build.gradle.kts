plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.javalin)
    implementation(libs.gson)
    runtimeOnly(libs.slf4j.simple)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    applicationName = "tibiawalk-web"
    mainClass.set("org.tibiawalk.web.WebServer")
    applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// Um único jar executável: java -jar web/build/libs/tibiawalk-web.jar --assets <pasta> [--port 7070]
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Gera um jar com todas as dependências."
    archiveFileName.set("tibiawalk-web.jar")
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
