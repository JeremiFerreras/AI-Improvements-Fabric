package com.builtbroken.ai.improvements.gametest;

import java.lang.reflect.Method;
import java.util.List;

import com.builtbroken.ai.improvements.ConfigMain;
import com.builtbroken.ai.improvements.FixedLookControl;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.squid.Squid;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * Server-side behavior tests for the Fabric AI port.
 *
 * <p>All checks run in one GameTest to avoid concurrent mutation of the
 * process-wide config values. Entity insertion and goal edits happen
 * synchronously when each mob is added to the test ServerLevel.
 */
public final class AIImprovementsGameTests implements CustomTestMethodInvoker {
    @GameTest(maxTicks = 40)
    public void goalSettingsAreIsolated(GameTestHelper helper) {
        ConfigMain config = ConfigMain.CONFIG;

        BoolState allowRemoveCalls = BoolState.capture(config.allowRemoveCalls);
        BoolState cowFloat = BoolState.capture(config.cow.removeFloat());
        BoolState cowBreed = BoolState.capture(config.cow.removeBreed());
        BoolState replaceLookController = BoolState.capture(config.replaceLookController.configValue());
        BoolState replaceLookAllowlist = BoolState.capture(config.replaceLookController.isAllowlist());
        List<String> replaceLookList = config.replaceLookController.filterList().get();

        BoolState pufferPuff = BoolState.capture(config.removeFishPuff);
        BoolState fishFollowLeader = BoolState.capture(config.removeFishFollowLeader);
        BoolState squidFlee = BoolState.capture(config.removeSquidFlee);
        BoolState squidRandomMove = BoolState.capture(config.removeRandomMove);
        BoolState sheepEatBlock = BoolState.capture(config.removeSheepEatBlock);

        try {
            // Start from deterministic values independent of the user's config.
            config.allowRemoveCalls.set(true);
            config.cow.removeFloat().set(false);
            config.cow.removeBreed().set(false);

            config.replaceLookController.configValue().set(true);
            config.replaceLookController.isAllowlist().set(false);
            config.replaceLookController.filterList().set(List.of());

            config.removeFishPuff.set(false);
            config.removeFishFollowLeader.set(false);
            config.removeSquidFlee.set(false);
            config.removeRandomMove.set(false);
            config.removeSheepEatBlock.set(false);

            // Baseline: default cow goals remain installed and the vanilla
            // LookControl is replaced without altering its goal selector.
            Mob baselineCow = helper.spawnWithNoFreeWill(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(hasGoal(baselineCow, FloatGoal.class),
                    "Cow FloatGoal must remain with removal disabled");
            helper.assertTrue(hasGoal(baselineCow, BreedGoal.class),
                    "Cow BreedGoal must remain with removal disabled");
            helper.assertTrue(baselineCow.getLookControl() instanceof FixedLookControl,
                    "Vanilla cow LookControl must be replaced by FixedLookControl");

            // A single cow option removes only FloatGoal, not unrelated goals.
            config.cow.removeFloat().set(true);
            Mob floatRemovedCow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 0, 0));
            helper.assertFalse(hasGoal(floatRemovedCow, FloatGoal.class),
                    "Cow FloatGoal must be removed when remove_float is enabled");
            helper.assertTrue(hasGoal(floatRemovedCow, BreedGoal.class),
                    "Removing FloatGoal must not remove BreedGoal");

            // The global safety switch must take precedence over individual flags.
            config.allowRemoveCalls.set(false);
            Mob guardedCow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 0, 0));
            helper.assertTrue(hasGoal(guardedCow, FloatGoal.class),
                    "allow_remove_calls=false must prevent all goal removal");

            // Pufferfish puff must be controlled by remove_puff, not by the
            // unrelated follow-leader option.
            config.allowRemoveCalls.set(true);
            config.removeFishPuff.set(true);
            config.removeFishFollowLeader.set(false);
            Mob puffRemoved = helper.spawnWithNoFreeWill(EntityTypes.PUFFERFISH, new BlockPos(6, 0, 0));
            helper.assertFalse(hasGoal(puffRemoved, Pufferfish.PufferfishPuffGoal.class),
                    "remove_puff must remove PufferfishPuffGoal");

            config.removeFishPuff.set(false);
            config.removeFishFollowLeader.set(true);
            Mob puffPreserved = helper.spawnWithNoFreeWill(EntityTypes.PUFFERFISH, new BlockPos(8, 0, 0));
            helper.assertTrue(hasGoal(puffPreserved, Pufferfish.PufferfishPuffGoal.class),
                    "remove_follow_leader must not remove PufferfishPuffGoal");

            // Squid flee and random movement must have independent settings.
            config.removeSquidFlee.set(true);
            config.removeRandomMove.set(false);
            Mob fleeRemoved = helper.spawnWithNoFreeWill(EntityTypes.SQUID, new BlockPos(10, 0, 0));
            helper.assertFalse(hasGoal(fleeRemoved, Squid.SquidFleeGoal.class),
                    "remove_flee must remove SquidFleeGoal");
            helper.assertTrue(hasGoal(fleeRemoved, Squid.SquidRandomMovementGoal.class),
                    "remove_flee must not remove SquidRandomMovementGoal");

            config.removeSquidFlee.set(false);
            config.removeRandomMove.set(true);
            Mob randomMoveRemoved = helper.spawnWithNoFreeWill(EntityTypes.SQUID, new BlockPos(12, 0, 0));
            helper.assertTrue(hasGoal(randomMoveRemoved, Squid.SquidFleeGoal.class),
                    "remove_random_move must not remove SquidFleeGoal");
            helper.assertFalse(hasGoal(randomMoveRemoved, Squid.SquidRandomMovementGoal.class),
                    "remove_random_move must remove SquidRandomMovementGoal");

            // Sheep's block-eating goal must be independently removable.
            config.removeSheepEatBlock.set(true);
            Mob sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(14, 0, 0));
            helper.assertFalse(hasGoal(sheep, EatBlockGoal.class),
                    "remove_eat_block must remove Sheep EatBlockGoal");
        } finally {
            allowRemoveCalls.restore();
            cowFloat.restore();
            cowBreed.restore();
            replaceLookController.restore();
            replaceLookAllowlist.restore();
            config.replaceLookController.filterList().set(replaceLookList);
            pufferPuff.restore();
            fishFollowLeader.restore();
            squidFlee.restore();
            squidRandomMove.restore();
            sheepEatBlock.restore();
        }

        helper.succeed();
    }

    private static boolean hasGoal(Mob mob, Class<? extends Goal> goalClass) {
        for (WrappedGoal wrappedGoal : mob.goalSelector.availableGoals) {
            if (goalClass.isInstance(wrappedGoal.getGoal())) {
                return true;
            }
        }
        return false;
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
