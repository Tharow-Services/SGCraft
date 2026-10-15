package gcewing.sg.util;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gcewing.sg.BaseConfiguration;
import gcewing.sg.BaseReflectionUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes SGCraft's chest loot tables configurable from sgcraft.cfg without touching the JSON files.
 *
 * <p>The JSON under /assets/sgcraft/loot_tables/ holds the defaults. At startup each table gets a "loot.&lt;table&gt;"
 * config category with its pools' chance and rolls and its items' weight and count, defaulting to the JSON values.
 * When Minecraft loads the table, the JSON is re-parsed with the configured values swapped in. Pool and entry names
 * stay fixed, so LootTweaker and similar mods can still edit the result.</p>
 */
public final class LootTableConfig {

    private static final Field GSON_FIELD = BaseReflectionUtils.getFieldDef(LootTableManager.class, "GSON_INSTANCE", "field_186526_b");
    private static final Field TABLE_POOLS_FIELD = BaseReflectionUtils.getFieldDef(LootTable.class, "pools", "field_186466_c");
    // Forge-added fields, so they have the same name in dev and in production.
    private static final Field TABLE_FROZEN_FIELD = BaseReflectionUtils.getFieldDef(LootTable.class, "isFrozen", "isFrozen");
    private static final Field POOL_FROZEN_FIELD = BaseReflectionUtils.getFieldDef(LootPool.class, "isFrozen", "isFrozen");

    // Configured JSON for each of our tables, by table name (e.g. sgcraft:chests/desert_pyramid).
    private static final Map<ResourceLocation, String> configuredTables = new HashMap<>();

    private LootTableConfig() {
    }

    /** Reads (and, for new keys, writes) the loot options for every SGCraft table that extends a vanilla table. */
    public static void load(BaseConfiguration config, String assetKey) {
        configuredTables.clear();
        // Superseded by loot.<table>.chance; drop them so they don't look like they still do something.
        for (String old : new String[] {"addressBookChance_abandoned_mineshaft", "addressBookChance_desert_pyramid", "addressBookChance_jungle_temple"}) {
            if (config.hasKey("loot", old)) {
                config.getCategory("loot").remove(old);
            }
        }
        config.setCategoryComment("loot", "Chest loot. Each table's values default to SGCraft's built-in loot table. "
                + "chance: 0.0-1.0 chance the pool is used at all. rolls_min/rolls_max: how many items the pool picks. "
                + "<item>_weight: relative chance of that item being picked (0 removes it). "
                + "<item>_count_min/_count_max: stack size.");

        for (ResourceLocation vanilla : LootTableList.getAll()) {
            if (!vanilla.getNamespace().equals("minecraft")) {
                continue;
            }
            URL url = LootTableConfig.class.getResource(String.format("/assets/%s/loot_tables/%s.json", assetKey, vanilla.getPath()));
            if (url == null) {
                continue;
            }
            try {
                JsonObject table = new JsonParser().parse(Resources.toString(url, Charsets.UTF_8)).getAsJsonObject();
                String category = "loot." + vanilla.getPath().replaceFirst("^chests/", "");
                configure(config, category, table);
                configuredTables.put(new ResourceLocation(assetKey, vanilla.getPath()), table.toString());
            } catch (Exception e) {
                throw new RuntimeException("Error reading loot table " + url + ": " + e, e);
            }
        }
    }

