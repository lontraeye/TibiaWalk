package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.metadata.LooktypeMeta;
import org.tibiawalk.core.metadata.Metadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.util.Arrays;
import java.util.concurrent.Callable;

@Command(name = "info", mixinStandardHelpOptions = true, description = "Mostra os detalhes de um looktype.")
final class InfoCommand implements Callable<Integer> {

    @Mixin
    TibiaWalkCli.AssetsOption assets;

    @Parameters(index = "0", description = "Looktype: número ou nome.")
    String looktypeArg;

    @Override
    public Integer call() throws Exception {
        Metadata metadata = Metadata.bundled();
        int looktype = metadata.resolve(looktypeArg, null, null)
                .orElseThrow(() -> new IllegalArgumentException("Looktype desconhecido: " + looktypeArg));
        ClientAssets client = assets.open();
        OutfitInfo o = client.outfit(looktype);
        if (o == null) {
            System.err.println("Looktype " + looktype + " não existe no cliente.");
            return 1;
        }
        System.out.println("looktype:   " + o.looktype());
        metadata.get(looktype).ifPresentOrElse(meta -> printMeta(meta), () -> System.out.println("tipo:       desconhecido"));
        System.out.println("direções:   " + o.directions());
        System.out.println("addons:     " + o.addonCount());
        System.out.println("montável:   " + (o.mountable() ? "sim" : "não"));
        System.out.println("colorível:  " + (o.colorable() ? "sim" : "não"));
        if (o.idle() != null) {
            System.out.println("idle:       " + o.idle().frames() + " frame(s) " + Arrays.toString(o.idle().durationsMs()));
        }
        if (o.moving() != null) {
            System.out.println("moving:     " + o.moving().frames() + " frame(s) " + Arrays.toString(o.moving().durationsMs()));
        }
        return 0;
    }

    private static void printMeta(LooktypeMeta meta) {
        System.out.println("tipo:       " + meta.kind().name().toLowerCase());
        System.out.println("nome:       " + meta.name());
        if (meta.sex() != null) {
            System.out.println("sexo:       " + meta.sex());
        }
        if (meta.mountId() != null) {
            System.out.println("mount id:   " + meta.mountId());
        }
        if (!meta.aliases().isEmpty()) {
            System.out.println("também:     " + String.join(", ", meta.aliases()));
        }
    }
}
