package org.tibiawalk.core.render;

/**
 * O que renderizar.
 *
 * @param looktype  looktype do outfit
 * @param addons    bitmask: 1 = primeiro addon, 2 = segundo, 3 = ambos
 * @param mount     looktype da montaria, 0 = sem montaria
 * @param direction direção
 */
public record OutfitRequest(
        int looktype,
        int addons,
        TibiaColor head,
        TibiaColor body,
        TibiaColor legs,
        TibiaColor feet,
        int mount,
        Direction direction) {

    public static OutfitRequest of(int looktype) {
        TibiaColor w = TibiaColor.WHITE;
        return new OutfitRequest(looktype, 0, w, w, w, w, 0, Direction.SOUTH);
    }

    public OutfitRequest withAddons(int addons) {
        return new OutfitRequest(looktype, addons, head, body, legs, feet, mount, direction);
    }

    public OutfitRequest withColors(TibiaColor head, TibiaColor body, TibiaColor legs, TibiaColor feet) {
        return new OutfitRequest(looktype, addons, head, body, legs, feet, mount, direction);
    }

    public OutfitRequest withMount(int mount) {
        return new OutfitRequest(looktype, addons, head, body, legs, feet, mount, direction);
    }

    public OutfitRequest withDirection(Direction direction) {
        return new OutfitRequest(looktype, addons, head, body, legs, feet, mount, direction);
    }
}
