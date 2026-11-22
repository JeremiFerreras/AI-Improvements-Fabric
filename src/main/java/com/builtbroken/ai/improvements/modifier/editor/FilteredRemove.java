package com.builtbroken.ai.improvements.modifier.editor;

import com.builtbroken.ai.improvements.ConfigMain;
import com.builtbroken.ai.improvements.ConfigMain.FilteredConfigValue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public final class FilteredRemove implements IEntityAiModifier {
    private final InstanceCheck instanceCheck;
    private final FilteredConfigValue filteredConfigValue;

    public FilteredRemove(InstanceCheck instanceCheck, FilteredConfigValue filteredConfigValue) {
        this.instanceCheck = instanceCheck;
        this.filteredConfigValue = filteredConfigValue;
    }

    @Override
    public Goal handle(Mob entity, Goal aiTask) {
        if (ConfigMain.CONFIG.allowRemoveCalls.get() && instanceCheck.isGoal(aiTask)) {
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!filteredConfigValue.isFiltered(entityId) && filteredConfigValue.configValue().get()) {
                return null;
            }
        }
        return aiTask;
    }
}
