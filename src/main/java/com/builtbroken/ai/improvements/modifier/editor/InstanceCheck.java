package com.builtbroken.ai.improvements.modifier.editor;

import net.minecraft.world.entity.ai.goal.Goal;

@FunctionalInterface
public interface InstanceCheck {
    boolean isGoal(Goal goal);
}
