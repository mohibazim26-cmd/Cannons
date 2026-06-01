package at.pavlov.cannons.hooks.movecraft.listener;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.Enum.InteractAction;
import at.pavlov.cannons.Enum.MessageEnum;
import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.cannon.CannonDesign;
import at.pavlov.cannons.hooks.movecraft.MovecraftUtils;
import at.pavlov.cannons.utils.CannonsUtil;
import at.pavlov.internal.enums.FakeBlockType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MovecraftWeaponController implements Listener {
    private static final String METADATA_KEY = "cannons-movecraft-clock";
    private static final String ALL_CANNONS_NAME = ChatColor.GOLD + "Cannons";
    private static final long AIM_HOLD_MILLIS = 2000L;

    private final Cannons plugin;
    private final Map<UUID, Long> activeAiming = new HashMap<>();

    public MovecraftWeaponController(Cannons plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActiveAiming, 2L, 5L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerClockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isClock(event.getItem()) || !isWeaponAction(event.getAction())) {
            return;
        }

        Player player = event.getPlayer();
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if (craft == null) {
            return;
        }

        Set<Cannon> craftCannons = MovecraftUtils.getCannons(craft);
        if (craftCannons.isEmpty()) {
            return;
        }

        markHandled(player);
        event.setCancelled(true);

        if (player.isSneaking() && isLeftClick(event.getAction())) {
            cycleClockFilter(event.getItem(), craftCannons, player);
            return;
        }

        String filter = getClockFilter(event.getItem());
        List<Cannon> selectedCannons = selectCannons(craftCannons, filter);
        if (selectedCannons.isEmpty()) {
            sendStatus(player, ChatColor.RED + "Nessun cannone " + formatFilter(filter) + " su questo craft");
            return;
        }

        if (isRightClick(event.getAction())) {
            activeAiming.put(player.getUniqueId(), System.currentTimeMillis() + AIM_HOLD_MILLIS);
            aimCannons(selectedCannons, player);
            showAimingVectors(selectedCannons, player);
            return;
        }

        if (isLeftClick(event.getAction())) {
            fireCannons(selectedCannons, player);
        }
    }

    private boolean isWeaponAction(Action action) {
        return isRightClick(action) || isLeftClick(action);
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isLeftClick(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private boolean isClock(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.CLOCK;
    }

    private List<Cannon> selectCannons(Set<Cannon> craftCannons, @Nullable String filter) {
        return craftCannons.stream()
                .filter(cannon -> matchesFilter(cannon, filter))
                .sorted(Comparator.comparing(cannon -> cannon.getLocation().toVector().toString()))
                .toList();
    }

    private int aimCannons(List<Cannon> cannons, Player player) {
        Location eye = player.getEyeLocation();
        double yaw = normalizeAngle(eye.getYaw());
        double pitch = eye.getPitch();
        int aimed = 0;

        for (Cannon cannon : cannons) {
            if (!cannon.canAimYaw(yaw) || !cannon.canAimPitch(pitch)) {
                continue;
            }

            double horizontal = normalizeAngle(yaw
                    - CannonsUtil.directionToYaw(cannon.getCannonDirection())
                    - cannon.getAdditionalHorizontalAngle());
            double vertical = -pitch
                    - cannon.getCannonDesign().getDefaultVerticalAngle()
                    - cannon.getAdditionalVerticalAngle();

            cannon.setHorizontalAngle(horizontal);
            cannon.setVerticalAngle(vertical);
            cannon.setAimingYaw(yaw);
            cannon.setAimingPitch(pitch);
            cannon.setAimingFinished(true);
            cannon.setLastAimed(System.currentTimeMillis());
            aimed++;
        }

        return aimed;
    }

    private void fireCannons(List<Cannon> cannons, Player player) {
        int aimed = aimCannons(cannons, player);
        int fired = 0;
        int loaded = 0;
        int blocked = 0;

        for (Cannon cannon : cannons) {
            if (!cannon.canAimYaw(player.getEyeLocation().getYaw()) || !cannon.canAimPitch(player.getEyeLocation().getPitch())) {
                continue;
            }

            CannonDesign design = cannon.getCannonDesign();
            MessageEnum result = plugin.getFireCannon().fire(
                    cannon,
                    player.getUniqueId(),
                    true,
                    !design.isAmmoInfiniteForPlayer(),
                    InteractAction.fireAutoaim
            );

            if (result == MessageEnum.CannonFire) {
                fired++;
            } else if (result == MessageEnum.loadProjectile || result == MessageEnum.loadGunpowder || result == MessageEnum.loadGunpowderAndProjectile) {
                loaded++;
            } else if (result == null || result.isError()) {
                blocked++;
            }
        }

        if (fired > 0) {
            sendStatus(player, ChatColor.GOLD + "Fuoco: " + ChatColor.YELLOW + fired + ChatColor.GRAY + " cannoni");
        } else if (loaded > 0) {
            sendStatus(player, ChatColor.GOLD + "Caricati: " + ChatColor.YELLOW + loaded + ChatColor.GRAY + " cannoni");
        } else if (aimed == 0) {
            sendStatus(player, ChatColor.RED + "Nessun cannone puo' mirare in quella direzione");
        } else {
            sendStatus(player, ChatColor.RED + "Nessun cannone pronto al fuoco" + (blocked > 0 ? ChatColor.GRAY + " (" + blocked + ")" : ""));
        }
    }

    private void showAimingVectors(List<Cannon> cannons, Player player) {
        plugin.getFakeBlockHandler().removeFakeBlocks(player, FakeBlockType.AIMING);
        for (Cannon cannon : cannons) {
            plugin.getAiming().showAimingVector(cannon, player);
        }
    }

    private void tickActiveAiming() {
        long now = System.currentTimeMillis();
        var iterator = activeAiming.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || entry.getValue() < now || !isClock(player.getInventory().getItemInMainHand())) {
                if (player != null) {
                    plugin.getFakeBlockHandler().removeFakeBlocks(player, FakeBlockType.AIMING);
                }
                iterator.remove();
                continue;
            }

            Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
            if (craft == null) {
                plugin.getFakeBlockHandler().removeFakeBlocks(player, FakeBlockType.AIMING);
                iterator.remove();
                continue;
            }

            List<Cannon> cannons = selectCannons(MovecraftUtils.getCannons(craft), getClockFilter(player.getInventory().getItemInMainHand()));
            if (cannons.isEmpty()) {
                plugin.getFakeBlockHandler().removeFakeBlocks(player, FakeBlockType.AIMING);
                iterator.remove();
                continue;
            }

            aimCannons(cannons, player);
            showAimingVectors(cannons, player);
        }
    }

    private void cycleClockFilter(ItemStack clock, Set<Cannon> craftCannons, Player player) {
        List<String> names = new ArrayList<>(getAvailableDesignNames(craftCannons));
        if (names.isEmpty()) {
            sendStatus(player, ChatColor.RED + "Nessun tipo di cannone trovato");
            return;
        }

        String currentFilter = getClockFilter(clock);
        int nextIndex = 0;
        if (currentFilter != null) {
            for (int i = 0; i < names.size(); i++) {
                if (sameSelector(names.get(i), currentFilter)) {
                    nextIndex = i + 1;
                    break;
                }
            }
        }

        ItemMeta meta = clock.getItemMeta();
        if (meta == null) {
            return;
        }

        if (nextIndex >= names.size()) {
            meta.setDisplayName(ALL_CANNONS_NAME);
            clock.setItemMeta(meta);
            sendStatus(player, ChatColor.GOLD + "Cannoni selezionati: " + ChatColor.YELLOW + "Tutti");
            return;
        }

        String nextName = names.get(nextIndex);
        meta.setDisplayName(ChatColor.GOLD + nextName);
        clock.setItemMeta(meta);
        sendStatus(player, ChatColor.GOLD + "Cannoni selezionati: " + ChatColor.YELLOW + nextName);
    }

    private Set<String> getAvailableDesignNames(Set<Cannon> craftCannons) {
        Set<String> names = new LinkedHashSet<>();
        craftCannons.stream()
                .sorted(Comparator.comparing(cannon -> cannon.getCannonDesign().getDesignID(), String.CASE_INSENSITIVE_ORDER))
                .forEach(cannon -> {
                    CannonDesign design = cannon.getCannonDesign();
                    String name = cleanName(design.getDesignID());
                    if (name == null) {
                        name = cleanName(design.getDesignName());
                    }
                    if (name != null) {
                        names.add(name);
                    }
                });
        return names;
    }

    private boolean matchesFilter(Cannon cannon, @Nullable String filter) {
        if (isAllFilter(filter)) {
            return true;
        }

        CannonDesign design = cannon.getCannonDesign();
        return sameSelector(filter, design.getDesignID())
                || sameSelector(filter, design.getDesignName())
                || sameSelector(filter, design.getMessageName())
                || sameSelector(filter, cannon.getCannonName());
    }

    private @Nullable String getClockFilter(@Nullable ItemStack clock) {
        if (clock == null || !clock.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = clock.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        String displayName = cleanName(meta.getDisplayName());
        return isAllFilter(displayName) ? null : displayName;
    }

    private boolean isAllFilter(@Nullable String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String normalised = normaliseSelector(filter);
        return normalised.equals("all")
                || normalised.equals("allcannons")
                || normalised.equals("cannons")
                || normalised.equals("tutti")
                || normalised.equals("tuttiicannoni");
    }

    private boolean sameSelector(@Nullable String first, @Nullable String second) {
        if (first == null || second == null) {
            return false;
        }

        return normaliseSelector(first).equals(normaliseSelector(second));
    }

    private String normaliseSelector(String value) {
        String stripped = cleanName(value);
        if (stripped == null) {
            return "";
        }

        return stripped.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    private @Nullable String cleanName(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String stripped = ChatColor.stripColor(value);
        if (stripped == null) {
            return null;
        }

        stripped = stripped.trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private String formatFilter(@Nullable String filter) {
        return isAllFilter(filter) ? "" : "'" + filter + "'";
    }

    private double normalizeAngle(double angle) {
        angle %= 360.0;
        while (angle < -180.0) {
            angle += 360.0;
        }
        while (angle > 180.0) {
            angle -= 360.0;
        }
        return angle;
    }

    private void sendStatus(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void markHandled(Player player) {
        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.removeMetadata(METADATA_KEY, plugin);
            }
        });
    }
}
