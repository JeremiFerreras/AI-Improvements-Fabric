package com.builtbroken.ai.improvements.modifier.editor;

import com.builtbroken.ai.improvements.ConfigMain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ConfigDrivenEdit implements IEntityAiModifier {
    private final ConfigMain.BooleanValue configCheck;
    private final IEntityAiModifier modifier;

    public ConfigDrivenEdit(ConfigMain.BooleanValue configCheck, IEntityAiModifier modifier) {
        this.configCheck = configCheck;
        this.modifier = modifier;
    }

    @Override
    public Goal handle(Mob entity, Goal aiTask) {
        return configCheck.get() ? modifier.handle(entity, aiTask) : aiTask;
    }
}
