package player.chokobolook;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LootTableManager {
    private static final double DEFAULT_FIND_CHANCE = 0.60;

    private final Chokobolook plugin;
    private double findChance;
    private List<LootEntry> entries = List.of();

    public LootTableManager(Chokobolook plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();
        findChance = Math.clamp(config.getDouble("find-chance", DEFAULT_FIND_CHANCE), 0, 1);
        entries = readEntries(config.getMapList("loot"));
    }

    public ItemStack roll() {
        if (!foundTreasure() || entries.isEmpty()) {
            return null;
        }
        LootEntry entry = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        return createItem(entry);
    }

    private boolean foundTreasure() {
        return ThreadLocalRandom.current().nextDouble() < findChance;
    }

    private List<LootEntry> readEntries(List<Map<?, ?>> definitions) {
        List<LootEntry> loaded = new ArrayList<>();
        for (Map<?, ?> definition : definitions) {
            LootEntry entry = readEntry(definition);
            if (entry != null) {
                loaded.add(entry);
            }
        }
        return List.copyOf(loaded);
    }

    private LootEntry readEntry(Map<?, ?> definition) {
        Material material = Material.matchMaterial(stringValue(definition, "material"));
        if (material == null) {
            plugin.getLogger().warning("Ignoring unknown loot material: " + definition.get("material"));
            return null;
        }

        int minAmount = Math.max(1, intValue(definition, "min-amount", 1));
        int maxAmount = Math.max(minAmount, intValue(definition, "max-amount", minAmount));
        return new LootEntry(material, minAmount, maxAmount, readEnchantments(definition));
    }

    private Map<String, Integer> readEnchantments(Map<?, ?> definition) {
        Object value = definition.get("enchantments");
        if (!(value instanceof Map<?, ?> enchantments)) {
            return Map.of();
        }

        Map<String, Integer> loaded = new HashMap<>();
        enchantments.forEach((name, level) -> loaded.put(
                String.valueOf(name),
                level instanceof Number number ? number.intValue() : 1
        ));
        return Map.copyOf(loaded);
    }

    private ItemStack createItem(LootEntry entry) {
        ItemStack item = new ItemStack(entry.material(), entry.randomAmount());
        applyEnchantments(item, entry.enchantments());
        return item;
    }

    private void applyEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        ItemMeta meta = item.getItemMeta();
        enchantments.forEach((name, level) -> addEnchantment(meta, findEnchantment(name), level));
        item.setItemMeta(meta);
    }

    private Enchantment findEnchantment(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        return Registry.ENCHANTMENT.get(key);
    }

    private void addEnchantment(ItemMeta meta, Enchantment enchantment, int level) {
        if (enchantment == null) {
            return;
        }
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            bookMeta.addStoredEnchant(enchantment, level, true);
            return;
        }
        meta.addEnchant(enchantment, level, true);
    }

    private String stringValue(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Map<?, ?> values, String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private record LootEntry(
            Material material,
            int minAmount,
            int maxAmount,
            Map<String, Integer> enchantments
    ) {
        private int randomAmount() {
            return ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
        }
    }
}
