package com.builtbroken.ai.improvements.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.builtbroken.ai.improvements.AIImprovementsFabric;
import com.builtbroken.ai.improvements.ConfigMain;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * First diagnostic pass: records the actual goal-selector contents produced by
 * vanilla Minecraft 26.3 in the GameTest server, before writing assumptions
 * about goals that may have changed between game releases.
 */
public final class AIImprovementsGameTests implements CustomTestMethodInvoker {
    @GameTest(maxTicks = 40)
    public void recordVanillaMobGoalBaselines(GameTestHelper helper) {
        ConfigMain config = ConfigMain.CONFIG;

        List<BoolState> states = List.of(
                BoolState.capture(config.allowRemoveCalls),
                BoolState.capture(config.replaceLookController.configValue()),
                BoolState.capture(config.replaceLookController.isAllowlist()),
                BoolState.capture(config.removeLookGoal.configValue()),
                BoolState.capture(config.removeLookGoal.isAllowlist()),
                BoolState.capture(config.removeLookRandom.configValue()),
                BoolState.capture(config.removeLookRandom.isAllowlist()),
                BoolState.capture(config.cow.removeFloat()),
                BoolState.capture(config.cow.removePanic()),
                BoolState.capture(config.cow.removeBreed()),
                BoolState.capture(config.cow.removeTempt()),
                BoolState.capture(config.cow.removeFollowParent()),
                BoolState.capture(config.cow.removeStroll()),
                BoolState.capture(config.sheep.removeFloat()),
                BoolState.capture(config.sheep.removePanic()),
                BoolState.capture(config.sheep.removeBreed()),
                BoolState.capture(config.sheep.removeTempt()),
                BoolState.capture(config.sheep.removeFollowParent()),
                BoolState.capture(config.sheep.removeStroll()),
                BoolState.capture(config.removeSheepEatBlock),
                BoolState.capture(config.removeFishSwim),
                BoolState.capture(config.removeFishAvoidPlayer),
                BoolState.capture(config.removeFishPanic),
                BoolState.capture(config.removeFishFollowLeader),
                BoolState.capture(config.removeFishPuff),
                BoolState.capture(config.removeSquidFlee),
                BoolState.capture(config.removeRandomMove)
        );

        List<String> replaceList = config.replaceLookController.filterList().get();
        List<String> lookList = config.removeLookGoal.filterList().get();
        List<String> randomLookList = config.removeLookRandom.filterList().get();

        try {
            config.allowRemoveCalls.set(true);
            config.replaceLookController.configValue().set(false);
            config.replaceLookController.isAllowlist().set(false);
            config.replaceLookController.filterList().set(List.of());

            config.removeLookGoal.configValue().set(false);
            config.removeLookGoal.isAllowlist().set(false);
            config.removeLookGoal.filterList().set(List.of());
            config.removeLookRandom.configValue().set(false);
            config.removeLookRandom.isAllowlist().set(false);
            config.removeLookRandom.filterList().set(List.of());

            config.cow.removeFloat().set(false);
            config.cow.removePanic().set(false);
            config.cow.removeBreed().set(false);
            config.cow.removeTempt().set(false);
            config.cow.removeFollowParent().set(false);
            config.cow.removeStroll().set(false);

            config.sheep.removeFloat().set(false);
            config.sheep.removePanic().set(false);
            config.sheep.removeBreed().set(false);
            config.sheep.removeTempt().set(false);
            config.sheep.removeFollowParent().set(false);
            config.sheep.removeStroll().set(false);
            config.removeSheepEatBlock.set(false);

            config.removeFishSwim.set(false);
            config.removeFishAvoidPlayer.set(false);
            config.removeFishPanic.set(false);
            config.removeFishFollowLeader.set(false);
            config.removeFishPuff.set(false);
            config.removeSquidFlee.set(false);
            config.removeRandomMove.set(false);

            Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, BlockPos.ZERO);
            Mob sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(2, 0, 0));
            Mob pufferfish = helper.spawnWithNoFreeWill(EntityTypes.PUFFERFISH, new BlockPos(4, 0, 0));
            Mob squid = helper.spawnWithNoFreeWill(EntityTypes.SQUID, new BlockPos(6, 0, 0));

            logGoals("cow", cow);
            logGoals("sheep", sheep);
            logGoals("pufferfish", pufferfish);
            logGoals("squid", squid);
        } finally {
            for (BoolState state : states) {
                state.restore();
            }
            config.replaceLookController.filterList().set(replaceList);
            config.removeLookGoal.filterList().set(lookList);
            config.removeLookRandom.filterList().set(randomLookList);
        }

        helper.succeed();
    }

    private static void logGoals(String label, Mob mob) {
        List<String> goals = new ArrayList<>();
        for (WrappedGoal wrappedGoal : mob.goalSelector.availableGoals) {
            Goal goal = wrappedGoal.getGoal();
            goals.add(goal.getClass().getName());
        }
        goals.sort(Comparator.naturalOrder());

        AIImprovementsFabric.LOGGER.info(
                "[AI Improvements GameTest] {} goals ({}): {} | LookControl={}",
                label,
                goals.size(),
                goals,
                mob.getLookControl().getClass().getName()
        );
    }

    private record BoolState(ConfigMain.BooleanValue value, boolean oldValue) {
        private static BoolState capture(ConfigMain.BooleanValue value) {
            return new BoolState(value, value.get());
        }

        private void restore() {
            value.set(oldValue);
        }
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
