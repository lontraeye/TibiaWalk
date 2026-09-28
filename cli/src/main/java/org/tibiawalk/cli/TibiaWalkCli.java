package org.tibiawalk.cli;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.render.TibiaColor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;

@Command(
        name = "tibiawalk",
        mixinStandardHelpOptions = true,
        version = "tibiawalk 0.1",
        description = "Gera imagens e GIFs de outfits do Tibia a partir dos assets do cliente oficial.",
        subcommands = {RenderCommand.class, ListCommand.class, InfoCommand.class})
public final class TibiaWalkCli {

    /** Opção comum: pasta assets do cliente. */
    static final class AssetsOption {
        @Option(names = {"-A", "--assets"},
                description = "Pasta assets do cliente (.../Tibia/packages/Tibia/assets). "
                        + "Padrão: variável de ambiente TIBIA_ASSETS.")
        Path assets;

        ClientAssets open() throws IOException {
            Path dir = assets;
            if (dir == null) {
                String env = System.getenv("TIBIA_ASSETS");
                if (env == null || env.isBlank()) {
                    throw new CommandLine.ParameterException(new CommandLine(new TibiaWalkCli()),
                            "Informe --assets ou defina TIBIA_ASSETS");
                }
                dir = Path.of(env);
            }
            return ClientAssets.open(dir);
        }
    }

    static final class ColorConverter implements CommandLine.ITypeConverter<TibiaColor> {
        @Override
        public TibiaColor convert(String value) {
            return TibiaColor.parse(value);
        }
    }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new TibiaWalkCli());
        cli.registerConverter(TibiaColor.class, new ColorConverter());
        cli.setCaseInsensitiveEnumValuesAllowed(true);
        cli.setExecutionExceptionHandler((e, commandLine, parseResult) -> {
            commandLine.getErr().println("Erro: " + e.getMessage());
            return 1;
        });
        System.exit(cli.execute(args));
    }
}
