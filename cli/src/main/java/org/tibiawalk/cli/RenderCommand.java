package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.render.AnimationType;
import org.tibiawalk.core.render.Direction;
import org.tibiawalk.core.render.GifWriter;
import org.tibiawalk.core.render.OutfitRenderer;
import org.tibiawalk.core.render.OutfitRequest;
import org.tibiawalk.core.render.TibiaColor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import javax.imageio.ImageIO;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "render", mixinStandardHelpOptions = true,
        description = "Renderiza um outfit como GIF animado ou PNG.")
final class RenderCommand implements Callable<Integer> {

    @Mixin
    TibiaWalkCli.AssetsOption assets;

    @Option(names = {"-l", "--looktype"}, required = true, description = "Looktype do outfit.")
    int looktype;

    @Option(names = {"-a", "--addons"}, defaultValue = "0",
            description = "Addons: 0 = nenhum, 1 = primeiro, 2 = segundo, 3 = ambos. Padrão: ${DEFAULT-VALUE}.")
    int addons;

    @Option(names = "--head", defaultValue = "ffffff", description = "Cor da cabeça: índice da paleta (0-132) ou hex.")
    TibiaColor head;

    @Option(names = "--body", defaultValue = "ffffff", description = "Cor do corpo.")
    TibiaColor body;

    @Option(names = "--legs", defaultValue = "ffffff", description = "Cor das pernas.")
    TibiaColor legs;

    @Option(names = "--feet", defaultValue = "ffffff", description = "Cor dos pés.")
    TibiaColor feet;

    @Option(names = {"-m", "--mount"}, defaultValue = "0", description = "Looktype da montaria (0 = sem).")
    int mount;

    @Option(names = {"-d", "--direction"}, defaultValue = "south",
            description = "north, east, south ou west. Padrão: ${DEFAULT-VALUE}.")
    Direction direction;

    @Option(names = "--idle", description = "Animação parada em vez de andando.")
    boolean idle;

    @Option(names = "--frame-ms", defaultValue = "100",
            description = "Duração de cada frame em ms (0 = a do cliente). Padrão: ${DEFAULT-VALUE}.")
    int frameMs;

    @Option(names = {"-o", "--output"},
            description = "Arquivo de saída (.gif ou .png). Padrão: generated/<looktype>_....gif")
    Path output;

    @Override
    public Integer call() throws Exception {
        ClientAssets client = assets.open();
        OutfitRenderer renderer = new OutfitRenderer(client);
        OutfitRequest request = OutfitRequest.of(looktype)
                .withAddons(addons)
                .withColors(head, body, legs, feet)
                .withMount(mount)
                .withDirection(direction);

        Path out = output != null ? output : Path.of("generated", defaultName(request));
        if (out.toAbsolutePath().getParent() != null) {
            Files.createDirectories(out.toAbsolutePath().getParent());
        }

        AnimationType type = idle ? AnimationType.IDLE : AnimationType.MOVING;
        List<OutfitRenderer.Frame> frames = renderer.frames(request, type, frameMs);
        if (out.getFileName().toString().toLowerCase().endsWith(".png")) {
            ImageIO.write(frames.get(0).image(), "png", out.toFile());
        } else {
            try (OutputStream stream = Files.newOutputStream(out)) {
                GifWriter.write(frames, stream);
            }
        }
        System.out.printf("%s (%d frames, %dx%d)%n", out.toAbsolutePath(), frames.size(),
                frames.get(0).image().getWidth(), frames.get(0).image().getHeight());
        return 0;
    }

    private String defaultName(OutfitRequest r) {
        StringBuilder name = new StringBuilder().append(r.looktype()).append("_a").append(r.addons());
        for (TibiaColor c : List.of(r.head(), r.body(), r.legs(), r.feet())) {
            name.append('_').append(c.toHex());
        }
        if (r.mount() > 0) {
            name.append("_m").append(r.mount());
        }
        name.append('_').append(r.direction().name().toLowerCase());
        return name.append(idle ? "_idle" : "").append(".gif").toString();
    }
}
