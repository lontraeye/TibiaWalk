package org.tibiawalk.core.metadata;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Nomes e categorias dos looktypes (player, montaria, criatura).
 *
 * <p>O cliente não tem essa informação; ela vem do metadata.json embutido no jar,
 * gerado pela task {@code updateMetadata} a partir do Canary e do staticdata do cliente.
 */
public final class Metadata {

    public static final String RESOURCE = "/org/tibiawalk/core/metadata.json";

    private static volatile Metadata bundled;

    private final Map<Integer, LooktypeMeta> byLooktype;
    private final JsonObject source;

    Metadata(Collection<LooktypeMeta> entries, JsonObject source) {
        Map<Integer, LooktypeMeta> map = new TreeMap<>();
        for (LooktypeMeta entry : entries) {
            map.put(entry.looktype(), entry);
        }
        this.byLooktype = map;
        this.source = source;
    }

    /** O metadata.json embutido; vazio se o recurso não existir. */
    public static Metadata bundled() {
        Metadata result = bundled;
        if (result == null) {
            synchronized (Metadata.class) {
                result = bundled;
                if (result == null) {
                    bundled = result = loadBundled();
                }
            }
        }
        return result;
    }

    private static Metadata loadBundled() {
        try (InputStream in = Metadata.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new Metadata(List.of(), new JsonObject());
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Metadata read(Reader reader) {
        Gson gson = MetadataJson.gson();
        JsonObject root = gson.fromJson(reader, JsonObject.class);
        List<LooktypeMeta> entries = gson.fromJson(root.get("looktypes"),
                new TypeToken<List<LooktypeMeta>>() { }.getType());
        JsonObject source = root.has("source") ? root.getAsJsonObject("source") : new JsonObject();
        return new Metadata(entries, source);
    }

    public Optional<LooktypeMeta> get(int looktype) {
        return Optional.ofNullable(byLooktype.get(looktype));
    }

    public Collection<LooktypeMeta> all() {
        return byLooktype.values();
    }

    /** De onde os dados vieram (commit do Canary, data da geração). */
    public JsonObject source() {
        return source;
    }

    /**
     * Resolve um looktype por número ou por nome ("Citizen", "widow queen").
     *
     * @param kind categoria esperada quando é nome; null aceita qualquer uma
     * @param sex  "male"/"female" para desempatar outfits de player; null prefere male
     */
    public Optional<Integer> resolve(String value, LooktypeKind kind, String sex) {
        String v = value.trim();
        if (v.chars().allMatch(Character::isDigit)) {
            return Optional.of(Integer.parseInt(v));
        }
        String wanted = v.toLowerCase(Locale.ROOT);
        String wantedSex = sex == null ? "male" : sex;
        LooktypeMeta fallback = null;
        for (LooktypeMeta meta : byLooktype.values()) {
            if (kind != null && meta.kind() != kind) {
                continue;
            }
            boolean match = meta.name().toLowerCase(Locale.ROOT).equals(wanted)
                    || meta.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(wanted));
            if (!match) {
                continue;
            }
            if (meta.sex() == null || meta.sex().equals(wantedSex)) {
                return Optional.of(meta.looktype());
            }
            fallback = meta;
        }
        return Optional.ofNullable(fallback).map(LooktypeMeta::looktype);
    }
}
