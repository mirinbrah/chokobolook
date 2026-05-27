package player.chokobolook;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class LootTableManager {
    private static final double DEFAULT_NOTHING_CHANCE = 0.20;
    private static final List<String> DEFAULT_TABLES = List.of(
            "overworld.json",
            "the_end.json",
            "nether.json",
            "nether_wastes.json",
            "crimson_forest.json",
            "warped_forest.json",
            "soul_sand_valley.json",
            "basalt_deltas.json"
    );

    private final Chokobolook plugin;
    private final Path lootDirectory;

    public LootTableManager(Chokobolook plugin) {
        this.plugin = plugin;
        this.lootDirectory = plugin.getDataFolder().toPath().resolve("loot");
    }

    public void createDefaultTables() {
        try {
            Files.createDirectories(lootDirectory);
            for (String table : DEFAULT_TABLES) {
                copyTableIfMissing(table);
            }
            for (OverworldLootCategory category : OverworldLootCategory.values()) {
                copyTableIfMissing(category.tableName());
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not create default loot tables.", exception);
        }
    }

    public ItemStack roll(Biome biome, World.Environment environment) {
        JsonObject table = loadTable(tablePath(biome, environment));
        if (table == null || rollNothing(table)) {
            return null;
        }

        List<LootEntry> entries = readLootEntries(table);
        LootEntry entry = selectWeightedEntry(entries);
        if (entry == null) {
            return null;
        }
        return createItem(entry);
    }

    private boolean rollNothing(JsonObject table) {
        double chance = doubleValueOrDefault(table, "nothingChance", DEFAULT_NOTHING_CHANCE);
        return ThreadLocalRandom.current().nextDouble() < Math.clamp(chance, 0, 1);
    }

    private List<LootEntry> readLootEntries(JsonObject table) {
        List<LootEntry> entries = new ArrayList<>();
        for (JsonElement item : table.getAsJsonArray("items")) {
            LootEntry entry = readLootEntry(item.getAsJsonObject());
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private LootEntry readLootEntry(JsonObject definition) {
        Material material = Material.matchMaterial(definition.get("material").getAsString());
        int weight = intValueOrDefault(definition, "weight", 1);
        return material != null && weight > 0
                ? new LootEntry(material, definition, weight)
                : null;
    }

    private LootEntry selectWeightedEntry(List<LootEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }

        int totalWeight = entries.stream().mapToInt(LootEntry::weight).sum();
        int choice = ThreadLocalRandom.current().nextInt(totalWeight);
        for (LootEntry entry : entries) {
            choice -= entry.weight();
            if (choice < 0) {
                return entry;
            }
        }
        return null;
    }

    private ItemStack createItem(LootEntry entry) {
        ItemStack item = new ItemStack(entry.material(), randomAmount(entry.definition()));
        applyEnchantments(item, entry.definition());
        return item;
    }

    private int randomAmount(JsonObject definition) {
        int min = Math.max(1, intValueOrDefault(definition, "minAmount", 1));
        int max = Math.max(min, intValueOrDefault(definition, "maxAmount", min));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void applyEnchantments(ItemStack item, JsonObject definition) {
        if (!definition.has("enchantments")) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        for (Map.Entry<String, JsonElement> enchantmentEntry
                : definition.getAsJsonObject("enchantments").entrySet()) {
            Enchantment enchantment = findEnchantment(enchantmentEntry.getKey());
            if (enchantment != null) {
                addEnchantment(meta, enchantment, enchantmentEntry.getValue().getAsInt());
            }
        }
        item.setItemMeta(meta);
    }

    private Enchantment findEnchantment(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        return Registry.ENCHANTMENT.get(key);
    }

    private void addEnchantment(ItemMeta meta, Enchantment enchantment, int level) {
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            bookMeta.addStoredEnchant(enchantment, level, true);
            return;
        }
        meta.addEnchant(enchantment, level, true);
    }

    private Path tablePath(Biome biome, World.Environment environment) {
        String biomeName = biome.getKey().getKey() + ".json";
        Path biomeTable = lootDirectory.resolve(biomeName);
        if (Files.exists(biomeTable)) {
            return biomeTable;
        }
        if (environment == World.Environment.NORMAL) {
            return lootDirectory.resolve(OverworldLootCategory.tableFor(biome.getKey().getKey()));
        }
        return lootDirectory.resolve(switch (environment) {
            case NETHER -> "nether.json";
            case THE_END -> "the_end.json";
            default -> "overworld.json";
        });
    }

    private JsonObject loadTable(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read loot table " + path + ".", exception);
            return null;
        }
    }

    private void copyTableIfMissing(String name) throws IOException {
        Path table = lootDirectory.resolve(name);
        if (Files.exists(table)) {
            return;
        }
        try (InputStream resource = plugin.getResource("loot/" + name)) {
            if (resource == null) {
                throw new IOException("Missing bundled loot resource loot/" + name);
            }
            Files.copy(resource, table);
        }
    }

    private static int intValueOrDefault(JsonObject object, String property, int defaultValue) {
        return object.has(property) ? object.get(property).getAsInt() : defaultValue;
    }

    private static double doubleValueOrDefault(JsonObject object, String property, double defaultValue) {
        return object.has(property) ? object.get(property).getAsDouble() : defaultValue;
    }

    private record LootEntry(Material material, JsonObject definition, int weight) {
    }
}
