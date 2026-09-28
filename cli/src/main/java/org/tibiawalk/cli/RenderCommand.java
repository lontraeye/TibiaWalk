package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.metadata.GameCharacter;
import org.tibiawalk.core.metadata.LooktypeKind;
import org.tibiawalk.core.metadata.LooktypeMeta;
import org.tibiawalk.core.metadata.Metadata;
import org.tibiawalk.core.render.AnimationType;
import org.tibiawalk.core.render.Direction;
import org.tibiawalk.core.render.GifWriter;
import org.tibiawalk.core.render.OutfitRenderer;
import org.tibiawalk.core.render.OutfitRequest;
import org.tibiawalk.core.render.TibiaColor;
import picocli.CommandLine.ArgGroup;
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

    /** O que renderizar: um looktype, ou um NPC/monstro com o outfit completo dele. */
    static final class Target {
        @Option(names = {"-l", "--looktype"},
                description = "Looktype do outfit: número ou nome (ex.: 128, Citizen, \"orc warlord\").")
        String looktype;

        @Option(names = "--npc", description = "NPC pelo nome (ex.: \"A Bearded Woman\"), com as cores e addons dele.")
        String npc;

        @Option(names = "--monster", description = "Monstro ou boss pelo nome (ex.: Demon, \"Black Knight\").")
        String monster;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    Target target;

    @Option(names = "--female", description = "Com nome de outfit de player, usa a versão feminina.")
    boolean female;

    // Sem valor padrão: o que não for informado vem do NPC/monstro, ou do padrão (branco, sem addons).
    @Option(names = {"-a", "--addons"},
            description = "Addons: 0 = nenhum, 1 = primeiro, 2 = segundo, 3 = ambos. Padrão: 0 ou o do personagem.")
    Integer addons;

    @Option(names = "--head", description = "Cor da cabeça: índice da paleta (0-132) ou hex. Padrão: ffffff "
            + "ou a do personagem.")
    TibiaColor head;

    @Option(names = "--body", description = "Cor do corpo.")
    TibiaColor body;

    @Option(names = "--legs", description = "Cor das pernas.")
    TibiaColor legs;

    @Option(names = "--feet", description = "Cor dos pés.")
    TibiaColor feet;

    @Option(names = {"-m", "--mount"},
            description = "Montaria: looktype ou nome (ex.: 368, \"Widow Queen\"). 0 = sem.")
    String mountArg;

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
        Metadata metadata = Metadata.bundled();
        preset = null;
        if (target.npc != null) {
            preset = metadata.character(target.npc, GameCharacter.Kind.NPC)
                    .orElseThrow(() -> new IllegalArgumentException("NPC desconhecido: " + target.npc));
        } else if (target.monster != null) {
            preset = metadata.character(target.monster, GameCharacter.Kind.MONSTER)
                    .or(() -> metadata.character(target.monster, GameCharacter.Kind.BOSS))
                    .orElseThrow(() -> new IllegalArgumentException("Monstro desconhecido: " + target.monster));
        }
        if (preset != null) {
            System.out.println(preset.name() + " (" + preset.kind().name().toLowerCase() + "): looktype "
                    + preset.looktype());
        }

        int looktype = preset != null ? preset.looktype()
                : metadata.resolve(target.looktype, null, female ? "female" : "male")
                .orElseThrow(() -> new IllegalArgumentException("Outfit desconhecido: " + target.looktype));
        int mount = mountArg != null
                ? metadata.resolve(mountArg, LooktypeKind.MOUNT, null)
                .orElseThrow(() -> new IllegalArgumentException("Montaria desconhecida: " + mountArg))
                : preset != null ? preset.mount() : 0;
        if (mount > 0 && metadata.get(mount).map(LooktypeMeta::kind).orElse(null) != LooktypeKind.MOUNT) {
            System.err.println("Aviso: o looktype " + mount + " não está na lista de montarias.");
        }

        ClientAssets client = assets.open();
        if (mount > 0 && client.outfit(looktype) != null && !client.outfit(looktype).mountable()) {
            System.err.println("Aviso: o looktype " + looktype + " não tem versão montada; a montaria foi ignorada.");
        }
        OutfitRenderer renderer = new OutfitRenderer(client);
        OutfitRequest request = OutfitRequest.of(looktype)
                .withAddons(addons != null ? addons : preset != null ? preset.addons() : 0)
                .withColors(color(head, preset == null ? -1 : preset.head()),
                        color(body, preset == null ? -1 : preset.body()),
                        color(legs, preset == null ? -1 : preset.legs()),
                        color(feet, preset == null ? -1 : preset.feet()))
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

    private GameCharacter preset;

    /** A cor informada; senão a do personagem (índice da paleta); senão branco. */
    private static TibiaColor color(TibiaColor explicit, int presetIndex) {
        if (explicit != null) {
            return explicit;
        }
        return presetIndex >= 0 ? TibiaColor.palette(presetIndex) : TibiaColor.WHITE;
    }

    private String defaultName(OutfitRequest r) {
        StringBuilder name = new StringBuilder();
        if (preset != null) {
            name.append(preset.name().toLowerCase().replaceAll("[^a-z0-9]+", "_")).append('_');
        }
        name.append(r.looktype()).append("_a").append(r.addons());
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
