package org.tibiawalk.core.assets;

import org.tibiawalk.core.proto.AppearancesProto.Appearance;
import org.tibiawalk.core.proto.AppearancesProto.Appearances;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Ponto de entrada: abre a pasta assets de um cliente Tibia 12+ e indexa os outfits. */
public final class ClientAssets {

    private final AssetCatalog catalog;
    private final Map<Integer, OutfitInfo> outfits;

    private ClientAssets(AssetCatalog catalog, Map<Integer, OutfitInfo> outfits) {
        this.catalog = catalog;
        this.outfits = outfits;
    }

    public static ClientAssets open(Path assetsDir) throws IOException {
        AssetCatalog catalog = AssetCatalog.load(assetsDir);

        Appearances appearances;
        try (InputStream in = Files.newInputStream(catalog.appearancesFile())) {
            appearances = Appearances.parseFrom(in);
        }

        Map<Integer, OutfitInfo> outfits = new TreeMap<>();
        for (Appearance outfit : appearances.getOutfitList()) {
            outfits.put(outfit.getId(), OutfitInfo.from(outfit));
        }
        return new ClientAssets(catalog, Collections.unmodifiableMap(outfits));
    }

    public AssetCatalog catalog() {
        return catalog;
    }

    /** O outfit do looktype, ou null se o cliente não tiver esse looktype. */
    public OutfitInfo outfit(int looktype) {
        return outfits.get(looktype);
    }

    public Collection<OutfitInfo> outfits() {
        return outfits.values();
    }
}
