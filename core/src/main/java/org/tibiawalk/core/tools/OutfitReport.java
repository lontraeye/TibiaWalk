package org.tibiawalk.core.tools;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/** Relatório para validar a leitura do appearances.dat. Args: pastaAssets [ids separados por vírgula] */
public final class OutfitReport {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("Informe a pasta assets: -Passets=<pasta> ou TIBIA_ASSETS");
            System.exit(1);
        }

        long start = System.nanoTime();
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        long ms = (System.nanoTime() - start) / 1_000_000;

        Collection<OutfitInfo> outfits = assets.outfits();
        System.out.printf("Assets: %s (%d ms)%n", assets.catalog().assetsDir(), ms);
        System.out.printf("Folhas de sprite: %d%n", assets.catalog().sheets().size());
        System.out.printf("Outfits (looktypes): %d%n", outfits.size());
        System.out.printf("  com 2 addons:        %d%n", count(outfits, o -> o.addonCount() == 2));
        System.out.printf("  montáveis:           %d%n", count(outfits, OutfitInfo::mountable));
        System.out.printf("  coloríveis:          %d%n", count(outfits, OutfitInfo::colorable));
        System.out.printf("  com animação moving: %d%n", count(outfits, o -> o.moving() != null));
        System.out.printf("  contagem de sprites inconsistente: %d%n", count(outfits, OutfitInfo::spriteCountMismatch));

        String ids = args.length > 1 ? args[1] : "";
        if (!ids.isBlank()) {
            System.out.println();
            System.out.println("looktype | dir | addons | montável | colorível | idle | moving (ms por frame)");
            Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .forEach(id -> printOutfit(id, assets.outfit(id)));
        }
    }

    private static void printOutfit(int id, OutfitInfo o) {
        if (o == null) {
            System.out.printf("%8d | não existe no cliente%n", id);
            return;
        }
        System.out.printf("%8d | %3d | %6d | %8s | %9s | %4s | %s%n",
                id, o.directions(), o.addonCount(), yesNo(o.mountable()), yesNo(o.colorable()),
                o.idle() == null ? "-" : o.idle().frames(),
                o.moving() == null ? "-" : o.moving().frames() + " " + Arrays.toString(o.moving().durationsMs()));
    }

    private static long count(Collection<OutfitInfo> outfits, java.util.function.Predicate<OutfitInfo> filter) {
        return outfits.stream().filter(filter).count();
    }

    private static String yesNo(boolean value) {
        return value ? "sim" : "não";
    }
}
