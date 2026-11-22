package com.builtbroken.ai.improvements.modifier;

import java.util.function.Predicate;

import com.builtbroken.ai.improvements.modifier.editor.ModifierLayer;
import com.builtbroken.ai.improvements.modifier.filters.FilterLayer;
import com.builtbroken.ai.improvements.modifier.filters.FilterNode;
import com.builtbroken.ai.improvements.modifier.filters.FilterResult;
import com.builtbroken.ai.improvements.modifier.filters.IFilterNode;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** One level in the entity-type-specific AI modifier tree. */
public final class ModifierLevel extends FilterNode {
    public final ModifierLayer goalEditor = new ModifierLayer(false);
    public final ModifierLayer combatGoalEditor = new ModifierLayer(true);
    public final FilterLayer filters = new FilterLayer(null);

    public static ModifierLevel newFilter(Predicate<Entity> filter) {
        return new ModifierLevel(entity ->
                filter.test(entity) ? FilterResult.FILTERED : FilterResult.DID_NOTHING);
    }

    private ModifierLevel(IFilterNode filter) {
        super(filter);
    }

    @Override
    public FilterResult handle(Entity entity) {
        FilterResult result = action.handle(entity);
        if (result != FilterResult.DID_NOTHING) {
            if (entity instanceof Mob mob) {
                goalEditor.handle(mob);
                combatGoalEditor.handle(mob);
            }
            return filters.handle(entity);
        }
        return result;
    }
}
