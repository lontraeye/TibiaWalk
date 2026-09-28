package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "list", mixinStandardHelpOptions = true,
        description = "Lista os looktypes do cliente e o que cada um suporta.")
final class ListCommand implements Callable<Integer> {

    @Mixin
    TibiaWalkCli.AssetsOption assets;

    @Option(names = "--mountable", description = "Só outfits que podem montar (na prática, outfits de player).")
    boolean mountable;

    @Option(names = "--with-addons", description = "Só outfits com addons.")
    boolean withAddons;

    @Option(names = "--colorable", description = "Só outfits coloríveis.")
    boolean colorable;

    @Option(names = "--json", description = "Saída em JSON.")
    boolean json;

    @Override
    public Integer call() throws Exception {
        ClientAssets client = assets.open();
        Stream<OutfitInfo> stream = client.outfits().stream();
        if (mountable) {
            stream = stream.filter(OutfitInfo::mountable);
        }
        if (withAddons) {
            stream = stream.filter(o -> o.addonCount() > 0);
        }
        if (colorable) {
            stream = stream.filter(OutfitInfo::colorable);
        }
        List<OutfitInfo> outfits = stream.toList();

        if (json) {
            System.out.println("[");
            for (int i = 0; i < outfits.size(); i++) {
                OutfitInfo o = outfits.get(i);
                System.out.printf("  {\"looktype\": %d, \"addons\": %d, \"mountable\": %b, \"colorable\": %b, "
                                + "\"directions\": %d, \"idleFrames\": %d, \"movingFrames\": %d}%s%n",
                        o.looktype(), o.addonCount(), o.mountable(), o.colorable(), o.directions(),
                        o.idle() == null ? 0 : o.idle().frames(),
                        o.moving() == null ? 0 : o.moving().frames(),
                        i < outfits.size() - 1 ? "," : "");
            }
            System.out.println("]");
            return 0;
        }

        System.out.println("looktype  addons  montável  colorível  frames(idle/moving)");
        for (OutfitInfo o : outfits) {
            System.out.printf("%8d  %6d  %8s  %9s  %d/%d%n", o.looktype(), o.addonCount(),
                    o.mountable() ? "sim" : "-", o.colorable() ? "sim" : "-",
                    o.idle() == null ? 0 : o.idle().frames(),
                    o.moving() == null ? 0 : o.moving().frames());
        }
        System.out.printf("%d looktypes%n", outfits.size());
        return 0;
    }
}
