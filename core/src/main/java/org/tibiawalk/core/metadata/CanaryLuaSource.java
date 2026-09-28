package org.tibiawalk.core.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monstros e NPCs do Canary: um arquivo Lua por criatura, com o nome e o outfit.
 *
 * <pre>
 * local mType = Game.createMonsterType("Azure Frog")      local internalNpcName = "A Bearded Woman"
 * monster.outfit = { lookType = 226, ... }                npcConfig.outfit = { lookType = 140, ... }
 * </pre>
 */
final class CanaryLuaSource {

    enum Type { MONSTER, NPC }

    record Creature(Type type, String name, int looktype) {
    }

    private static final Pattern MONSTER_NAME = Pattern.compile("createMonsterType\\(\\s*\"([^\"]+)\"");
    private static final Pattern NPC_NAME = Pattern.compile(
            "internalNpcName\\s*=\\s*\"([^\"]+)\"|createNpcType\\(\\s*\"([^\"]+)\"");
    /** O primeiro bloco outfit; lookType dentro dele (lookTypeEx é item, não interessa). */
    private static final Pattern OUTFIT = Pattern.compile("\\.outfit\\s*=\\s*\\{([^}]*)}", Pattern.DOTALL);
    private static final Pattern LOOKTYPE = Pattern.compile("\\blookType\\s*=\\s*(\\d+)");
    private static final int PARALLEL = 16;

    private final HttpClient http;
    private final String repo;

    CanaryLuaSource(HttpClient http, String repo) {
        this.http = http;
        this.repo = repo;
    }

    List<Creature> creatures(String commit) throws IOException, InterruptedException {
        List<String> paths = luaFiles(commit);
        System.out.println("Canary: lendo " + paths.size() + " arquivos de monstros e NPCs…");

        Semaphore limit = new Semaphore(PARALLEL);
        List<CompletableFuture<Creature>> futures = new ArrayList<>();
        for (String path : paths) {
            limit.acquire();
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://raw.githubusercontent.com/" + repo + "/" + commit + "/" + path)).build();
            futures.add(http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((r, e) -> limit.release())
                    .thenApply(r -> r.statusCode() == 200 ? parse(path, r.body()) : null)
                    .exceptionally(e -> null));
        }

        List<Creature> result = new ArrayList<>();
        int failed = 0;
        for (CompletableFuture<Creature> future : futures) {
            Creature creature = future.join();
            if (creature != null) {
                result.add(creature);
            } else {
                failed++;
            }
        }
        if (failed > 0) {
            System.out.println("Canary: " + failed + " arquivos sem outfit de criatura (ex. armadilhas com lookTypeEx) "
                    + "ou com falha no download, ignorados");
        }
        return result;
    }

    /** Todos os .lua dentro de pastas monster/ ou npc/, via a árvore do commit (uma chamada só). */
    private List<String> luaFiles(String commit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://api.github.com/repos/" + repo + "/git/trees/" + commit + "?recursive=1"))
                .header("Accept", "application/vnd.github+json").build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondeu HTTP " + response.statusCode() + " ao listar a árvore do Canary");
        }
        JsonObject tree = JsonParser.parseString(response.body()).getAsJsonObject();
        if (tree.has("truncated") && tree.get("truncated").getAsBoolean()) {
            System.out.println("Aviso: a árvore do Canary veio truncada; alguns arquivos podem faltar");
        }
        List<String> paths = new ArrayList<>();
        for (JsonElement element : tree.getAsJsonArray("tree")) {
            String path = element.getAsJsonObject().get("path").getAsString();
            // scripts/spells/monster/ são feitiços, não definições de criatura
            if (path.endsWith(".lua") && (path.contains("/monster/") || path.contains("/npc/"))
                    && !path.contains("/scripts/")) {
                paths.add(path);
            }
        }
        return paths;
    }

    static Creature parse(String path, String lua) {
        boolean npc = path.contains("/npc/");
        Matcher name = (npc ? NPC_NAME : MONSTER_NAME).matcher(lua);
        Matcher outfit = OUTFIT.matcher(lua);
        if (!name.find() || !outfit.find()) {
            return null;
        }
        Matcher looktype = LOOKTYPE.matcher(outfit.group(1));
        if (!looktype.find()) {
            return null;
        }
        int lt = Integer.parseInt(looktype.group(1));
        if (lt <= 0) {
            return null;
        }
        String n = name.group(1) != null ? name.group(1) : name.group(2);
        return new Creature(npc ? Type.NPC : Type.MONSTER, n.trim(), lt);
    }
}
