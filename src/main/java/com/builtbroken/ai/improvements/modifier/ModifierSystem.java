package com.builtbroken.ai.improvements.modifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Fabric-side placeholder for the AI modifier engine.
 *
 * <p>The original implementation is retained in
 * {@code src/legacy-neoforge/java}. Its filters, goal editors, configuration
 * binding and private-member access are deliberately not wired into this
 * bootstrap until their Fabric equivalents have been migrated and validated.
 */
public final class ModifierSystem {
    private ModifierSystem() {
    }

    /**
     * Receives server-side entity lifecycle notifications from Fabric.
     * No AI changes are made at this porting checkpoint.
     */
    public static void handleEntityLoad(Entity entity, ServerLevel level) {
        // TODO(port): adapt the original FilterLayer/ModifierLevel pipeline.
        // TODO(port): preserve the original spawn/load timing semantics.
    }
}
