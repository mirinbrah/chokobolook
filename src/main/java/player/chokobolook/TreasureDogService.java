package player.chokobolook;

import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

public final class TreasureDogService {
    private static final byte TREASURE_DOG_MARKER = 1;

    private final NamespacedKey treasureDogKey;

    public TreasureDogService(Chokobolook plugin) {
        this.treasureDogKey = new NamespacedKey(plugin, "treasure_dog");
    }

    public Wolf summonFor(Player owner) {
        Wolf wolf = (Wolf) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.WOLF);
        wolf.setOwner(owner);
        wolf.setSitting(false);
        wolf.setCollarColor(DyeColor.ORANGE);
        wolf.setCustomName("Treasure Dog");
        wolf.setCustomNameVisible(true);
        wolf.setInvulnerable(true);
        wolf.getPersistentDataContainer().set(treasureDogKey, PersistentDataType.BYTE, TREASURE_DOG_MARKER);
        return wolf;
    }

    public boolean isTreasureDog(Wolf wolf) {
        Byte marker = wolf.getPersistentDataContainer().get(treasureDogKey, PersistentDataType.BYTE);
        return marker != null && marker == TREASURE_DOG_MARKER;
    }
}