    private static void configure(BaseConfiguration config, String category, JsonObject table) {
        JsonArray pools = table.getAsJsonArray("pools");
        for (int p = 0; p < pools.size(); p++) {
            JsonObject pool = pools.get(p).getAsJsonObject();
            String prefix = pools.size() > 1 ? "pool" + (p + 1) + "_" : "";
            if (!pool.has("name")) {
                pool.addProperty("name", category.replace('.', '_') + "_" + (p + 1));
            }

            if (pool.has("conditions")) {
                for (JsonElement element : pool.getAsJsonArray("conditions")) {
                    JsonObject condition = element.getAsJsonObject();
                    if (condition.get("condition").getAsString().replaceFirst("^minecraft:", "").equals("random_chance")) {
                        double chance = config.get(category, prefix + "chance", condition.get("chance").getAsDouble(),
                                "Chance (0.0-1.0) that this pool adds anything to the chest").getDouble();
                        condition.addProperty("chance", Math.max(0.0, Math.min(1.0, chance)));
                    }
                }
            }

            int[] rolls = range(pool.get("rolls"));
            int rollsMin = Math.max(0, config.get(category, prefix + "rolls_min", rolls[0], "Fewest items this pool picks").getInt());
            int rollsMax = Math.max(rollsMin, config.get(category, prefix + "rolls_max", rolls[1], "Most items this pool picks").getInt());
            pool.add("rolls", rangeJson(rollsMin, rollsMax));

            Set<String> usedKeys = new HashSet<>();
            for (JsonElement element : pool.getAsJsonArray("entries")) {
                JsonObject entry = element.getAsJsonObject();
                if (!"item".equals(entry.get("type").getAsString())) {
                    continue;
                }
                String item = entry.get("name").getAsString();
                String key = item.substring(item.indexOf(':') + 1).toLowerCase();
                for (int n = 2; !usedKeys.add(key); n++) {
                    key = key.replaceFirst("_\\d+$", "") + "_" + n;
                }
                // Explicit names keep Forge from giving the entries hash-based names when we re-parse the table.
                if (!entry.has("entryName")) {
                    entry.addProperty("entryName", key);
                }

                int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                entry.addProperty("weight", Math.max(0, config.get(category, prefix + key + "_weight", weight,
                        "Relative chance of " + item + " being picked; 0 removes it").getInt()));

                if (entry.has("functions")) {
                    for (JsonElement f : entry.getAsJsonArray("functions")) {
                        JsonObject function = f.getAsJsonObject();
                        if (function.get("function").getAsString().replaceFirst("^minecraft:", "").equals("set_count")) {
                            int[] count = range(function.get("count"));
                            int min = Math.max(1, config.get(category, prefix + key + "_count_min", count[0], "Smallest stack of " + item).getInt());
                            int max = Math.max(min, config.get(category, prefix + key + "_count_max", count[1], "Largest stack of " + item).getInt());
                            function.add("count", rangeJson(min, max));
                        }
                    }
                }
            }
        }
    }

    private static int[] range(JsonElement value) {
        if (value == null) {
            return new int[] {1, 1};
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            return new int[] {object.get("min").getAsInt(), object.get("max").getAsInt()};
        }
        int n = value.getAsInt();
        return new int[] {n, n};
    }

    private static JsonObject rangeJson(int min, int max) {
        JsonObject object = new JsonObject();
        object.addProperty("min", min);
        object.addProperty("max", max);
        return object;
    }

    // Runs first, so later listeners (LootTweaker etc.) edit the configured table, not the built-in one.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLootTableLoad(LootTableLoadEvent event) {
        String json = configuredTables.get(event.getName());
        if (json == null) {
            return;
        }
        // "custom" parsing skips firing this event again; our explicit pool and entry names keep the names stable.
        LootTable table = ForgeHooks.loadLootTable((Gson) BaseReflectionUtils.getField(null, GSON_FIELD), event.getName(), json, true, event.getLootTableManager());
        // That parse freezes the table; thaw it so the remaining listeners can still change it. Forge freezes it again
        // once the event is over.
        BaseReflectionUtils.setField(table, TABLE_FROZEN_FIELD, false);
        for (Object pool : (List<?>) BaseReflectionUtils.getField(table, TABLE_POOLS_FIELD)) {
            BaseReflectionUtils.setField(pool, POOL_FROZEN_FIELD, false);
        }
        event.setTable(table);
    }
}
