package org.tibiawalk.core.sprites;

import org.tukaani.xz.LZMAInputStream;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodifica uma folha sprites-*.bmp.lzma do cliente 12+.
 *
 * <p>Layout do arquivo: bytes 0x00 de preenchimento, assinatura da CipSoft
 * {@code 70 0A FA 80 24}, tamanho comprimido em varint de 7 bits, e então um
 * cabeçalho LZMA "alone" (props, dicionário u32, tamanho u64 que não é confiável)
 * seguido do fluxo LZMA. O conteúdo descomprimido é um BMP de 32 bits (BGRA).
 */
public final class SpriteSheetDecoder {

    public static final int SHEET_SIZE = 384;

    private SpriteSheetDecoder() {
    }

    public static BufferedImage decode(Path file) throws IOException {
        return decodeBmp(decompress(Files.readAllBytes(file)), file);
    }

    static byte[] decompress(byte[] data) throws IOException {
        int pos = 0;
        while (pos < data.length && data[pos] == 0) {
            pos++;
        }
        pos += 5; // assinatura 70 0A FA 80 24
        while ((data[pos++] & 0x80) != 0) {
            // varint do tamanho comprimido: não precisamos dele
        }

        byte props = data[pos];
        int dictSize = (data[pos + 1] & 0xFF)
                | (data[pos + 2] & 0xFF) << 8
                | (data[pos + 3] & 0xFF) << 16
                | (data[pos + 4] & 0xFF) << 24;
        pos += 1 + 4 + 8; // props + dicionário + tamanho descomprimido (ignorado)

        InputStream raw = new ByteArrayInputStream(data, pos, data.length - pos);
        ByteArrayOutputStream out = new ByteArrayOutputStream(SHEET_SIZE * SHEET_SIZE * 4 + 256);
        try (LZMAInputStream lzma = new LZMAInputStream(raw, -1, props, dictSize)) {
            lzma.transferTo(out);
        } catch (EOFException e) {
            // Sem marcador de fim: o fluxo termina junto com a entrada; o que já saiu é o BMP inteiro.
        }
        return out.toByteArray();
    }

    private static BufferedImage decodeBmp(byte[] bmp, Path source) throws IOException {
        if (bmp.length < 54 || bmp[0] != 'B' || bmp[1] != 'M') {
            throw new IOException("Conteúdo descomprimido não é um BMP: " + source);
        }
        int dataOffset = readInt(bmp, 10);
        int width = readInt(bmp, 18);
        int height = readInt(bmp, 22);
        int bitsPerPixel = (bmp[28] & 0xFF) | (bmp[29] & 0xFF) << 8;
        if (bitsPerPixel != 32) {
            throw new IOException("BMP com " + bitsPerPixel + " bits não suportado: " + source);
        }

        boolean bottomUp = height > 0;
        height = Math.abs(height);
        if (dataOffset + width * height * 4 > bmp.length) {
            throw new IOException("BMP truncado: " + source);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            int src = dataOffset + y * width * 4;
            for (int x = 0; x < width; x++, src += 4) {
                int b = bmp[src] & 0xFF;
                int g = bmp[src + 1] & 0xFF;
                int r = bmp[src + 2] & 0xFF;
                int a = bmp[src + 3] & 0xFF;
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            image.setRGB(0, bottomUp ? height - 1 - y : y, width, 1, row, 0, width);
        }
        return image;
    }

    private static int readInt(byte[] b, int off) {
        return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8 | (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
    }
}
