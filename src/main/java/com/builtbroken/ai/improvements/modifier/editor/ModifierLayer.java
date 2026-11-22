package com.builtbroken.ai.improvements.modifier.editor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.builtbroken.ai.improvements.ConfigMain;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Ordered set of goal edits. Goal removals are deferred until iteration finishes. */
public final class ModifierLayer {
    private ModifierNode[] nodes = new ModifierNode[5];
    private int size;
    private boolean resortNormalize;

    public final boolean combatAI;

    public ModifierLayer(boolean combatAI) {
        this.combatAI = combatAI;
    }

    public void add(IEntityAiModifier modifier) {
        if (modifier == null) {
            return;
        }
        if (size == nodes.length) {
            nodes = Arrays.copyOf(nodes, nodes.length + 5);
        }
        nodes[size++] = new ModifierNode(modifier);
    }

    public void handle(Mob entity) {
        GoalSelector selector = combatAI ? entity.targetSelector : entity.goalSelector;
        Set<Goal> goalsToRemove = new HashSet<>();

        for (WrappedGoal prioritizedGoal : selector.availableGoals) {
            Goal goal = prioritizedGoal.getGoal();
            Goal replacement = process(entity, goal);
            if (replacement == null) {
                goalsToRemove.add(goal);
            } else if (replacement != goal) {
                // Replacement support remains deferred, matching the upstream TODO.
            }
        }

        for (Goal goal : goalsToRemove) {
            selector.removeGoal(goal);
        }

        if (resortNormalize) {
            resortNormalize = false;
            resort(true);
        }
    }

    protected Goal process(Mob entity, Goal goal) {
        for (int i = 0; i < size; i++) {
            ModifierNode modifier = nodes[i];
            Goal result = modifier.handle(entity, goal);

            if (result != goal) {
                if (ConfigMain.CONFIG.enableCallBubbling.get() && i != 0
                        && modifier.callCount > nodes[i - 1].callCount) {
                    bubble(modifier, i);
                }
                return result;
            }
        }
        return goal;
    }

    protected void resort(boolean normalize) {
        if (size == 0) {
            return;
        }
        Arrays.sort(nodes, 0, size, Comparator.comparingInt(node -> node.callCount));
        if (normalize) {
            long sum = 0;
            for (int i = 0; i < size; i++) {
                sum += nodes[i].callCount;
            }
            if (sum > 0) {
                for (int i = 0; i < size; i++) {
                    nodes[i].callCount = (int) Math.floor(nodes[i].callCount / (double) sum);
                }
            }
        }
    }

    protected void bubble(ModifierNode modifier, int index) {
        modifier.callCount++;
        if (modifier.callCount >= Integer.MAX_VALUE / 2) {
            resortNormalize = true;
            return;
        }
        nodes[index] = nodes[index - 1];
        nodes[index - 1] = modifier;
    }
}
