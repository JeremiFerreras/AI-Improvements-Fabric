package com.builtbroken.ai.improvements.modifier.editor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

@FunctionalInterface
public interface IEntityAiModifier {
    /** @return null to remove the goal, same instance to keep it, or a replacement. */
    Goal handle(Mob entity, Goal aiTask);
}
