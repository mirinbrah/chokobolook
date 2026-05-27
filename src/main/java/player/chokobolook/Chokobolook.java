package player.chokobolook;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Chokobolook extends JavaPlugin {

    @Override
    public void onEnable() {
        LootTableManager lootTableManager = new LootTableManager(this);
        TreasureDogService dogService = new TreasureDogService(this);
        lootTableManager.reload();
        TreasureDogAdminCommand adminCommand = new TreasureDogAdminCommand(lootTableManager, dogService);
        Objects.requireNonNull(getCommand("chokobolook")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("chokobolook")).setTabCompleter(adminCommand);
        getServer().getPluginManager().registerEvents(
                new TreasureDogListener(this, lootTableManager, dogService),
                this
        );
        getLogger().info("Treasure dog minigame is enabled.");
    }
}
