package com.builtbroken.ai.improvements.fabric;

import com.builtbroken.ai.improvements.modifier.ModifierSystem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/** Registers the earliest public Fabric server-side entity-load hook. */
public final class FabricEntityLifecycle {
    private FabricEntityLifecycle() {
    }

    public static void register() {
        // ALLOW_LOAD runs immediately before insertion into ServerLevel.
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, spawnReason, isLoadedFromDisk) -> {
            ModifierSystem.handleEntityJoinLevel(entity, level);
            return true;
        });
    }
}
