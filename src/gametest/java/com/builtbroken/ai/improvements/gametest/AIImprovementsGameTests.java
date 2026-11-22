package com.builtbroken.ai.improvements.gametest;

import java.lang.reflect.Method;
import java.util.List;

import com.builtbroken.ai.improvements.ConfigMain;
import com.builtbroken.ai.improvements.ConfigMain.AnimalConfigSection;
import com.builtbroken.ai.improvements.FixedLookControl;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
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
                BoolState.capture(config.removeLookRandom.configValue()),
                BoolState.capture(config.removeLookRandom.isAllowlist()),
                BoolState.capture(config.cow.removeFloat()),
                BoolState.capture(config.cow.removePanic()),
                BoolState.capture(config.cow.removeBreed()),
                BoolState.capture(config.cow.removeTempt()),
                BoolState.capture(config.cow.removeFollowParent()),
                BoolState.capture(config.cow.removeStroll()),
                BoolState.capture(config.chicken.removeFloat()),
                BoolState.capture(config.chicken.removePanic()),
                BoolState.capture(config.chicken.removeBreed()),
                BoolState.capture(config.chicken.removeTempt()),
                BoolState.capture(config.chicken.removeFollowParent()),
                BoolState.capture(config.chicken.removeStroll()),
                BoolState.capture(config.pig.removeFloat()),
                BoolState.capture(config.pig.removePanic()),
                BoolState.capture(config.pig.removeBreed()),
                BoolState.capture(config.pig.removeTempt()),
                BoolState.capture(config.pig.removeFollowParent()),
                BoolState.capture(config.pig.removeStroll()),
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

        List<String> replaceLookList = config.replaceLookController.filterList().get();
        List<String> removeLookList = config.removeLookGoal.filterList().get();
        List<String> removeLookRandomList = config.removeLookRandom.filterList().get();

        try {
            // Use known defaults independent of the machine's config file.
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

            disableAnimalOptions(config.cow);
            disableAnimalOptions(config.chicken);
            disableAnimalOptions(config.pig);
            disableAnimalOptions(config.sheep);
            config.removeSheepEatBlock.set(false);

            config.removeFishSwim.set(false);
            config.removeFishAvoidPlayer.set(false);
            config.removeFishPanic.set(false);
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

            // The random-look option is independent from looking at players.
            config.removeLookRandom.configValue().set(true);
            config.removeLookRandom.isAllowlist().set(true);
            config.removeLookRandom.filterList().set(List.of("minecraft:sheep"));

            Mob randomLookSheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertFalse(hasGoal(randomLookSheep, RandomLookAroundGoal.class),
                    "Allowlisted sheep must lose RandomLookAroundGoal");
            helper.assertTrue(hasGoal(randomLookSheep, LookAtPlayerGoal.class),
                    "Removing RandomLookAroundGoal must preserve LookAtPlayerGoal");
            helper.discard(randomLookSheep);

            Mob randomLookCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(hasGoal(randomLookCow, RandomLookAroundGoal.class),
                    "Entity outside remove_look_random allowlist must retain the goal");
            helper.discard(randomLookCow);
            config.removeLookRandom.configValue().set(false);

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

            // Compare the actual yaw/pitch output of the optimized controller
            // with vanilla at several horizontal and vertical target angles.
            config.replaceLookController.filterList().set(List.of("minecraft:sheep"));
            Mob vanillaRotationCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            config.replaceLookController.filterList().set(List.of("minecraft:cow"));
            Mob fixedRotationCow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
            helper.assertTrue(vanillaRotationCow.getLookControl().getClass() == LookControl.class,
                    "Rotation reference cow must retain vanilla LookControl");
            helper.assertTrue(fixedRotationCow.getLookControl() instanceof FixedLookControl,
                    "Rotation comparison cow must use FixedLookControl");
            verifyLookControllerParity(helper, vanillaRotationCow, fixedRotationCow);
            helper.discard(vanillaRotationCow);
            helper.discard(fixedRotationCow);

            // Cover all six options for all four farm-animal config sections.
            // Every case confirms the goal exists before checking its removal.
            verifyAnimalOptions(helper, EntityTypes.COW, config.cow, "Cow");
            verifyAnimalOptions(helper, EntityTypes.CHICKEN, config.chicken, "Chicken");
            verifyAnimalOptions(helper, EntityTypes.PIG, config.pig, "Pig");
            verifyAnimalOptions(helper, EntityTypes.SHEEP, config.sheep, "Sheep");

            // Fish options are independent for the shared AbstractFish editor.
            config.replaceLookController.configValue().set(false);
            config.removeFishAvoidPlayer.set(true);
            config.removeFishPanic.set(false);
            Mob avoidRemovedFish = helper.spawn(EntityTypes.PUFFERFISH, BlockPos.ZERO);
            helper.assertFalse(hasGoal(avoidRemovedFish, net.minecraft.world.entity.ai.goal.AvoidEntityGoal.class),
                    "remove_avoid_player must remove the fish avoid goal");
            helper.assertTrue(hasGoal(avoidRemovedFish, PanicGoal.class),
                    "remove_avoid_player must preserve fish PanicGoal");
            helper.discard(avoidRemovedFish);

            config.removeFishAvoidPlayer.set(false);
            config.removeFishPanic.set(true);
            Mob panicRemovedFish = helper.spawn(EntityTypes.PUFFERFISH, BlockPos.ZERO);
            helper.assertTrue(hasGoal(panicRemovedFish, net.minecraft.world.entity.ai.goal.AvoidEntityGoal.class),
                    "remove_panic must preserve the fish avoid goal");
            helper.assertFalse(hasGoal(panicRemovedFish, PanicGoal.class),
                    "remove_panic must remove fish PanicGoal");
            helper.discard(panicRemovedFish);

            // Schooling fish use FollowFlockLeaderGoal through the
            // AbstractSchoolingFish hierarchy; verify the dedicated option
            // against a real school-capable species, not a pufferfish.
            config.removeFishPanic.set(false);
            Mob baselineSchoolFish = helper.spawn(EntityTypes.SALMON, BlockPos.ZERO);
            helper.assertTrue(hasGoal(baselineSchoolFish, FollowFlockLeaderGoal.class),
                    "Baseline salmon must have FollowFlockLeaderGoal");
            helper.discard(baselineSchoolFish);

            config.removeFishFollowLeader.set(true);
            Mob schoolFish = helper.spawn(EntityTypes.SALMON, BlockPos.ZERO);
            helper.assertFalse(hasGoal(schoolFish, FollowFlockLeaderGoal.class),
                    "remove_follow_leader must remove the schooling fish leader goal");
            helper.discard(schoolFish);
            config.removeFishFollowLeader.set(false);

            // Pufferfish puff and fish swimming are separate settings.
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
            config.removeFishAvoidPlayer.set(false);
            config.removeFishPanic.set(false);
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
            Mob baselineSheep = helper.spawn(EntityTypes.SHEEP, BlockPos.ZERO);
            helper.assertTrue(hasGoal(baselineSheep, EatBlockGoal.class),
                    "Baseline sheep must have EatBlockGoal");
            helper.discard(baselineSheep);
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
            config.removeLookRandom.filterList().set(removeLookRandomList);
        }

        helper.succeed();
    }

    private static void verifyLookControllerParity(
            GameTestHelper helper, Mob vanillaMob, Mob fixedMob) {
        double[][] targetOffsets = {
                {0.0, 0.0, 8.0},
                {4.0, 0.0, 8.0},
                {-4.0, 0.0, 8.0},
                {4.0, 3.0, 8.0},
                {-4.0, -3.0, 8.0},
                {0.0, 5.0, 8.0},
                {0.0, -5.0, 8.0},
                {1.0, 2.0, 12.0},
                {-1.0, -2.0, 12.0}
        };

        for (int i = 0; i < targetOffsets.length; i++) {
            double dx = targetOffsets[i][0];
            double dy = targetOffsets[i][1];
            double dz = targetOffsets[i][2];

            vanillaMob.getLookControl().setLookAt(
                    vanillaMob.getX() + dx,
                    vanillaMob.getY() + vanillaMob.getEyeHeight() + dy,
                    vanillaMob.getZ() + dz,
                    180.0F,
                    180.0F);
            fixedMob.getLookControl().setLookAt(
                    fixedMob.getX() + dx,
                    fixedMob.getY() + fixedMob.getEyeHeight() + dy,
                    fixedMob.getZ() + dz,
                    180.0F,
                    180.0F);

            vanillaMob.getLookControl().tick();
            fixedMob.getLookControl().tick();

            float yawError = wrappedAngleDifference(
                    vanillaMob.getYHeadRot(), fixedMob.getYHeadRot());
            float pitchError = Math.abs(vanillaMob.getXRot() - fixedMob.getXRot());

            helper.assertTrue(yawError <= 0.75F,
                    "FixedLookControl yaw differs from vanilla at target sample " + i
                            + " by " + yawError + " degrees");
            helper.assertTrue(pitchError <= 0.75F,
                    "FixedLookControl pitch differs from vanilla at target sample " + i
                            + " by " + pitchError + " degrees");
        }
    }

    private static float wrappedAngleDifference(float left, float right) {
        float difference = (left - right) % 360.0F;
        if (difference > 180.0F) {
            difference -= 360.0F;
        } else if (difference < -180.0F) {
            difference += 360.0F;
        }
        return Math.abs(difference);
    }

    private static void disableAnimalOptions(AnimalConfigSection section) {
        section.removeFloat().set(false);
        section.removePanic().set(false);
        section.removeBreed().set(false);
        section.removeTempt().set(false);
        section.removeFollowParent().set(false);
        section.removeStroll().set(false);
    }

    private static void verifyAnimalOptions(
            GameTestHelper helper,
            EntityType<? extends Mob> entityType,
            AnimalConfigSection section,
            String animalName) {
        verifyAnimalGoalRemoval(helper, entityType, section.removeFloat(),
                FloatGoal.class, animalName + " remove_float");
        verifyAnimalGoalRemoval(helper, entityType, section.removePanic(),
                PanicGoal.class, animalName + " remove_panic");
        verifyAnimalGoalRemoval(helper, entityType, section.removeBreed(),
                BreedGoal.class, animalName + " remove_breed");
        verifyAnimalGoalRemoval(helper, entityType, section.removeTempt(),
                TemptGoal.class, animalName + " remove_tempt");
        verifyAnimalGoalRemoval(helper, entityType, section.removeFollowParent(),
                FollowParentGoal.class, animalName + " remove_follow_parent");
        verifyAnimalGoalRemoval(helper, entityType, section.removeStroll(),
                WaterAvoidingRandomStrollGoal.class, animalName + " remove_stroll");
    }

    private static void verifyAnimalGoalRemoval(
            GameTestHelper helper,
            EntityType<? extends Mob> entityType,
            ConfigMain.BooleanValue option,
            Class<? extends Goal> goalClass,
            String settingName) {
        option.set(false);
        Mob baseline = helper.spawn(entityType, BlockPos.ZERO);
        helper.assertTrue(hasGoal(baseline, goalClass),
                "Baseline " + settingName + " must contain " + goalClass.getSimpleName());
        helper.discard(baseline);

        option.set(true);
        Mob modified = helper.spawn(entityType, BlockPos.ZERO);
        helper.assertFalse(hasGoal(modified, goalClass),
                settingName + " must remove " + goalClass.getSimpleName());
        helper.discard(modified);

        option.set(false);
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
