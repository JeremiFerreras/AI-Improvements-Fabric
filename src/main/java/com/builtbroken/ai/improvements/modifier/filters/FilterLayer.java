package com.builtbroken.ai.improvements.modifier.filters;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.world.entity.Entity;

/**
 * Short-circuiting entity filter pipeline with adaptive ordering.
 *
 * <p>FILTERED consumes the entity at this level. MODIFIED records that a
 * previous node changed it but deliberately continues through later filters.
 */
public final class FilterLayer extends FilterNode {
    private static final int COUNTER_RENORMALIZE_THRESHOLD = Integer.MAX_VALUE / 2;

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

        boolean modified = result == FilterResult.MODIFIED;

        for (int i = 0; i < size; i++) {
            FilterNode node = nodes[i];
            result = node.handle(entity);

            if (result == FilterResult.FILTERED) {
                node.callCount++;

                if (node.callCount >= COUNTER_RENORMALIZE_THRESHOLD) {
                    resortAndNormalize();
                } else if (i > 0 && node.callCount > nodes[i - 1].callCount) {
                    bubble(node, i);
                }
                return FilterResult.FILTERED;
            }

            if (result == FilterResult.MODIFIED) {
                modified = true;
            }
        }

        // Do not lose an earlier modification merely because a later,
        // unrelated filter returned DID_NOTHING.
        return modified ? FilterResult.MODIFIED : FilterResult.DID_NOTHING;
    }

    private void resortAndNormalize() {
        if (size < 2) {
            return;
        }

        // Frequently matching nodes are evaluated first.
        Arrays.sort(nodes, 0, size,
                Comparator.comparingInt((FilterNode node) -> node.callCount).reversed());

        // Preserve relative frequency. floor(count / sum) would collapse
        // almost every positive counter to zero.
        for (int i = 0; i < size; i++) {
            nodes[i].callCount >>>= 1;
        }
    }

    private void bubble(FilterNode node, int index) {
        nodes[index] = nodes[index - 1];
        nodes[index - 1] = node;
    }
}
