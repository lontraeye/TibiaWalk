package org.tibiawalk.core.render;

/**
 * Cor de uma parte do outfit (cabeça, corpo, pernas, pés), em RGB.
 *
 * <p>Aceita tanto um índice da paleta do jogo (0 a 132, como o servidor envia em
 * lookHead/lookBody/...) quanto uma cor hex livre.
 */
public record TibiaColor(int rgb) {

    public static final int PALETTE_SIZE = 133;

    private static final int HSI_H_STEPS = 19;
    private static final int[] PALETTE = buildPalette();

    public static final TibiaColor WHITE = new TibiaColor(0xFFFFFF);

    public static TibiaColor palette(int index) {
        if (index < 0 || index >= PALETTE_SIZE) {
            throw new IllegalArgumentException("Índice de cor fora da paleta (0-132): " + index);
        }
        return new TibiaColor(PALETTE[index]);
    }

    public static TibiaColor hex(String hex) {
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() != 6) {
            throw new IllegalArgumentException("Cor hex deve ter 6 dígitos: " + hex);
        }
        return new TibiaColor(Integer.parseInt(value, 16));
    }

    /** "94" vira índice da paleta; "ff0000" ou "#ff0000" vira hex. */
    public static TibiaColor parse(String value) {
        String v = value.trim();
        if (v.length() <= 3 && v.chars().allMatch(Character::isDigit)) {
            return palette(Integer.parseInt(v));
        }
        return hex(v);
    }

    public int red() {
        return (rgb >> 16) & 0xFF;
    }

    public int green() {
        return (rgb >> 8) & 0xFF;
    }

    public int blue() {
        return rgb & 0xFF;
    }

    public String toHex() {
        return String.format("%06x", rgb);
    }

    /** Mesma fórmula HSI do cliente (19 tons x 7 saturações/intensidades). */
    private static int[] buildPalette() {
        int[] palette = new int[PALETTE_SIZE];
        for (int color = 0; color < PALETTE_SIZE; color++) {
            double hue;
            double saturation;
            double intensity;
            if (color % HSI_H_STEPS != 0) {
                hue = (color % HSI_H_STEPS) / 18.0;
                switch (color / HSI_H_STEPS) {
                    case 0 -> { saturation = 0.25; intensity = 1.00; }
                    case 1 -> { saturation = 0.25; intensity = 0.75; }
                    case 2 -> { saturation = 0.50; intensity = 0.75; }
                    case 3 -> { saturation = 0.667; intensity = 0.75; }
                    case 4 -> { saturation = 1.00; intensity = 1.00; }
                    case 5 -> { saturation = 1.00; intensity = 0.75; }
                    default -> { saturation = 1.00; intensity = 0.50; }
                }
            } else {
                hue = 0;
                saturation = 0;
                intensity = 1 - (double) color / HSI_H_STEPS / 7.0;
            }
            palette[color] = hsiToRgb(hue, saturation, intensity);
        }
        return palette;
    }

    private static int hsiToRgb(double hue, double saturation, double intensity) {
        if (intensity == 0) {
            return 0;
        }
        if (saturation == 0) {
            int v = (int) (intensity * 255);
            return v << 16 | v << 8 | v;
        }
        double r;
        double g;
        double b;
        if (hue < 1.0 / 6.0) {
            r = intensity;
            b = intensity * (1 - saturation);
            g = b + (intensity - b) * 6 * hue;
        } else if (hue < 2.0 / 6.0) {
            g = intensity;
            b = intensity * (1 - saturation);
            r = g - (intensity - b) * (6 * hue - 1);
        } else if (hue < 3.0 / 6.0) {
            g = intensity;
            r = intensity * (1 - saturation);
            b = r + (intensity - r) * (6 * hue - 2);
        } else if (hue < 4.0 / 6.0) {
            b = intensity;
            r = intensity * (1 - saturation);
            g = b - (intensity - r) * (6 * hue - 3);
        } else if (hue < 5.0 / 6.0) {
            b = intensity;
            g = intensity * (1 - saturation);
            r = g + (intensity - g) * (6 * hue - 4);
        } else {
            r = intensity;
            g = intensity * (1 - saturation);
            b = r - (intensity - g) * (6 * hue - 5);
        }
        return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }
}
