package com.builtbroken.ai.improvements.gametest;

import java.lang.reflect.Method;
import java.util.List;

import com.builtbroken.ai.improvements.ConfigMain;
import com.builtbroken.ai.improvements.FixedLookControl;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.squid.Squid;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * Server-side regression tests using normal mobs with AI enabled.
 *
 * <p>All options are tested in one GameTest so the singleton configuration
 * cannot be mutated concurrently by multiple tests. Every spawned mob is
 * inspected immediately after insertion, before its first AI tick.
 */
public final class AIImprovementsGameTests implements CustomTestMethodInvoker {
    @GameTest(maxTicks = 60)
    public void goalOptionsOnlyChangeTheirTargetGoals(GameTestHelper helper) {
        ConfigMain config = ConfigMain.CONFIG;

        List<BoolState> states = List.of(
                BoolState.capture(config.allowRemoveCalls),
                BoolState.capture(config.replaceLookController.configValue()),
                BoolState.capture(config.replaceLookController.isAllowlist()),
                BoolState.capture(config.removeLookGoal.configValue()),
                BoolState.capture(config.removeLookGoal.isAllowlist()),
                BoolState.capture(config.cow.removeFloat()),
                BoolState.capture(config.cow.removeBreed()),
                BoolState.capture(config.removeSheepEatBlock),
                BoolState.capture(config.removeFishSwim),
                BoolState.capture(config.removeFishFollowLeader),
                BoolState.capture(config.removeFishPuff),
                BoolState.capture(config.removeSquidFlee),
                BoolState.capture(config.removeRandomMove)
        );

        List<String> replaceLookList = config.replaceLookController.filterList().get();
        List<String> removeLookList = config.removeLookGoal.filterList().get();

        try {
            // Use known defaults independent of the machine's config file.
            config.allowRemoveCalls.set(true);
            config.replaceLookController.configValue().set(false);
            config.replaceLookController.isAllowlist().set(false);
            config.replaceLookController.filterList().set(List.of());
            config.removeLookGoal.configValue().set(false);
            config.removeLookGoal.isAllowlist().set(false);
            config.removeLookGoal.filterList().set(List.of());

            config.cow.removeFloat().set(false);
            config.cow.removeBreed().set(false);
            config.removeSheepEatBlock.set(false);

            config.removeFishSwim.set(false);
            config.removeFishFollowLeader.set(false);
            config.removeFishPuff.set(false);

            config.removeSquidFlee.set(false);
            config.removeRandomMove.set(false);

            // Vanilla baseline: these goals exist in Minecraft 26.3 and must
            // remain untouched when their matching options are disabled.
            Mob baselineCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(hasGoal(baselineCow, FloatGoal.class),
                    "Baseline cow must have FloatGoal");
            helper.assertTrue(hasGoal(baselineCow, BreedGoal.class),
                    "Baseline cow must have BreedGoal");
            helper.assertTrue(baselineCow.getLookControl().getClass() == LookControl.class,
                    "LookControl replacement must be disabled in this baseline");
            helper.discard(baselineCow);

            // remove_float removes FloatGoal only; BreedGoal remains.
            config.cow.removeFloat().set(true);
            Mob floatRemovedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertFalse(hasGoal(floatRemovedCow, FloatGoal.class),
                    "Cow remove_float must remove FloatGoal");
            helper.assertTrue(hasGoal(floatRemovedCow, BreedGoal.class),
                    "Cow remove_float must preserve BreedGoal");
            helper.discard(floatRemovedCow);

            // The global guard overrides individual removal flags.
            config.allowRemoveCalls.set(false);
            Mob guardedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(hasGoal(guardedCow, FloatGoal.class),
                    "allow_remove_calls=false must preserve FloatGoal");
            helper.discard(guardedCow);
            config.allowRemoveCalls.set(true);

            // Allowlist: only entity IDs explicitly listed are modified.
            config.removeLookGoal.configValue().set(true);
            config.removeLookGoal.isAllowlist().set(true);
            config.removeLookGoal.filterList().set(List.of("minecraft:cow"));

            Mob allowlistedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertFalse(hasGoal(allowlistedCow, LookAtPlayerGoal.class),
                    "Allowlisted cow must lose LookAtPlayerGoal");
            helper.assertTrue(hasGoal(allowlistedCow, RandomLookAroundGoal.class),
                    "Removing LookAtPlayerGoal must preserve RandomLookAroundGoal");
            helper.discard(allowlistedCow);

            Mob nonAllowlistedSheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertTrue(hasGoal(nonAllowlistedSheep, LookAtPlayerGoal.class),
                    "Entity outside the allowlist must retain LookAtPlayerGoal");
            helper.discard(nonAllowlistedSheep);

            // Denylist: listed entity IDs are excluded, all other IDs are modified.
            config.removeLookGoal.isAllowlist().set(false);

            Mob deniedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(hasGoal(deniedCow, LookAtPlayerGoal.class),
                    "Denylisted cow must retain LookAtPlayerGoal");
            helper.discard(deniedCow);

            Mob nonDeniedSheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertFalse(hasGoal(nonDeniedSheep, LookAtPlayerGoal.class),
                    "Entity outside the denylist must lose LookAtPlayerGoal");
            helper.assertTrue(hasGoal(nonDeniedSheep, RandomLookAroundGoal.class),
                    "Removing LookAtPlayerGoal must preserve RandomLookAroundGoal");
            helper.discard(nonDeniedSheep);

            config.removeLookGoal.configValue().set(false);

            // Controller replacement applies to vanilla LookControl, while
            // entity filters decide exactly which mob types are changed.
            config.replaceLookController.configValue().set(true);
            config.replaceLookController.isAllowlist().set(false);
            config.replaceLookController.filterList().set(List.of());

            Mob replacedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(replacedCow.getLookControl() instanceof FixedLookControl,
                    "Enabled LookControl optimization must install FixedLookControl");
            helper.discard(replacedCow);

            config.replaceLookController.isAllowlist().set(true);
            config.replaceLookController.filterList().set(List.of("minecraft:sheep"));

            Mob excludedCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(excludedCow.getLookControl().getClass() == LookControl.class,
                    "Cow outside the LookControl allowlist must keep vanilla LookControl");
            helper.discard(excludedCow);

            Mob allowlistedSheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertTrue(allowlistedSheep.getLookControl() instanceof FixedLookControl,
                    "Sheep in the LookControl allowlist must receive FixedLookControl");
            helper.discard(allowlistedSheep);

            // Pufferfish puff and fish swimming are separate settings.
            config.replaceLookController.configValue().set(false);
            config.removeFishPuff.set(true);
            config.removeFishSwim.set(false);

            Mob puffRemoved = helper.spawn(EntityTypes.PUFFERFISH, BlockPos.ZERO);
            helper.assertFalse(hasGoal(puffRemoved, Pufferfish.PufferfishPuffGoal.class),
                    "remove_puff must remove PufferfishPuffGoal");
            helper.assertTrue(hasGoal(puffRemoved, RandomSwimmingGoal.class),
                    "remove_puff must preserve the fish swimming goal");
            helper.discard(puffRemoved);

            config.removeFishPuff.set(false);
            config.removeFishSwim.set(true);

            Mob swimRemoved = helper.spawn(EntityTypes.PUFFERFISH, BlockPos.ZERO);
            helper.assertTrue(hasGoal(swimRemoved, Pufferfish.PufferfishPuffGoal.class),
                    "remove_swim must preserve PufferfishPuffGoal");
            helper.assertFalse(hasGoal(swimRemoved, RandomSwimmingGoal.class),
                    "remove_swim must remove the inherited FishSwimGoal");
            helper.discard(swimRemoved);

            // Squid flee and random movement are independent.
            config.removeFishSwim.set(false);
            config.removeSquidFlee.set(true);
            config.removeRandomMove.set(false);

            Mob fleeRemoved = helper.spawn(EntityTypes.SQUID, BlockPos.ZERO);
            helper.assertFalse(hasGoal(fleeRemoved, Squid.SquidFleeGoal.class),
                    "remove_flee must remove SquidFleeGoal");
            helper.assertTrue(hasGoal(fleeRemoved, Squid.SquidRandomMovementGoal.class),
                    "remove_flee must preserve SquidRandomMovementGoal");
            helper.discard(fleeRemoved);

            config.removeSquidFlee.set(false);
            config.removeRandomMove.set(true);

            Mob randomMoveRemoved = helper.spawn(EntityTypes.SQUID, BlockPos.ZERO);
            helper.assertTrue(hasGoal(randomMoveRemoved, Squid.SquidFleeGoal.class),
                    "remove_random_move must preserve SquidFleeGoal");
            helper.assertFalse(hasGoal(randomMoveRemoved, Squid.SquidRandomMovementGoal.class),
                    "remove_random_move must remove SquidRandomMovementGoal");
            helper.discard(randomMoveRemoved);

            // Sheep's block-eating option must not remove other animal goals.
            config.removeRandomMove.set(false);
            config.removeSheepEatBlock.set(true);

            Mob sheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertFalse(hasGoal(sheep, EatBlockGoal.class),
                    "remove_eat_block must remove Sheep EatBlockGoal");
            helper.assertTrue(hasGoal(sheep, FloatGoal.class),
                    "remove_eat_block must preserve Sheep FloatGoal");
            helper.discard(sheep);
        } finally {
            for (BoolState state : states) {
                state.restore();
            }
            config.replaceLookController.filterList().set(replaceLookList);
            config.removeLookGoal.filterList().set(removeLookList);
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
