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

/**
 * Ordered goal-edit pipeline.
 *
 * <p>Removals are queued while iterating the selector so the selector's backing
 * set is never mutated during traversal. Adaptive ordering only changes the
 * order in which independent predicates are evaluated; it does not change
 * which predicates are registered or which goals they match.
 */
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

        // Apply mutations only after the selector has been fully traversed.
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
                // Do not collect statistics or reorder anything when the option
                // is disabled. The old NeoForge implementation only incremented
                // inside bubble(), after comparing two initially-zero counters,
                // so the adaptive order never started moving.
                if (ConfigMain.CONFIG.enableCallBubbling.get()) {
                    modifier.callCount++;

                    if (modifier.callCount >= Integer.MAX_VALUE / 2) {
                        resortNormalize = true;
                    } else if (i > 0 && modifier.callCount > nodes[i - 1].callCount) {
                        bubble(modifier, i);
                    }
                }
                return result;
            }
        }
        return goal;
    }

    /**
     * Rebuilds the order when counters approach overflow.
     *
     * <p>Higher hit counts must be first because processing starts at index 0.
     * Halving preserves relative frequency. Counters are normalized even when
     * this layer contains a single modifier, so the counter cannot overflow.
     */
    protected void resort(boolean normalize) {
        if (size > 1) {
            Arrays.sort(nodes, 0, size,
                    Comparator.comparingInt((ModifierNode node) -> node.callCount).reversed());
        }

        if (normalize) {
            for (int i = 0; i < size; i++) {
                nodes[i].callCount >>>= 1;
            }
        }
    }

    protected void bubble(ModifierNode modifier, int index) {
        nodes[index] = nodes[index - 1];
        nodes[index - 1] = modifier;
    }
}
