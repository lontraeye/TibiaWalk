package org.tibiawalk.core.assets;

import org.tibiawalk.core.proto.AppearancesProto.Appearance;
import org.tibiawalk.core.proto.AppearancesProto.FIXED_FRAME_GROUP;
import org.tibiawalk.core.proto.AppearancesProto.FrameGroup;
import org.tibiawalk.core.proto.AppearancesProto.SpriteInfo;

/**
 * O que um looktype suporta, lido do appearances.dat.
 *
 * <p>No cliente, os sprites de um outfit são indexados por
 * (fase, montaria, addon, direção, camada): {@code directions} = pattern_width,
 * {@code addonLayers} = pattern_height (1 = só base, 3 = base + addon 1 + addon 2),
 * {@code mountStates} = pattern_depth (2 = tem versão montada) e
 * {@code layers} = 2 quando existe a máscara de cores (o antigo "_template.png").
 */
public record OutfitInfo(
        int looktype,
        int directions,
        int addonLayers,
        int mountStates,
        int layers,
        Animation idle,
        Animation moving,
        boolean spriteCountMismatch) {

    /** Uma animação (idle ou moving): quantidade de frames e duração de cada um em ms. */
    public record Animation(FrameGroup group, int frames, int[] durationsMs) {
    }

    public int addonCount() {
        return addonLayers - 1;
    }

    public boolean mountable() {
        return mountStates > 1;
    }

    public boolean colorable() {
        return layers > 1;
    }

    static OutfitInfo from(Appearance appearance) {
        FrameGroup idleGroup = null;
        FrameGroup movingGroup = null;
        for (FrameGroup group : appearance.getFrameGroupList()) {
            if (group.getFixedFrameGroup() == FIXED_FRAME_GROUP.FIXED_FRAME_GROUP_OUTFIT_MOVING) {
                movingGroup = group;
            } else if (idleGroup == null) {
                idleGroup = group;
            }
        }
        if (idleGroup == null) {
            idleGroup = movingGroup;
        }

        Animation idle = idleGroup == null ? null : animationOf(idleGroup);
        Animation moving = movingGroup == null ? null : animationOf(movingGroup);
        SpriteInfo info = idleGroup == null ? SpriteInfo.getDefaultInstance() : idleGroup.getSpriteInfo();

        boolean mismatch = false;
        for (FrameGroup group : appearance.getFrameGroupList()) {
            mismatch |= expectedSprites(group) != group.getSpriteInfo().getSpriteIdCount();
        }

        return new OutfitInfo(
                appearance.getId(),
                Math.max(1, info.getPatternWidth()),
                Math.max(1, info.getPatternHeight()),
                Math.max(1, info.getPatternDepth()),
                Math.max(1, info.getLayers()),
                idle,
                moving,
                mismatch);
    }

    private static Animation animationOf(FrameGroup group) {
        SpriteInfo info = group.getSpriteInfo();
        if (!info.hasAnimation() || info.getAnimation().getSpritePhaseCount() == 0) {
            return new Animation(group, 1, new int[] {0});
        }
        var phases = info.getAnimation().getSpritePhaseList();
        int[] durations = new int[phases.size()];
        for (int i = 0; i < durations.length; i++) {
            durations[i] = phases.get(i).getDurationMin();
        }
        return new Animation(group, phases.size(), durations);
    }

    private static int expectedSprites(FrameGroup group) {
        SpriteInfo info = group.getSpriteInfo();
        int phases = info.hasAnimation() ? Math.max(1, info.getAnimation().getSpritePhaseCount()) : 1;
        return Math.max(1, info.getPatternWidth())
                * Math.max(1, info.getPatternHeight())
                * Math.max(1, info.getPatternDepth())
                * Math.max(1, info.getLayers())
                * phases;
    }
}
