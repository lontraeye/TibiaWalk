package org.tibiawalk.core.render;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Grava frames ARGB como GIF animado em loop, com fundo transparente. */
public final class GifWriter {

    private static final int MAX_COLORS = 255; // índice 0 fica para o transparente

    private GifWriter() {
    }

    public static void write(List<OutfitRenderer.Frame> frames, OutputStream out) throws IOException {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Nenhum frame para gravar");
        }
        int[] palette = palette(frames);
        IndexColorModel colorModel = colorModel(palette);
        Map<Integer, Integer> lookup = new HashMap<>();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (OutfitRenderer.Frame frame : frames) {
                BufferedImage indexed = toIndexed(frame.image(), colorModel, palette, lookup);
                IIOMetadata metadata = metadata(writer, indexed, frame.durationMs(), first);
                writer.writeToSequence(new IIOImage(indexed, null, metadata), null);
                first = false;
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static boolean opaque(int argb) {
        return (argb >>> 24) >= 128;
    }

    private static int[] palette(List<OutfitRenderer.Frame> frames) {
        Set<Integer> colors = new LinkedHashSet<>();
        for (OutfitRenderer.Frame frame : frames) {
            BufferedImage img = frame.image();
            for (int argb : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
                if (opaque(argb)) {
                    colors.add(argb & 0xFFFFFF);
                }
            }
        }
        if (colors.size() <= MAX_COLORS) {
            return colors.stream().mapToInt(Integer::intValue).toArray();
        }
        return medianCut(new ArrayList<>(colors), MAX_COLORS);
    }

    /** Divide recursivamente a caixa de cores pelo canal de maior amplitude. */
    private static int[] medianCut(List<Integer> colors, int target) {
        List<List<Integer>> boxes = new ArrayList<>();
        boxes.add(colors);
        while (boxes.size() < target) {
            List<Integer> widest = null;
            int widestChannel = 0;
            int widestRange = 0;
            for (List<Integer> box : boxes) {
                if (box.size() < 2) {
                    continue;
                }
                for (int channel = 0; channel < 3; channel++) {
                    int range = range(box, channel);
                    if (range > widestRange) {
                        widestRange = range;
                        widest = box;
                        widestChannel = channel;
                    }
                }
            }
            if (widest == null) {
                break;
            }
            int shift = 16 - widestChannel * 8;
            widest.sort(Comparator.comparingInt(c -> (c >> shift) & 0xFF));
            int mid = widest.size() / 2;
            boxes.remove(widest);
            boxes.add(new ArrayList<>(widest.subList(0, mid)));
            boxes.add(new ArrayList<>(widest.subList(mid, widest.size())));
        }
        int[] palette = new int[boxes.size()];
        for (int i = 0; i < palette.length; i++) {
            long r = 0;
            long g = 0;
            long b = 0;
            for (int c : boxes.get(i)) {
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            int n = boxes.get(i).size();
            palette[i] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        }
        return palette;
    }

    private static int range(List<Integer> box, int channel) {
        int shift = 16 - channel * 8;
        int min = 255;
        int max = 0;
        for (int c : box) {
            int v = (c >> shift) & 0xFF;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private static IndexColorModel colorModel(int[] palette) {
        int size = palette.length + 1;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        for (int i = 0; i < palette.length; i++) {
            r[i + 1] = (byte) (palette[i] >> 16);
            g[i + 1] = (byte) (palette[i] >> 8);
            b[i + 1] = (byte) palette[i];
        }
        return new IndexColorModel(8, size, r, g, b, 0);
    }

    private static BufferedImage toIndexed(BufferedImage src, IndexColorModel model, int[] palette,
                                           Map<Integer, Integer> lookup) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, model);
        byte[] data = ((java.awt.image.DataBufferByte) out.getRaster().getDataBuffer()).getData();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int argb = px[i];
            data[i] = opaque(argb)
                    ? (byte) (int) lookup.computeIfAbsent(argb & 0xFFFFFF, rgb -> nearest(palette, rgb) + 1)
                    : 0;
        }
        return out;
    }

    private static int nearest(int[] palette, int rgb) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int dr = ((palette[i] >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((palette[i] >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (palette[i] & 0xFF) - (rgb & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static IIOMetadata metadata(ImageWriter writer, BufferedImage image, int durationMs, boolean first)
            throws IOException {
        IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "restoreToBackgroundColor");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "TRUE");
        control.setAttribute("transparentColorIndex", "0");
        control.setAttribute("delayTime", Integer.toString(Math.max(1, Math.round(durationMs / 10f))));

        if (first) {
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[] {1, 0, 0}); // 0 = loop infinito
            extensions.appendChild(loop);
        }

        metadata.setFromTree(format, root);
        return metadata;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
