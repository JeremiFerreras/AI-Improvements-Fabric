package com.builtbroken.ai.improvements.modifier.filters;

import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface IFilterNode {
    FilterResult handle(Entity entity);
}
