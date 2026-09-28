package org.tibiawalk.core.metadata;

/**
 * Um personagem do jogo (NPC, monstro ou boss) com o outfit completo que ele usa.
 *
 * <p>Cores são índices da paleta (0-132), addons é o bitmask (1, 2, 3) e mount o looktype da montaria (0 = sem).
 *
 * @param source de onde veio: "canary", "staticdata" ou "staticdata+canary"
 */
public record GameCharacter(
        String name,
        Kind kind,
        int looktype,
        int head,
        int body,
        int legs,
        int feet,
        int addons,
        int mount,
        String source) {

    public enum Kind {
        NPC,
        MONSTER,
        BOSS
    }
}
