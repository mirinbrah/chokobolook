package player.chokobolook;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class TreasureDogAdminCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "chokobolook.admin";

    private final LootTableManager lootTableManager;
    private final TreasureDogService dogService;

    public TreasureDogAdminCommand(LootTableManager lootTableManager, TreasureDogService dogService) {
        this.lootTableManager = lootTableManager;
        this.dogService = dogService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            lootTableManager.reload();
            sender.sendMessage("Treasure dog config has been reloaded.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("summon")) {
            return summonDog(sender, args);
        }

        sender.sendMessage("Usage: /" + label + " <reload|summon> [player]");
        return true;
    }

    private boolean summonDog(CommandSender sender, String[] args) {
        Player owner = targetPlayer(sender, args);
        if (owner == null) {
            sender.sendMessage("Usage: /chokobolook summon <player>");
            return true;
        }

        dogService.summonFor(owner);
        sender.sendMessage("A Treasure Dog has been summoned for " + owner.getName() + ".");
        if (owner != sender) {
            owner.sendMessage("An administrator summoned your Treasure Dog.");
        }
        return true;
    }

    private Player targetPlayer(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return player;
        }
        if (args.length == 2) {
            return sender.getServer().getPlayerExact(args[1]);
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reload", "summon").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("summon")) {
            return sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
