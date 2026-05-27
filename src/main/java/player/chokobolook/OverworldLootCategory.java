package player.chokobolook;

import java.util.Arrays;
import java.util.Set;

public enum OverworldLootCategory {
    PLAINS("overworld_plains.json", "plains", "sunflower_plains", "meadow"),
    FOREST("overworld_forest.json", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest"),
    DARK_FOREST("overworld_dark_forest.json", "dark_forest", "pale_garden"),
    DESERT("overworld_desert.json", "desert", "beach"),
    SAVANNA("overworld_savanna.json", "savanna", "savanna_plateau", "windswept_savanna"),
    BADLANDS("overworld_badlands.json", "badlands", "wooded_badlands", "eroded_badlands"),
    SNOWY(
            "overworld_snowy.json",
            "snowy_plains",
            "ice_spikes",
            "snowy_slopes",
            "frozen_peaks",
            "jagged_peaks",
            "frozen_river",
            "snowy_beach"
    ),
    TAIGA("overworld_taiga.json", "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga"),
    JUNGLE("overworld_jungle.json", "jungle", "sparse_jungle", "bamboo_jungle"),
    SWAMP("overworld_swamp.json", "swamp", "mangrove_swamp"),
    MOUNTAIN(
            "overworld_mountain.json",
            "windswept_hills",
            "windswept_forest",
            "windswept_gravelly_hills",
            "stony_peaks",
            "stony_shore",
            "grove"
    ),
    CAVE("overworld_cave.json", "lush_caves", "dripstone_caves", "deep_dark"),
    OCEAN(
            "overworld_ocean.json",
            "ocean",
            "deep_ocean",
            "cold_ocean",
            "deep_cold_ocean",
            "lukewarm_ocean",
            "deep_lukewarm_ocean",
            "warm_ocean",
            "frozen_ocean",
            "deep_frozen_ocean",
            "river"
    ),
    CHERRY("overworld_cherry.json", "cherry_grove"),
    MUSHROOM("overworld_mushroom.json", "mushroom_fields");

    private static final String DEFAULT_TABLE = "overworld.json";

    private final String tableName;
    private final Set<String> biomes;

    OverworldLootCategory(String tableName, String... biomes) {
        this.tableName = tableName;
        this.biomes = Set.of(biomes);
    }

    public String tableName() {
        return tableName;
    }

    public static String tableFor(String biome) {
        return Arrays.stream(values())
                .filter(category -> category.biomes.contains(biome))
                .findFirst()
                .map(OverworldLootCategory::tableName)
                .orElse(DEFAULT_TABLE);
    }
}
