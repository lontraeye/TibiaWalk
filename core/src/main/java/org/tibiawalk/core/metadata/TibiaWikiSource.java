package org.tibiawalk.core.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê outfits e montarias do TibiaWiki (tibia.fandom.com) pelas infoboxes das páginas:
 * {@code Infobox Outfit} tem {@code male_id}/{@code female_id} e {@code Infobox Mount} tem {@code mount_id}.
 *
 * <p>Conteúdo do TibiaWiki sob CC BY-SA; aqui só usamos nomes e IDs.
 */
final class TibiaWikiSource {

    static final String API = "https://tibia.fandom.com/api.php";
    private static final String USER_AGENT = "TibiaWalk/0.1 (+https://github.com/lontraeye/TibiaWalk)";
    private static final int BATCH = 50;
    private static final Pattern FIELD = Pattern.compile("^\\|\\s*([a-z_]+)\\s*=\\s*(.*?)\\s*$", Pattern.MULTILINE);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    record Outfit(String name, Integer male, Integer female, boolean premium) {
    }

    record Mount(String name, int looktype) {
    }

    private final HttpClient http;

    TibiaWikiSource(HttpClient http) {
        this.http = http;
    }

    List<Outfit> outfits() throws IOException, InterruptedException {
        List<Outfit> result = new ArrayList<>();
        for (Map<String, String> fields : infoboxes("Template:Infobox_Outfit")) {
            Integer male = number(fields.get("male_id"));
            Integer female = number(fields.get("female_id"));
            String name = fields.get("name");
            if (name != null && !name.isBlank() && (male != null || female != null)) {
                result.add(new Outfit(clean(name), male, female, "yes".equalsIgnoreCase(fields.get("premium"))));
            }
        }
        return result;
    }

    List<Mount> mounts() throws IOException, InterruptedException {
        List<Mount> result = new ArrayList<>();
        for (Map<String, String> fields : infoboxes("Template:Infobox_Mount")) {
            Integer id = number(fields.get("mount_id"));
            String name = fields.get("name");
            if (name != null && !name.isBlank() && id != null) {
                result.add(new Mount(clean(name), id));
            }
        }
        return result;
    }

    /** Campos da infobox de cada página que usa o template. */
    private List<Map<String, String>> infoboxes(String template) throws IOException, InterruptedException {
        List<String> titles = pagesUsing(template);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < titles.size(); i += BATCH) {
            List<String> batch = titles.subList(i, Math.min(titles.size(), i + BATCH));
            JsonObject pages = get(Map.of(
                    "action", "query", "prop", "revisions", "rvprop", "content", "rvslots", "main",
                    "titles", String.join("|", batch), "format", "json"))
                    .getAsJsonObject("query").getAsJsonObject("pages");
            for (Map.Entry<String, JsonElement> page : pages.entrySet()) {
                JsonArray revisions = page.getValue().getAsJsonObject().getAsJsonArray("revisions");
                if (revisions == null || revisions.isEmpty()) {
                    continue;
                }
                String text = revisions.get(0).getAsJsonObject().getAsJsonObject("slots")
                        .getAsJsonObject("main").get("*").getAsString();
                Map<String, String> fields = new LinkedHashMap<>();
                Matcher m = FIELD.matcher(text);
                while (m.find()) {
                    fields.putIfAbsent(m.group(1), m.group(2));
                }
                result.add(fields);
            }
        }
        return result;
    }

    private List<String> pagesUsing(String template) throws IOException, InterruptedException {
        List<String> titles = new ArrayList<>();
        String cont = null;
        do {
            Map<String, String> params = new LinkedHashMap<>(Map.of(
                    "action", "query", "list", "embeddedin", "eititle", template,
                    "einamespace", "0", "eilimit", "500", "format", "json"));
            if (cont != null) {
                params.put("eicontinue", cont);
            }
            JsonObject body = get(params);
            for (JsonElement page : body.getAsJsonObject("query").getAsJsonArray("embeddedin")) {
                titles.add(page.getAsJsonObject().get("title").getAsString());
            }
            cont = body.has("continue") ? body.getAsJsonObject("continue").get("eicontinue").getAsString() : null;
        } while (cont != null);
        return titles;
    }

    private JsonObject get(Map<String, String> params) throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            query.append(query.isEmpty() ? "?" : "&")
                    .append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + query))
                .header("User-Agent", USER_AGENT).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("TibiaWiki respondeu HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static Integer number(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(value);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    /** Remove marcação de wiki simples do nome ([[links]], ''itálico''). */
    private static String clean(String name) {
        return name.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]", "$1").replace("''", "").trim();
    }
}
