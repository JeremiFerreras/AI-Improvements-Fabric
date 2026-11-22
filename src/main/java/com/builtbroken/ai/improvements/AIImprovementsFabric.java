package com.builtbroken.ai.improvements;

import com.builtbroken.ai.improvements.fabric.FabricEntityLifecycle;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point for the Minecraft 26.3 port.
 *
 * <p>This first porting checkpoint only establishes the Fabric bootstrap and
 * server-entity lifecycle bridge. The original AI editing engine is preserved
 * under {@code src/legacy-neoforge} and is not active yet.
 */
public final class AIImprovementsFabric implements ModInitializer {
    public static final String MOD_ID = "aiimprovements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FastTrig.init();
        FabricEntityLifecycle.register();

        LOGGER.warn(
                "AI Improvements Fabric 26.3 port scaffold loaded. " +
                "AI modification features are not migrated or active yet."
        );
    }
}
