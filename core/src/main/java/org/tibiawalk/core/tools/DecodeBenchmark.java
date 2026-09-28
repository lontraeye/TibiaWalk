package org.tibiawalk.core.tools;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.SpriteSheet;
import org.tibiawalk.core.sprites.SpriteSheetDecoder;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

/** Depuração: tempo médio para decodificar folhas de sprite e um checksum do conteúdo. Args: pastaAssets [n] */
public final class DecodeBenchmark {

    public static void main(String[] args) throws Exception {
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        List<SpriteSheet> sheets = assets.catalog().sheets();
        CRC32 crc = new CRC32();
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            SpriteSheet sheet = sheets.get((int) ((long) i * sheets.size() / n));
            BufferedImage image = SpriteSheetDecoder.decode(assets.catalog().assetsDir().resolve(sheet.file()));
            for (int p : image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) {
                crc.update(p);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%d folhas em %d ms (%.1f ms/folha), crc=%08x%n", n, ms, (double) ms / n, crc.getValue());
    }
}
