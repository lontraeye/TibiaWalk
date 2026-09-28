package org.tibiawalk.core.metadata;

import java.util.List;

/**
 * O que se sabe de um looktype além do visual.
 *
 * @param sex     "male" ou "female" para outfits de player; null nos demais
 * @param mountId id da montaria no servidor (só para MOUNT)
 * @param aliases outros nomes que usam o mesmo looktype (criaturas repetem muito)
 * @param source  de onde veio: "tibiawiki", "canary", "staticdata" ou "heuristic"
 */
public record LooktypeMeta(
        int looktype,
        LooktypeKind kind,
        String name,
        String sex,
        boolean premium,
        Integer mountId,
        List<String> aliases,
        String source) {

    public LooktypeMeta {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
