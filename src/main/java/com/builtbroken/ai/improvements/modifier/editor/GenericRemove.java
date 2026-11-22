package com.builtbroken.ai.improvements.modifier.editor;

import com.builtbroken.ai.improvements.ConfigMain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public final class GenericRemove implements IEntityAiModifier {
    private final InstanceCheck instanceCheck;
    private final ConfigMain.BooleanValue configCheck;

    public GenericRemove(InstanceCheck instanceCheck, ConfigMain.BooleanValue configCheck) {
        this.instanceCheck = instanceCheck;
        this.configCheck = configCheck;
    }

    @Override
    public Goal handle(Mob entity, Goal aiTask) {
        return ConfigMain.CONFIG.allowRemoveCalls.get()
                && instanceCheck.isGoal(aiTask)
                && configCheck.get() ? null : aiTask;
    }
}
