package com.builtbroken.ai.improvements.modifier;

import com.builtbroken.ai.improvements.AIImprovementsFabric;
import com.builtbroken.ai.improvements.ConfigMain;
import com.builtbroken.ai.improvements.ConfigMain.AnimalConfigSection;
import com.builtbroken.ai.improvements.ConfigMain.FilteredConfigValue;
import com.builtbroken.ai.improvements.FixedLookControl;
import com.builtbroken.ai.improvements.modifier.editor.FilteredRemove;
import com.builtbroken.ai.improvements.modifier.editor.GenericRemove;
import com.builtbroken.ai.improvements.modifier.filters.FilterLayer;
import com.builtbroken.ai.improvements.modifier.filters.FilterResult;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.squid.Squid;

/** Builds and applies the original AI goal-filter pipeline on the logical server. */
public final class ModifierSystem {
    public static final FilterLayer editor = new FilterLayer(null);

    public static final ModifierLevel mobEntityEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Mob);
    public static final ModifierLevel fishEditor =
            ModifierLevel.newFilter(entity -> entity instanceof AbstractFish);
    public static final ModifierLevel squidEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Squid);
    public static final ModifierLevel cowEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Cow);
    public static final ModifierLevel chickenEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Chicken);
    public static final ModifierLevel pigEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Pig);
    public static final ModifierLevel sheepEditor =
            ModifierLevel.newFilter(entity -> entity instanceof Sheep);

    private static boolean initialized;

    private ModifierSystem() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        editor.add(mobEntityEditor);

        // Generic mob goal removal and look-controller optimization.
        mobEntityEditor.goalEditor.add(new FilteredRemove(
                goal -> goal instanceof LookAtPlayerGoal, ConfigMain.CONFIG.removeLookGoal));
        mobEntityEditor.goalEditor.add(new FilteredRemove(
                goal -> goal instanceof RandomLookAroundGoal, ConfigMain.CONFIG.removeLookRandom));
        mobEntityEditor.filters.add(entity -> replaceLookHelper((Mob) entity));

        // Fish
        mobEntityEditor.filters.add(fishEditor);
        fishEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof RandomSwimmingGoal, ConfigMain.CONFIG.removeFishSwim));
        fishEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof AvoidEntityGoal, ConfigMain.CONFIG.removeFishAvoidPlayer));
        fishEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof PanicGoal, ConfigMain.CONFIG.removeFishPanic));
        fishEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof FollowFlockLeaderGoal, ConfigMain.CONFIG.removeFishFollowLeader));
        fishEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof Pufferfish.PufferfishPuffGoal, ConfigMain.CONFIG.removeFishPuff));

        // Squid
        mobEntityEditor.filters.add(squidEditor);
        squidEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof Squid.SquidRandomMovementGoal, ConfigMain.CONFIG.removeRandomMove));
        squidEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof Squid.SquidFleeGoal, ConfigMain.CONFIG.removeSquidFlee));

        // Farm animals
        mobEntityEditor.filters.add(cowEditor);
        populateAnimalEditor(cowEditor, ConfigMain.CONFIG.cow);

        mobEntityEditor.filters.add(chickenEditor);
        populateAnimalEditor(chickenEditor, ConfigMain.CONFIG.chicken);

        mobEntityEditor.filters.add(pigEditor);
        populateAnimalEditor(pigEditor, ConfigMain.CONFIG.pig);

        mobEntityEditor.filters.add(sheepEditor);
        populateAnimalEditor(sheepEditor, ConfigMain.CONFIG.sheep);
        sheepEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof EatBlockGoal, ConfigMain.CONFIG.removeSheepEatBlock));
    }

    public static void handleEntityJoinLevel(Entity entity, ServerLevel level) {
        if (initialized && entity instanceof Mob) {
            editor.handle(entity);
        }
    }

    private static FilterResult replaceLookHelper(Mob living) {
        FilteredConfigValue config = ConfigMain.CONFIG.replaceLookController;

        if (config.configValue().get()
                && (living.getLookControl() == null
                || living.getLookControl().getClass() == LookControl.class)) {

            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
            if (config.isFiltered(entityId)) {
                return FilterResult.DID_NOTHING;
            }

            LookControl oldHelper = living.getLookControl();
            living.lookControl = new FixedLookControl(living);

            if (living.getLookControl() instanceof FixedLookControl replacement) {
                if (oldHelper != null) {
                    replacement.copyDataIntoSelf(oldHelper);
                }
                return FilterResult.MODIFIED;
            }

            AIImprovementsFabric.LOGGER.warn(
                    "Could not install FixedLookControl for entity type {}.", entityId);
        }

        return FilterResult.DID_NOTHING;
    }

    private static void populateAnimalEditor(ModifierLevel animalEditor, AnimalConfigSection configs) {
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof FloatGoal, configs.removeFloat()));
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof PanicGoal, configs.removePanic()));
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof BreedGoal, configs.removeBreed()));
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof TemptGoal, configs.removeTempt()));
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof FollowParentGoal, configs.removeFollowParent()));
        animalEditor.goalEditor.add(new GenericRemove(
                goal -> goal instanceof WaterAvoidingRandomStrollGoal, configs.removeStroll()));
    }
}
