package at.pavlov.cannons.hooks.movecraft;

import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.cannon.CannonDesign;
import at.pavlov.cannons.hooks.movecraft.type.properties.CannonProperties;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.CraftType;

import java.util.Locale;
import java.util.Set;

public final class FirepowerUtil {
    private static final double TORPEDO_OVERFLOW_MULTIPLIER = 3.34;

    private FirepowerUtil() {
    }

    public static FirepowerReport calculate(Craft craft, Set<Cannon> cannons) {
        CraftType type = craft.getType();
        Set<String> excluded = CannonProperties.EXCLUDE_FROM_MASS.get(type);

        double cannonFirepower = 0.0;
        double torpedoFirepower = 0.0;
        for (Cannon cannon : cannons) {
            CannonDesign design = cannon.getCannonDesign();
            if (excluded.contains(design.getDesignID())) {
                continue;
            }

            if (isTorpedo(design)) {
                torpedoFirepower += design.getMassOfCannon();
            } else {
                cannonFirepower += design.getMassOfCannon();
            }
        }

        Integer maxCannon = scaledLimit(CannonProperties.MAX_CANNON_FIREPOWER.get(type), craft);
        Integer maxTorpedo = scaledLimit(CannonProperties.MAX_TORPEDO_FIREPOWER.get(type), craft);
        boolean canOverflow = CannonProperties.TORPEDO_OVERFLOW_USES_CANNON_FIREPOWER.get(type) == Boolean.TRUE;

        double overflow = 0.0;
        double effectiveCannonFirepower = cannonFirepower;
        if (canOverflow && maxTorpedo != null && torpedoFirepower > maxTorpedo) {
            overflow = torpedoFirepower - maxTorpedo;
            effectiveCannonFirepower += overflow * TORPEDO_OVERFLOW_MULTIPLIER;
        }

        return new FirepowerReport(cannonFirepower, torpedoFirepower, effectiveCannonFirepower, overflow, maxCannon, maxTorpedo);
    }

    public static boolean isTorpedo(CannonDesign design) {
        String id = design.getDesignID().toLowerCase(Locale.ROOT);
        String name = design.getDesignName().toLowerCase(Locale.ROOT);
        return id.contains("torpedo") || name.contains("torpedo");
    }

    private static Integer scaledLimit(Integer configuredLimit, Craft craft) {
        if (configuredLimit == null || CannonProperties.SCALE_FIREPOWER_WITH_SIZE.get(craft.getType()) != Boolean.TRUE) {
            return configuredLimit;
        }

        int minSize = craft.getType().getIntProperty(CraftType.MIN_SIZE);
        if (minSize <= 0) {
            return configuredLimit;
        }
        return Math.max(configuredLimit, (int) Math.round(configuredLimit * (craft.getOrigBlockCount() / (double) minSize)));
    }
}
