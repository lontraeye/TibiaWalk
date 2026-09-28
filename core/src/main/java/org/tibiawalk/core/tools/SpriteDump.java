package org.tibiawalk.core.tools;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.sprites.SpriteStore;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Depuração: salva a folha e os sprites idle de um looktype. Args: pastaAssets looktype pastaSaida */
public final class SpriteDump {

    public static void main(String[] args) throws Exception {
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        OutfitInfo outfit = assets.outfit(Integer.parseInt(args[1]));
        Path out = Files.createDirectories(Path.of(args[2]));
        SpriteStore store = new SpriteStore(assets.catalog());

        var info = outfit.idle().group().getSpriteInfo();
        System.out.println("sprite ids idle: " + info.getSpriteIdList());
        System.out.println("shift: " + assets.appearance(outfit.looktype()).getFlags().getShift());

        int first = info.getSpriteId(0);
        ImageIO.write(store.sheet(assets.catalog().findSheet(first)), "png", out.resolve("sheet.png").toFile());
        for (int i = 0; i < info.getSpriteIdCount(); i++) {
            BufferedImage sprite = store.sprite(info.getSpriteId(i));
            ImageIO.write(sprite, "png", out.resolve("idle_" + i + ".png").toFile());
        }
        System.out.println("salvo em " + out.toAbsolutePath());
    }
}
