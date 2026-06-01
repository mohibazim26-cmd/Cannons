package at.pavlov.cannons.hooks.movecraft.listener;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.cannon.CannonDesign;
import at.pavlov.cannons.hooks.movecraft.MovecraftUtils;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.CraftType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MovecraftWeaponsScoreboard implements Listener {
    private static final String OBJECTIVE_NAME = "mcweapons";
    private static final int MAX_LINES = 15;
    private static final int MAX_LINE_LENGTH = 38;

    private final Cannons plugin;
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, Set<String>> activeEntries = new HashMap<>();

    public MovecraftWeaponsScoreboard(Cannons plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if (craft == null) {
            clear(player);
            return;
        }

        Set<Cannon> cannons = MovecraftUtils.getCannons(craft);
        if (cannons.isEmpty()) {
            clear(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        Scoreboard board = activeScoreboards.computeIfAbsent(playerId, ignored -> createBoard());
        if (!previousScoreboards.containsKey(playerId) && player.getScoreboard() != board) {
            previousScoreboards.put(playerId, player.getScoreboard());
        }

        Objective objective = getOrCreateObjective(board);
        objective.setDisplayName(formatTitle(craft));

        Set<String> previousEntries = activeEntries.computeIfAbsent(playerId, ignored -> new HashSet<>());
        for (String previousEntry : previousEntries) {
            board.resetScores(previousEntry);
        }
        previousEntries.clear();

        List<String> lines = buildLines(cannons);
        int score = Math.min(lines.size(), MAX_LINES);
        Set<String> usedLines = new HashSet<>();
        for (String line : lines.stream().limit(MAX_LINES).toList()) {
            String uniqueLine = uniqueLine(line, usedLines);
            objective.getScore(uniqueLine).setScore(score--);
            previousEntries.add(uniqueLine);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private Scoreboard createBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        return manager.getNewScoreboard();
    }

    private Objective getOrCreateObjective(Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", ChatColor.WHITE + "Weapons");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        return objective;
    }

    private List<String> buildLines(Set<Cannon> cannons) {
        List<Cannon> sortedCannons = cannons.stream()
                .sorted(Comparator
                        .comparing((Cannon cannon) -> displayName(cannon.getCannonDesign()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(cannon -> directionLabel(cannon.getCannonDirection()))
                        .thenComparing(cannon -> cannon.getLocation().toVector().toString()))
                .toList();

        List<String> lines = new ArrayList<>();
        for (Cannon cannon : sortedCannons) {
            lines.add(formatCannonLine(cannon));
        }
        return lines;
    }

    private String formatCannonLine(Cannon cannon) {
        CannonDesign design = cannon.getCannonDesign();
        ChatColor color = statusColor(cannon);

        StringBuilder line = new StringBuilder();
        line.append(color)
                .append(trimVisible(displayName(design), 18))
                .append(" ")
                .append(ChatColor.WHITE)
                .append("(")
                .append(directionLabel(cannon.getCannonDirection()))
                .append(")");

        if (cannon.isFiring()) {
            line.append(ChatColor.BLUE).append(" firing");
        } else if (cannon.isLoading() || cannon.barrelTooHot() || !cannon.finishedFiringAndLoading() && cannon.isLoaded()) {
            line.append(ChatColor.YELLOW).append(" loading");
        } else if (cannon.isLoaded() && charges(cannon) > 0) {
            line.append(ChatColor.WHITE).append(" - ")
                    .append(charges(cannon))
                    .append(" charges");
        }

        return trimVisible(line.toString(), MAX_LINE_LENGTH);
    }

    private ChatColor statusColor(Cannon cannon) {
        if (cannon.isFiring()) {
            return ChatColor.BLUE;
        }
        if (cannon.isLoading() || cannon.barrelTooHot() || !cannon.finishedFiringAndLoading() && cannon.isLoaded()) {
            return ChatColor.YELLOW;
        }
        if (cannon.isReadyToFire()) {
            return ChatColor.GREEN;
        }
        return ChatColor.RED;
    }

    private int charges(Cannon cannon) {
        if (cannon.getLoadedProjectile() != null && cannon.getLoadedProjectile().getAutomaticFiringMagazineSize() > 1) {
            return cannon.getLoadedProjectile().getAutomaticFiringMagazineSize();
        }
        return cannon.getLoadedGunpowder();
    }

    private String displayName(CannonDesign design) {
        String name = ChatColor.stripColor(design.getDesignName());
        if (name == null || name.isBlank()) {
            name = ChatColor.stripColor(design.getDesignID());
        }
        return name == null || name.isBlank() ? "Cannon" : name.trim();
    }

    private String directionLabel(BlockFace face) {
        return switch (face) {
            case NORTH, NORTH_EAST, NORTH_WEST -> "N";
            case SOUTH, SOUTH_EAST, SOUTH_WEST -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "?";
        };
    }

    private String formatTitle(Craft craft) {
        String craftName = craft.getName();
        if (craftName == null || craftName.isBlank()) {
            craftName = craft.getType().getStringProperty(CraftType.NAME);
        }
        if (craftName == null || craftName.isBlank()) {
            craftName = "Weapons";
        }
        return ChatColor.WHITE.toString() + ChatColor.BOLD + trimVisible(craftName, 28);
    }

    private String uniqueLine(String line, Set<String> usedLines) {
        String unique = line;
        ChatColor[] colors = ChatColor.values();
        int suffix = 0;
        while (usedLines.contains(unique)) {
            unique = trimVisible(line, MAX_LINE_LENGTH - 2) + colors[suffix++ % colors.length];
        }
        usedLines.add(unique);
        return unique;
    }

    private String trimVisible(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void clear(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard active = activeScoreboards.remove(playerId);
        activeEntries.remove(playerId);

        Scoreboard previous = previousScoreboards.remove(playerId);
        if (active != null && player.isOnline() && player.getScoreboard() == active) {
            if (previous != null) {
                player.setScoreboard(previous);
            } else if (Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }
}
