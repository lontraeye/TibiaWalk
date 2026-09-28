package org.tibiawalk.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.tibiawalk.core.assets.ClientAssets;

import java.nio.file.Path;
import java.util.Map;

/**
 * Servidor HTTP: API de imagens, a página de outfitter e o componente &lt;tibia-outfit&gt;.
 *
 * <p>Args: --assets &lt;pasta&gt; (ou TIBIA_ASSETS) e --port &lt;porta&gt; (ou PORT, padrão 7070).
 */
public final class WebServer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        String assetsArg = option(args, "--assets", System.getenv("TIBIA_ASSETS"));
        if (assetsArg == null || assetsArg.isBlank()) {
            System.err.println("Informe --assets <pasta> ou defina TIBIA_ASSETS");
            System.exit(1);
        }
        int port = Integer.parseInt(option(args, "--port", System.getenv().getOrDefault("PORT", "7070")));

        ClientAssets assets = ClientAssets.open(Path.of(assetsArg));
        OutfitService service = new OutfitService(assets);
        start(service, port);
        System.out.println("TibiaWalk web em http://localhost:" + port + "/");
    }

    static Javalin start(OutfitService service, int port) {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add(files -> {
                files.hostedPath = "/";
                files.directory = "/public";
                files.location = Location.CLASSPATH;
            });
            // As imagens e o componente podem ser usados de qualquer site.
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
        });

        app.get("/api/outfit.gif", ctx -> image(ctx, service, false));
        app.get("/api/outfit.png", ctx -> image(ctx, service, true));
        app.get("/api/looktypes", ctx -> json(ctx, service.looktypes(
                ctx.queryParam("kind"), ctx.queryParam("sex"), ctx.queryParam("q"))));
        app.get("/api/mounts", ctx -> json(ctx, service.looktypes("mount", null, ctx.queryParam("q"))));
        app.get("/api/characters", ctx -> json(ctx, service.characters(ctx.queryParam("kind"), ctx.queryParam("q"))));
        app.get("/api/palette", ctx -> json(ctx, service.palette()));
        app.get("/api/info", ctx -> json(ctx, service.info()));

        app.exception(BadRequestResponse.class, (e, ctx) -> {
            ctx.status(400);
            json(ctx, Map.of("error", e.getMessage()));
        });
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400);
            json(ctx, Map.of("error", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500);
            json(ctx, Map.of("error", "Erro ao renderizar: " + e.getMessage()));
        });

        return app.start(port);
    }

    private static void image(Context ctx, OutfitService service, boolean png) throws Exception {
        OutfitService.Rendered rendered = service.render(ctx, png);
        // Por nome (npc=Rashid, looktype=Citizen) o resultado pode mudar quando o metadata for atualizado;
        // só com números a imagem é fixa para a versão do cliente.
        boolean byName = ctx.queryParam("npc") != null || ctx.queryParam("monster") != null
                || !numeric(ctx.queryParam("looktype")) || !numeric(ctx.queryParam("mount"));
        ctx.header("Cache-Control", byName ? "public, max-age=3600" : "public, max-age=86400");
        ctx.contentType(rendered.contentType());
        ctx.result(rendered.bytes());
    }

    /** Ausente ou só dígitos. */
    private static boolean numeric(String value) {
        return value == null || value.isBlank() || value.trim().chars().allMatch(Character::isDigit);
    }

    private static void json(Context ctx, Object value) {
        ctx.contentType("application/json; charset=utf-8");
        ctx.result(GSON.toJson(value));
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
