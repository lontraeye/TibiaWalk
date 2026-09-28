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

// Pacote de deploy em generated/deploy: jar + install.sh + tibiawalk.service (+ assets com -Passets).
//   ./gradlew :web:deployBundle -Passets="C:/.../Tibia/packages/Tibia/assets"
val assetsDir = providers.gradleProperty("assets").orElse(providers.environmentVariable("TIBIA_ASSETS"))
val deployDir = rootProject.layout.projectDirectory.dir("generated/deploy")

tasks.register<Tar>("assetsTar") {
    group = "tibiawalk"
    description = "Compacta a pasta assets do cliente (sem os tiles de minimapa) em generated/deploy."
    compression = Compression.GZIP
    archiveFileName.set("tibia-assets.tar.gz")
    destinationDirectory.set(deployDir)
    if (assetsDir.isPresent) {
        from(assetsDir.get()) {
            into("assets")
            exclude("minimap-*")
        }
    }
    doFirst {
        require(assetsDir.isPresent) { "Informe -Passets=<pasta assets do cliente> ou TIBIA_ASSETS" }
    }
}

tasks.register<Copy>("deployBundle") {
    group = "tibiawalk"
    description = "Monta generated/deploy para enviar à VM."
    dependsOn("fatJar")
    if (assetsDir.isPresent) {
        dependsOn("assetsTar")
    }
    from(layout.buildDirectory.file("libs/tibiawalk-web.jar"))
    from(rootProject.file("deploy")) {
        include("install.sh", "tibiawalk.service")
    }
    into(deployDir)
    doLast {
        println("Pacote em ${deployDir.asFile}")
    }
}
