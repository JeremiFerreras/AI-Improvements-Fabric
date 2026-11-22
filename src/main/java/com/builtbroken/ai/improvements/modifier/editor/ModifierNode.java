package com.builtbroken.ai.improvements.modifier.editor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ModifierNode implements IEntityAiModifier {
    protected int callCount;
    private final IEntityAiModifier action;

    public ModifierNode(IEntityAiModifier action) {
        this.action = action;
    }

    @Override
    public Goal handle(Mob entity, Goal aiTask) {
        return action.handle(entity, aiTask);
    }
}
