package at.pavlov.cannons.commands;

import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.hooks.movecraft.FirepowerReport;
import at.pavlov.cannons.hooks.movecraft.FirepowerUtil;
import at.pavlov.cannons.hooks.movecraft.MovecraftUtils;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Set;

@CommandAlias("firepower")
public class FirepowerCommand extends BaseCommand {
    @Default
    public void onFirepower(Player player) {
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if (craft == null) {
            player.sendMessage(ChatColor.RED + "Non stai pilotando nessun craft.");
            return;
        }

        Set<Cannon> cannons = MovecraftUtils.getCannons(craft);
        FirepowerReport report = FirepowerUtil.calculate(craft, cannons);

        player.sendMessage(ChatColor.GOLD + "Firepower di " + ChatColor.YELLOW + craft.getName());
        player.sendMessage(ChatColor.GRAY + "Cannoni: " + color(report.effectiveCannonFirepower(), report.maxCannonFirepower())
                + format(report.effectiveCannonFirepower()) + limit(report.maxCannonFirepower()));
        player.sendMessage(ChatColor.GRAY + "Torpedini: " + color(report.torpedoFirepower(), report.maxTorpedoFirepower())
                + format(report.torpedoFirepower()) + limit(report.maxTorpedoFirepower()));
        if (report.torpedoOverflow() > 0) {
            player.sendMessage(ChatColor.GRAY + "Overflow torpedo: " + ChatColor.YELLOW
                    + format(report.torpedoOverflow()) + ChatColor.GRAY + " -> consumo firepower normale");
        }
    }

    private String limit(Integer limit) {
        return limit == null ? ChatColor.GRAY + " / nessun limite" : ChatColor.GRAY + " / " + limit;
    }

    private ChatColor color(double value, Integer limit) {
        if (limit == null) {
            return ChatColor.GREEN;
        }
        return value > limit ? ChatColor.RED : ChatColor.GREEN;
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }
}
