package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.util.Arrays;
import java.util.concurrent.Callable;

@Command(name = "info", mixinStandardHelpOptions = true, description = "Mostra os detalhes de um looktype.")
final class InfoCommand implements Callable<Integer> {

    @Mixin
    TibiaWalkCli.AssetsOption assets;

    @Parameters(index = "0", description = "Looktype.")
    int looktype;

    @Override
    public Integer call() throws Exception {
        ClientAssets client = assets.open();
        OutfitInfo o = client.outfit(looktype);
        if (o == null) {
            System.err.println("Looktype " + looktype + " não existe no cliente.");
            return 1;
        }
        System.out.println("looktype:   " + o.looktype());
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
}
