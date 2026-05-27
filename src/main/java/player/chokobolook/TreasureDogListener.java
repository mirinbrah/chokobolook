package player.chokobolook;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TreasureDogListener implements Listener {
    private static final int SEARCH_RADIUS = 50;
    private static final int MIN_SEARCH_DISTANCE = 12;
    private static final int SEARCH_TIMEOUT_TICKS = 20 * 25;
    private static final Set<Material> TREASURE_FOOD = Set.of(
            Material.BONE,
            Material.BEEF,
            Material.COOKED_BEEF,
            Material.CHICKEN,
            Material.COOKED_CHICKEN,
            Material.MUTTON,
            Material.COOKED_MUTTON,
            Material.PORKCHOP,
            Material.COOKED_PORKCHOP,
            Material.RABBIT,
            Material.COOKED_RABBIT,
            Material.ROTTEN_FLESH
    );

    private final Chokobolook plugin;
    private final LootTableManager lootTableManager;
    private final TreasureDogService dogService;
    private final Set<UUID> huntingDogs = ConcurrentHashMap.newKeySet();

    public TreasureDogListener(
            Chokobolook plugin,
            LootTableManager lootTableManager,
            TreasureDogService dogService
    ) {
        this.plugin = plugin;
        this.lootTableManager = lootTableManager;
        this.dogService = dogService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDogDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Wolf wolf
                && dogService.isTreasureDog(wolf)
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDogFed(PlayerInteractEntityEvent event) {
        Wolf wolf = interactingOwnedDog(event);
        if (wolf == null || !isTreasureFood(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
        startHunt(wolf, event.getPlayer());
    }

    private void startHunt(Wolf wolf, Player owner) {
        if (!huntingDogs.add(wolf.getUniqueId())) {
            owner.sendMessage("Your dog is already searching for treasure.");
            return;
        }
        wolf.setSitting(false);
        consumeOne(owner, owner.getInventory().getItemInMainHand());

        Location target = findTreasureSpot(wolf.getLocation());
        if (target == null) {
            huntingDogs.remove(wolf.getUniqueId());
            owner.sendMessage("Your dog could not find a safe place to dig.");
            return;
        }
        owner.sendMessage("Your dog caught a scent and ran to search for treasure!");
        runToTreasure(wolf, owner, target);
    }

    private void runToTreasure(Wolf wolf, Player owner, Location target) {
        Pathfinder pathfinder = wolf.getPathfinder();
        moveToTarget(pathfinder, target);
        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!isAvailable(wolf)) {
                    finishHunt(wolf);
                    cancel();
                    return;
                }
                if (lostScent(wolf, target, elapsed)) {
                    owner.sendMessage("Your dog lost the scent.");
                    finishHunt(wolf);
                    cancel();
                    return;
                }
                if (reachedTarget(wolf, target)) {
                    cancel();
                    digForTreasure(wolf, owner, target);
                    return;
                }
                if (elapsed % 40 == 0) {
                    refreshSearchTrail(wolf, pathfinder, target);
                }
                elapsed += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void digForTreasure(Wolf wolf, Player owner, Location target) {
        wolf.getPathfinder().stopPathfinding();
        wolf.teleport(target.clone().add(0, 0, 0));
        new BukkitRunnable() {
            private int digs;

            @Override
            public void run() {
                if (!wolf.isValid()) {
                    finishHunt(wolf);
                    cancel();
                    return;
                }
                playDigEffect(wolf, target);
                digs++;
                if (digs >= 6) {
                    cancel();
                    revealTreasure(wolf, owner, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    private void revealTreasure(Wolf wolf, Player owner, Location target) {
        ItemStack treasure = lootTableManager.roll();
        if (treasure == null) {
            revealEmptyDig(owner, target);
        } else {
            revealFoundTreasure(owner, target, treasure);
        }
        finishHunt(wolf);
    }

    private Wolf interactingOwnedDog(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Wolf wolf)
                || !isOwnedBy(wolf, event.getPlayer())) {
            return null;
        }
        return wolf;
    }

    private boolean isTreasureFood(Player player) {
        return TREASURE_FOOD.contains(player.getInventory().getItemInMainHand().getType());
    }

    private boolean isAvailable(Wolf wolf) {
        return wolf.isValid() && !wolf.isDead();
    }

    private boolean lostScent(Wolf wolf, Location target, int elapsed) {
        return wolf.getWorld() != target.getWorld() || elapsed >= SEARCH_TIMEOUT_TICKS;
    }

    private boolean reachedTarget(Wolf wolf, Location target) {
        return wolf.getLocation().distanceSquared(target) <= 6.25;
    }

    private void refreshSearchTrail(Wolf wolf, Pathfinder pathfinder, Location target) {
        moveToTarget(pathfinder, target);
        wolf.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, wolf.getLocation().add(0, 0.7, 0), 2);
    }

    private void moveToTarget(Pathfinder pathfinder, Location target) {
        pathfinder.moveTo(target, 1.35);
    }

    private void playDigEffect(Wolf wolf, Location target) {
        Block ground = target.clone().subtract(0, 1, 0).getBlock();
        wolf.getWorld().spawnParticle(
                Particle.BLOCK,
                target.clone().add(0, 0.25, 0),
                14,
                0.35,
                0.15,
                0.35,
                ground.getBlockData()
        );
        wolf.getWorld().playSound(target, Sound.BLOCK_GRAVEL_BREAK, 0.8f, 1.1f);
    }

    private void revealEmptyDig(Player owner, Location target) {
        owner.sendMessage("Your dog dug carefully, but found nothing this time.");
        target.getWorld().playSound(target, Sound.ENTITY_WOLF_WHINE, 0.8f, 1.1f);
    }

    private void revealFoundTreasure(Player owner, Location target, ItemStack treasure) {
        Item dropped = target.getWorld().dropItemNaturally(target.clone().add(0, 0.45, 0), treasure);
        dropped.setVelocity(new Vector(0, 0.18, 0));
        owner.sendMessage("Your dog found: " + treasure.getType().translationKey() + "!");
        target.getWorld().playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.clone().add(0, 0.5, 0), 22);
    }

    private Location findTreasureSpot(Location origin) {
        World world = origin.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            int distance = random.nextInt(MIN_SEARCH_DISTANCE, SEARCH_RADIUS + 1);
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location spot = safeSurfaceLocation(world, x, z);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    private Location safeSurfaceLocation(World world, int x, int z) {
        if (world.getEnvironment() != World.Environment.NETHER) {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            return safeAt(world, x, y, z);
        }

        int maximumY = Math.min(120, world.getMaxHeight() - 2);
        for (int y = maximumY; y > world.getMinHeight() + 1; y--) {
            Location safe = safeAt(world, x, y, z);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Location safeAt(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);
        if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()) {
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private boolean isOwnedBy(Wolf wolf, Player player) {
        return dogService.isTreasureDog(wolf)
                && wolf.isTamed()
                && wolf.getOwner() != null
                && wolf.getOwner().getUniqueId().equals(player.getUniqueId());
    }

    private void consumeOne(Player player, ItemStack heldItem) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }
    }

    private void finishHunt(Wolf wolf) {
        huntingDogs.remove(wolf.getUniqueId());
    }
}
