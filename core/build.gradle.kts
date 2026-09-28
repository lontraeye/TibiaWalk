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
    implementation(libs.xz)
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

// Roda uma ferramenta de org.tibiawalk.core.tools: -Ptool=SpriteDump -Pargs="a b c"
tasks.register<JavaExec>("tool") {
    group = "tibiawalk"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(providers.gradleProperty("tool").map { "org.tibiawalk.core.tools.$it" })
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(providers.gradleProperty("args").getOrElse("").split(" ").filter { it.isNotBlank() })
}

// Regenera o metadata.json (nomes/categorias dos looktypes) a partir do Canary + staticdata do cliente.
// Ex.: ./gradlew :core:updateMetadata -Passets="C:/.../assets" [-PcanaryRef=main]
tasks.register<JavaExec>("updateMetadata") {
    group = "tibiawalk"
    description = "Junta TibiaWiki, Canary, staticdata e metadata-overrides.json em metadata.json."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.tibiawalk.core.metadata.MetadataBuilder")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val assets = providers.gradleProperty("assets").orElse(providers.environmentVariable("TIBIA_ASSETS"))
    val output = layout.projectDirectory.file("src/main/resources/org/tibiawalk/core/metadata.json").asFile
    val overrides = layout.projectDirectory.file("metadata-overrides.json").asFile
    args(assets.getOrElse(""), output.path, providers.gradleProperty("canaryRef").getOrElse("main"), overrides.path)
}
