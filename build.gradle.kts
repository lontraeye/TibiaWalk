// Atualização completa depois de um patch do Tibia (o launcher já atualizou a pasta assets):
//   ./gradlew updateAll -Passets="C:/.../Tibia/packages/Tibia/assets"   (ou TIBIA_ASSETS)
// Regenera nomes/categorias (TibiaWiki, Canary, staticdata, heurísticas, overrides) e os três jars.
tasks.register("updateAll") {
    group = "tibiawalk"
    description = "Regenera o metadata.json e gera os jars da CLI, do desktop e do web."
    dependsOn(":core:updateMetadata", ":cli:fatJar", ":desktop:fatJar", ":web:fatJar")
    doLast {
        println()
        println("Pronto. Jars atualizados:")
        listOf("cli/build/libs/tibiawalk.jar", "desktop/build/libs/tibiawalk-desktop.jar",
            "web/build/libs/tibiawalk-web.jar").forEach { println("  $it") }
        println("Revise as mudanças acima e commite core/src/main/resources/org/tibiawalk/core/metadata.json.")
    }
}
