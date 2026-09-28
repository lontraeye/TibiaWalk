package org.tibiawalk.core.assets;

/**
 * Uma folha de sprites do cliente (sprites-*.bmp.lzma), como listada no catalog-content.json.
 *
 * @param file          nome do arquivo dentro da pasta assets
 * @param spriteType    layout da folha: 0 = 32x32, 1 = 32x64, 2 = 64x32, 3 = 64x64
 * @param firstSpriteId primeiro sprite ID contido na folha
 * @param lastSpriteId  último sprite ID contido na folha (inclusivo)
 */
public record SpriteSheet(String file, int spriteType, int firstSpriteId, int lastSpriteId) {

    public boolean contains(int spriteId) {
        return spriteId >= firstSpriteId && spriteId <= lastSpriteId;
    }

    public int spriteWidth() {
        return (spriteType == 2 || spriteType == 3) ? 64 : 32;
    }

    public int spriteHeight() {
        return (spriteType == 1 || spriteType == 3) ? 64 : 32;
    }
}
