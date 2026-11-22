package com.builtbroken.ai.improvements.modifier.filters;

import net.minecraft.world.entity.Entity;

public class FilterNode implements IFilterNode {
    protected int callCount;
    protected final IFilterNode action;

    protected FilterNode(IFilterNode action) {
        this.action = action;
    }

    @Override
    public FilterResult handle(Entity entity) {
        return action.handle(entity);
    }
}
