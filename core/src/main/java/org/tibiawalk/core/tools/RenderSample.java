package org.tibiawalk.core.tools;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.render.AnimationType;
import org.tibiawalk.core.render.Direction;
import org.tibiawalk.core.render.GifWriter;
import org.tibiawalk.core.render.OutfitRenderer;
import org.tibiawalk.core.render.OutfitRequest;
import org.tibiawalk.core.render.TibiaColor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderiza exemplos para conferência visual.
 * Args: pastaAssets pastaSaida looktype addons head body legs feet [mount]
 */
public final class RenderSample {

    public static void main(String[] args) throws Exception {
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        Path out = Files.createDirectories(Path.of(args[1]));
        OutfitRenderer renderer = new OutfitRenderer(assets);

        OutfitRequest request = OutfitRequest.of(Integer.parseInt(args[2]))
                .withAddons(Integer.parseInt(args[3]))
                .withColors(TibiaColor.parse(args[4]), TibiaColor.parse(args[5]),
                        TibiaColor.parse(args[6]), TibiaColor.parse(args[7]))
                .withMount(args.length > 8 ? Integer.parseInt(args[8]) : 0);
        String name = request.looktype() + "_a" + request.addons() + (request.mount() > 0 ? "_m" + request.mount() : "");

        long start = System.nanoTime();
        List<BufferedImage> directions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            directions.add(renderer.still(request.withDirection(direction)));
        }
        List<OutfitRenderer.Frame> walk = renderer.frames(request, AnimationType.MOVING, 100);
        long ms = (System.nanoTime() - start) / 1_000_000;

        ImageIO.write(directions.get(Direction.SOUTH.ordinal()), "png", out.resolve(name + "_still.png").toFile());
        ImageIO.write(strip(directions, 4), "png", out.resolve(name + "_directions.png").toFile());
        ImageIO.write(strip(walk.stream().map(OutfitRenderer.Frame::image).toList(), 4), "png",
                out.resolve(name + "_walk_frames.png").toFile());
        try (OutputStream gif = Files.newOutputStream(out.resolve(name + "_walk.gif"))) {
            GifWriter.write(walk, gif);
        }
        System.out.printf("%s: %d frames %dx%d, renderizado em %d ms -> %s%n", name, walk.size(),
                walk.get(0).image().getWidth(), walk.get(0).image().getHeight(), ms, out.toAbsolutePath());
    }

    /** Lado a lado, ampliado para facilitar a conferência. */
    private static BufferedImage strip(List<BufferedImage> images, int scale) {
        int w = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int h = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(1);
        BufferedImage strip = new BufferedImage(w * images.size() * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = strip.createGraphics();
        g.setColor(new java.awt.Color(0x40, 0x60, 0x40));
        g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
        for (int i = 0; i < images.size(); i++) {
            g.drawImage(images.get(i), i * w * scale, 0, w * scale, h * scale, null);
        }
        g.dispose();
        return strip;
    }
}
