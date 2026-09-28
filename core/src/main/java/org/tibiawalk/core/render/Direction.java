package org.tibiawalk.core.render;

/** Direção do outfit; o índice é o pattern X do cliente. */
public enum Direction {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    public static Direction parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
