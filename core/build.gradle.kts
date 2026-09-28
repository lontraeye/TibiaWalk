plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.protobuf.java)
    implementation(libs.gson)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}

// Pasta assets do cliente: -Passets=<pasta> ou variável de ambiente TIBIA_ASSETS.
// Ex.: ./gradlew :core:outfitReport -Passets="C:/.../Tibia/packages/Tibia/assets" -Pids=128,130
tasks.register<JavaExec>("outfitReport") {
    group = "tibiawalk"
    description = "Lê os assets do cliente e imprime um relatório dos outfits."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.tibiawalk.core.tools.OutfitReport")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val assets = providers.gradleProperty("assets").orElse(providers.environmentVariable("TIBIA_ASSETS"))
    val ids = providers.gradleProperty("ids").orElse("")
    args(assets.getOrElse(""), ids.get())
}
