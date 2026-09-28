package org.tibiawalk.core.metadata;

public enum LooktypeKind {
    /** Outfit que o jogador pode vestir (outfits.xml). */
    PLAYER,
    /** Montaria (mounts.xml). */
    MOUNT,
    /** Monstro ou boss (staticdata do cliente, ou arquivos de monstro do Canary). */
    CREATURE,
    /** NPC (arquivos de NPC do Canary). */
    NPC
}
