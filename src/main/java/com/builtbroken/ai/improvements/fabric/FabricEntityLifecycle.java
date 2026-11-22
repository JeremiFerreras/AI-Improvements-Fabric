package com.builtbroken.ai.improvements.fabric;

import com.builtbroken.ai.improvements.modifier.ModifierSystem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * Fabric-side replacement point for the old NeoForge entity-join subscriber.
 */
public final class FabricEntityLifecycle {
    private FabricEntityLifecycle() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(ModifierSystem::handleEntityLoad);
    }
}
