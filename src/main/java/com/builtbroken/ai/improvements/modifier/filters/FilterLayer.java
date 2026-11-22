package com.builtbroken.ai.improvements.modifier.filters;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.world.entity.Entity;

/** Short-circuiting entity filter pipeline with per-node call-frequency bubbling. */
public final class FilterLayer extends FilterNode {
    private FilterNode[] nodes = new FilterNode[5];
    private int size;

    public FilterLayer(IFilterNode filter) {
        super(filter);
    }

    public void add(IFilterNode modifier) {
        IFilterNode action = modifier == null ? NoopNode.INSTANCE : modifier;
        if (size == nodes.length) {
            nodes = Arrays.copyOf(nodes, nodes.length + 5);
        }
        nodes[size++] = new FilterNode(action);
    }

    @Override
    public FilterResult handle(Entity entity) {
        FilterResult result = action != null ? action.handle(entity) : FilterResult.FILTERED;
        if (result == FilterResult.DID_NOTHING) {
            return result;
        }

        for (int i = 0; i < size; i++) {
            FilterNode node = nodes[i];
            result = node.handle(entity);

            if (result == FilterResult.FILTERED) {
                node.callCount++;
                if (node.callCount >= Integer.MAX_VALUE / 2) {
                    resortAndNormalize();
                } else if (i > 0 && node.callCount > nodes[i - 1].callCount) {
                    nodes[i] = nodes[i - 1];
                    nodes[i - 1] = node;
                }
                return result;
            }
            // MODIFIED deliberately continues; DID_NOTHING also falls through.
        }
        return result;
    }

    private void resortAndNormalize() {
        Arrays.sort(nodes, 0, size, Comparator.comparingInt(node -> node.callCount));
        long sum = 0;
        for (int i = 0; i < size; i++) {
            sum += nodes[i].callCount;
        }
        if (sum == 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            nodes[i].callCount = (int) Math.floor(nodes[i].callCount / (double) sum);
        }
    }
}
