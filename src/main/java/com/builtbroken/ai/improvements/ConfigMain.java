package com.builtbroken.ai.improvements;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric-side configuration replacing NeoForge ModConfigSpec.
 *
 * <p>Option names and default values are based on the original 26.3 source.
 * Configuration is stored at config/aiimprovements.json.
 */
public final class ConfigMain {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("aiimprovements.json");

    public static final ConfigMain CONFIG = new ConfigMain();

    public static final class BooleanValue {
        private final boolean defaultValue;
        private boolean value;

        private BooleanValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public boolean get() {
            return value;
        }

        public void set(boolean value) {
            this.value = value;
        }

        public boolean defaultValue() {
            return defaultValue;
        }
    }

    public static final class StringListValue {
        private List<String> value = List.of();

        public List<String> get() {
            return value;
        }

        public void set(List<String> value) {
            this.value = List.copyOf(value);
        }
    }

    public record AnimalConfigSection(
            BooleanValue removeFloat,
            BooleanValue removePanic,
            BooleanValue removeBreed,
            BooleanValue removeTempt,
            BooleanValue removeFollowParent,
            BooleanValue removeStroll) {
    }

    public record FilteredConfigValue(
            BooleanValue configValue,
            BooleanValue isAllowlist,
            StringListValue filterList) {
        public boolean isFiltered(String entityId) {
            return isAllowlist.get() != filterList.get().contains(entityId);
        }
    }

    // Global
    public final BooleanValue allowRemoveCalls = bool(true);
    public final BooleanValue enableCallBubbling = bool(true);

    // Generic mobs
    public final FilteredConfigValue removeLookGoal = filtered(false);
    public final FilteredConfigValue removeLookRandom = filtered(false);
    public final FilteredConfigValue replaceLookController = filtered(true);

    // Fish
    public final BooleanValue removeFishSwim = bool(false);
    public final BooleanValue removeFishAvoidPlayer = bool(false);
    public final BooleanValue removeFishPanic = bool(false);
    public final BooleanValue removeFishFollowLeader = bool(false);
    public final BooleanValue removeFishPuff = bool(false);

    // Squid
    public final BooleanValue removeSquidFlee = bool(false);
    public final BooleanValue removeRandomMove = bool(false);

    // Animals
    public final AnimalConfigSection cow = animal();
    public final AnimalConfigSection chicken = animal();
    public final AnimalConfigSection pig = animal();
    public final AnimalConfigSection sheep = animal();
    public final BooleanValue removeSheepEatBlock = bool(false);

    private ConfigMain() {
    }

    private static BooleanValue bool(boolean defaultValue) {
        return new BooleanValue(defaultValue);
    }

    private static FilteredConfigValue filtered(boolean defaultValue) {
        return new FilteredConfigValue(bool(defaultValue), bool(false), new StringListValue());
    }

    private static AnimalConfigSection animal() {
        return new AnimalConfigSection(
                bool(false), bool(false), bool(false),
                bool(false), bool(false), bool(false));
    }

    public static void load() {
        CONFIG.loadFromDisk();
    }

    public static void save() {
        CONFIG.saveToDisk();
    }

