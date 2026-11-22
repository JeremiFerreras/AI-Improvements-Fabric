package com.builtbroken.ai.improvements;

import com.builtbroken.ai.improvements.fabric.FabricEntityLifecycle;
import com.builtbroken.ai.improvements.modifier.ModifierSystem;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric entry point for the experimental Minecraft 26.3 port. */
public final class AIImprovementsFabric implements ModInitializer {
    public static final String MOD_ID = "aiimprovements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FastTrig.init();
        ConfigMain.load();
        ModifierSystem.init();
        FabricEntityLifecycle.register();

        LOGGER.warn("AI Improvements Fabric 26.3 experimental port initialized. " +
                "The AI editor is active; behavior and performance still require in-game validation.");
    }
}
