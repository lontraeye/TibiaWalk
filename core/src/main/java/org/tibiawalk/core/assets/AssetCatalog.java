package org.tibiawalk.core.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Índice dos arquivos da pasta assets do cliente, lido do catalog-content.json. */
public final class AssetCatalog {

    public static final String CATALOG_FILE = "catalog-content.json";

    private final Path assetsDir;
    private final Path appearancesFile;
    private final Path staticDataFile;
    private final List<SpriteSheet> sheets;

    private AssetCatalog(Path assetsDir, Path appearancesFile, Path staticDataFile, List<SpriteSheet> sheets) {
        this.assetsDir = assetsDir;
        this.appearancesFile = appearancesFile;
        this.staticDataFile = staticDataFile;
        this.sheets = sheets;
    }

    public static AssetCatalog load(Path assetsDir) throws IOException {
        Path catalogFile = assetsDir.resolve(CATALOG_FILE);
        if (!Files.isRegularFile(catalogFile)) {
            throw new IOException("Não achei " + CATALOG_FILE + " em " + assetsDir
                    + " (a pasta deve ser .../Tibia/packages/Tibia/assets)");
        }

        JsonArray entries;
        try (Reader reader = Files.newBufferedReader(catalogFile)) {
            entries = JsonParser.parseReader(reader).getAsJsonArray();
        }

        Path appearances = null;
        Path staticData = null;
        List<SpriteSheet> sheets = new ArrayList<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String type = entry.get("type").getAsString();
            String file = entry.get("file").getAsString();
            switch (type) {
                case "appearances" -> appearances = assetsDir.resolve(file);
                case "staticdata" -> staticData = assetsDir.resolve(file);
                case "sprite" -> sheets.add(new SpriteSheet(
                        file,
                        entry.get("spritetype").getAsInt(),
                        entry.get("firstspriteid").getAsInt(),
                        entry.get("lastspriteid").getAsInt()));
                default -> { /* mapa, proficiências etc. não interessam aqui */ }
            }
        }

        if (appearances == null) {
            throw new IOException(CATALOG_FILE + " não tem uma entrada do tipo 'appearances'");
        }
        sheets.sort(Comparator.comparingInt(SpriteSheet::firstSpriteId));
        return new AssetCatalog(assetsDir, appearances, staticData, List.copyOf(sheets));
    }

    public Path assetsDir() {
        return assetsDir;
    }

    public Path appearancesFile() {
        return appearancesFile;
    }

    /** Dados do Cyclopedia (monstros, bosses...); null se o cliente não tiver. */
    public Path staticDataFile() {
        return staticDataFile;
    }

    public List<SpriteSheet> sheets() {
        return sheets;
    }

    /** Busca binária da folha que contém o sprite; null se não existir. */
    public SpriteSheet findSheet(int spriteId) {
        int lo = 0;
        int hi = sheets.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            SpriteSheet sheet = sheets.get(mid);
            if (spriteId < sheet.firstSpriteId()) {
                hi = mid - 1;
            } else if (spriteId > sheet.lastSpriteId()) {
                lo = mid + 1;
            } else {
                return sheet;
            }
        }
        return null;
    }
}