    private void loadFromDisk() {
        if (!Files.exists(CONFIG_FILE)) {
            saveToDisk();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("Root JSON value must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();

            readBoolean(root, "entity.general.allow_remove_calls", allowRemoveCalls);
            readBoolean(root, "entity.general.enable_call_bubbling", enableCallBubbling);

            readFiltered(root, "entity.mob.remove_look_goal", removeLookGoal);
            readFiltered(root, "entity.mob.remove_look_random", removeLookRandom);
            readFiltered(root, "entity.mob.replace_look_controller", replaceLookController);

            readBoolean(root, "entity.fish.remove_swim", removeFishSwim);
            readBoolean(root, "entity.fish.remove_panic", removeFishPanic);
            readBoolean(root, "entity.fish.remove_avoid_player", removeFishAvoidPlayer);
            readBoolean(root, "entity.fish.remove_follow_leader", removeFishFollowLeader);
            readBoolean(root, "entity.fish.remove_puff", removeFishPuff);

            readBoolean(root, "entity.squid.remove_flee", removeSquidFlee);
            readBoolean(root, "entity.squid.remove_random_move", removeRandomMove);

            readAnimal(root, "entity.cow", cow);
            readAnimal(root, "entity.chicken", chicken);
            readAnimal(root, "entity.pig", pig);
            readAnimal(root, "entity.sheep", sheep);
            readBoolean(root, "entity.sheep.remove_eat_block", removeSheepEatBlock);
        } catch (Exception exception) {
            AIImprovementsFabric.LOGGER.error(
                    "Could not read config/aiimprovements.json. Defaults remain active.", exception);
        }
    }

    private void saveToDisk() {
        JsonObject root = new JsonObject();

        writeBoolean(root, "entity.general.allow_remove_calls", allowRemoveCalls);
        writeBoolean(root, "entity.general.enable_call_bubbling", enableCallBubbling);

        writeFiltered(root, "entity.mob.remove_look_goal", removeLookGoal);
        writeFiltered(root, "entity.mob.remove_look_random", removeLookRandom);
        writeFiltered(root, "entity.mob.replace_look_controller", replaceLookController);

        writeBoolean(root, "entity.fish.remove_swim", removeFishSwim);
        writeBoolean(root, "entity.fish.remove_panic", removeFishPanic);
        writeBoolean(root, "entity.fish.remove_avoid_player", removeFishAvoidPlayer);
        writeBoolean(root, "entity.fish.remove_follow_leader", removeFishFollowLeader);
        writeBoolean(root, "entity.fish.remove_puff", removeFishPuff);

        writeBoolean(root, "entity.squid.remove_flee", removeSquidFlee);
        writeBoolean(root, "entity.squid.remove_random_move", removeRandomMove);

        writeAnimal(root, "entity.cow", cow);
        writeAnimal(root, "entity.chicken", chicken);
        writeAnimal(root, "entity.pig", pig);
        writeAnimal(root, "entity.sheep", sheep);
        writeBoolean(root, "entity.sheep.remove_eat_block", removeSheepEatBlock);

        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException exception) {
            AIImprovementsFabric.LOGGER.error("Could not save config/aiimprovements.json.", exception);
        }
    }

    private static void readAnimal(JsonObject root, String path, AnimalConfigSection section) {
        readBoolean(root, path + ".remove_float", section.removeFloat());
        readBoolean(root, path + ".remove_panic", section.removePanic());
        readBoolean(root, path + ".remove_breed", section.removeBreed());
        readBoolean(root, path + ".remove_tempt", section.removeTempt());
        readBoolean(root, path + ".remove_follow_parent", section.removeFollowParent());
        readBoolean(root, path + ".remove_stroll", section.removeStroll());
    }

    private static void writeAnimal(JsonObject root, String path, AnimalConfigSection section) {
        writeBoolean(root, path + ".remove_float", section.removeFloat());
        writeBoolean(root, path + ".remove_panic", section.removePanic());
        writeBoolean(root, path + ".remove_breed", section.removeBreed());
        writeBoolean(root, path + ".remove_tempt", section.removeTempt());
        writeBoolean(root, path + ".remove_follow_parent", section.removeFollowParent());
        writeBoolean(root, path + ".remove_stroll", section.removeStroll());
    }

    private static void readFiltered(JsonObject root, String path, FilteredConfigValue value) {
        readBoolean(root, path + ".enabled", value.configValue());
        readBoolean(root, path + ".is_allowlist", value.isAllowlist());

        JsonElement element = getPath(root, path + ".filter_list");
        if (element != null && element.isJsonArray()) {
            List<String> strings = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    strings.add(item.getAsString());
                }
            }
            value.filterList().set(strings);
        }
    }

    private static void writeFiltered(JsonObject root, String path, FilteredConfigValue value) {
        writeBoolean(root, path + ".enabled", value.configValue());
        writeBoolean(root, path + ".is_allowlist", value.isAllowlist());
        JsonArray array = new JsonArray();
        value.filterList().get().forEach(array::add);
        putPath(root, path + ".filter_list", array);
    }

    private static void readBoolean(JsonObject root, String path, BooleanValue target) {
        JsonElement element = getPath(root, path);
        if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()) {
            target.set(element.getAsBoolean());
        }
    }

    private static void writeBoolean(JsonObject root, String path, BooleanValue value) {
        putPath(root, path, new com.google.gson.JsonPrimitive(value.get()));
    }

    private static JsonElement getPath(JsonObject root, String path) {
        JsonObject current = root;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            JsonElement next = current.get(parts[i]);
            if (next == null) {
                return null;
            }
            if (i == parts.length - 1) {
                return next;
            }
            if (!next.isJsonObject()) {
                return null;
            }
            current = next.getAsJsonObject();
        }
        return null;
    }

    private static void putPath(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = current.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                JsonObject child = new JsonObject();
                current.add(parts[i], child);
                current = child;
            } else {
                current = next.getAsJsonObject();
            }
        }
        current.add(parts[parts.length - 1], value);
    }
}
