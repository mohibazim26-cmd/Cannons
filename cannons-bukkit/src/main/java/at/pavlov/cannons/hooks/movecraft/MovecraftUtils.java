package at.pavlov.cannons.hooks.movecraft;

import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.cannon.CannonManager;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.util.Pair;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.countercraft.movecraft.craft.type.TypeData.NUMERIC_PREFIX;

public class MovecraftUtils {
    private static final Map<UUID, Set<UUID>> CRAFT_CANNONS = new ConcurrentHashMap<>();

    private MovecraftUtils() {
    }

    public static Set<Cannon> getCannons(Craft craft) {
        Set<Cannon> cannons = getCannons(craft.getWorld(), craft.getHitBox());
        if (!cannons.isEmpty()) {
            registerCannons(craft, cannons);
            return cannons;
        }

        return getRegisteredCannons(craft);
    }

    public static Set<Cannon> getCannons(World world, HitBox hitBox) {
        List<Location> shipLocations = new ArrayList<>();
        for (MovecraftLocation loc : hitBox) {
            shipLocations.add(loc.toBukkit(world));
        }

        return CannonManager.getCannonsByLocations(shipLocations);
    }

    public static void registerCannons(Craft craft, Set<Cannon> cannons) {
        if (craft == null || craft.getUUID() == null || cannons == null || cannons.isEmpty()) {
            return;
        }

        Set<UUID> cannonIds = ConcurrentHashMap.newKeySet();
        for (Cannon cannon : cannons) {
            if (cannon != null) {
                cannonIds.add(cannon.getUID());
            }
        }

        if (!cannonIds.isEmpty()) {
            CRAFT_CANNONS.put(craft.getUUID(), cannonIds);
        }
    }

    public static Set<Cannon> getRegisteredCannons(Craft craft) {
        Set<Cannon> cannons = new HashSet<>();
        if (craft == null || craft.getUUID() == null) {
            return cannons;
        }

        Set<UUID> cannonIds = CRAFT_CANNONS.get(craft.getUUID());
        if (cannonIds == null || cannonIds.isEmpty()) {
            return cannons;
        }

        Set<UUID> staleIds = new HashSet<>();
        for (UUID cannonId : cannonIds) {
            Cannon cannon = CannonManager.getCannon(cannonId);
            if (cannon == null) {
                staleIds.add(cannonId);
                continue;
            }
            cannons.add(cannon);
        }

        if (!staleIds.isEmpty()) {
            cannonIds.removeAll(staleIds);
        }
        if (cannonIds.isEmpty()) {
            CRAFT_CANNONS.remove(craft.getUUID());
        }
        return cannons;
    }

    public static void clearRegisteredCannons(Craft craft) {
        if (craft != null && craft.getUUID() != null) {
            CRAFT_CANNONS.remove(craft.getUUID());
        }
    }

    /**
     * This method tries to get the player that is piloting the craft, or if the craft
     * is a subcraft, the pilot of the parent craft.
     *
     * @param craft Movecraft craft to search for its pilot
     * @return UUID of the pilot
     */
    public static UUID getPlayerFromCraft(Craft craft) {
        if (craft instanceof PilotedCraft pilotedCraft) {
            // If this is a piloted craft, return the pilot's UUID
            return pilotedCraft.getPilot().getUniqueId();
        }

        if (craft instanceof SubCraft subCraft) {
            // If this is a subcraft, look for a parent
            Craft parent = subCraft.getParent();
            if (parent != null) {
                // If the parent is not null, recursively check it for a UUID
                return getPlayerFromCraft(parent);
            }
        }

        // Return null if all else fails
        return null;
    }

    public static @NotNull Pair<Boolean, ? extends Number> parseLimit(@NotNull Object input) {
        if (!(input instanceof String str)) {
            return new Pair<>(false, (double) input);
        }

        if (!str.contains(NUMERIC_PREFIX)) {
            return new Pair<>(false, Double.valueOf(str));
        }

        String[] parts = str.split(NUMERIC_PREFIX);
        int val = Integer.parseInt(parts[1]);
        return new Pair<>(true, val);
    }

    public static @NotNull List<@NotNull String> parseKey(Object key) {
        if (key instanceof String string) {
            return List.of(string);
        } else if (key instanceof ArrayList<?> array) {
            if (array.get(0) instanceof String) {
                return (ArrayList<String>) array;
            }

            throw new IllegalArgumentException("Invalid parsed key");
        } else {
            throw new IllegalArgumentException("Invalid parsed key");
        }
    }
}

