package org.tibiawalk.core.tools;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.proto.AppearancesProto.SpriteInfo;
import org.tibiawalk.core.sprites.SpriteStore;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Depuração da ordem dos addons: para um looktype e direção, salva as camadas separadas e as
 * composições possíveis lado a lado. Args: pastaAssets looktype direção(0-3) saida.png
 */
public final class AddonOrderCheck {

    public static void main(String[] args) throws Exception {
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        OutfitInfo outfit = assets.outfit(Integer.parseInt(args[1]));
        int dir = Integer.parseInt(args[2]);
        SpriteStore store = new SpriteStore(assets.catalog());
        SpriteInfo info = outfit.idle().group().getSpriteInfo();

        BufferedImage base = layer(store, info, dir, 0);
        BufferedImage a1 = layer(store, info, dir, 1);
        BufferedImage a2 = layer(store, info, dir, 2);

        List<BufferedImage> panels = List.of(
                base, a1, a2,
                compose(base, a1, a2),          // corpo, addon 1, addon 2 (normal)
                compose(base, a2, a1),          // corpo, addon 2, addon 1 (addons invertidos)
                compose(a1, a2, base),          // addons atrás do corpo (como estava)
                compose(a2, a1, base));
        int scale = 3;
        int w = base.getWidth() * scale;
        BufferedImage out = new BufferedImage(w * panels.size(), w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x3c5a3c));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < panels.size(); i++) {
            g.drawImage(panels.get(i), i * w, 0, w, w, null);
        }
        g.dispose();
        ImageIO.write(out, "png", Path.of(args[3]).toFile());
        System.out.println("flags: " + assets.appearance(outfit.looktype()).getFlags());
    }

    private static BufferedImage layer(SpriteStore store, SpriteInfo info, int dir, int addon) {
        int width = info.getPatternWidth();
        int height = info.getPatternHeight();
        int layers = info.getLayers();
        int index = ((addon * width) + dir) * layers; // fase 0, sem montaria, camada base
        return store.sprite(info.getSpriteId(index));
    }

    private static BufferedImage compose(BufferedImage... images) {
        BufferedImage out = new BufferedImage(images[0].getWidth(), images[0].getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (BufferedImage image : images) {
            g.drawImage(image, 0, 0, null);
        }
        g.dispose();
        return out;
    }
}
