package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.AssetCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/** Acha a pasta assets do cliente e lembra a última escolhida. */
final class AssetsLocator {

    private static final String PREF_KEY = "assetsDir";
    private static final Preferences PREFS = Preferences.userNodeForPackage(AssetsLocator.class);

    private AssetsLocator() {
    }

    static Optional<Path> find() {
        List<Path> candidates = new ArrayList<>();
        String saved = PREFS.get(PREF_KEY, null);
        if (saved != null) {
            candidates.add(Path.of(saved));
        }
        String env = System.getenv("TIBIA_ASSETS");
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Tibia", "packages", "Tibia", "assets"));
        }
        return candidates.stream().map(AssetsLocator::normalize).flatMap(Optional::stream).findFirst();
    }

    /**
     * Aceita a própria pasta assets ou uma pasta acima dela (a instalação do launcher,
     * ou .../packages/Tibia), para o usuário não precisar achar a pasta exata.
     */
    static Optional<Path> normalize(Path dir) {
        for (Path candidate : List.of(dir, dir.resolve("assets"), dir.resolve("packages/Tibia/assets"))) {
            if (Files.isRegularFile(candidate.resolve(AssetCatalog.CATALOG_FILE))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static void remember(Path dir) {
        PREFS.put(PREF_KEY, dir.toAbsolutePath().toString());
    }
}
