package at.pavlov.cannons.hooks.movecraft.listener;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.hooks.movecraft.MovecraftUtils;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class MovecraftWeaponController implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerClockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 1. Controlla se il giocatore ha in mano l'orologio di puntamento
        if (item == null || item.getType() != Material.CLOCK) {
            return;
        }

        // 2. Trova il veicolo Movecraft pilotato dal giocatore passando dal CraftManager globale
        Craft craft = Movecraft.getInstance().getCraftManager().getCraftByPlayer(player);
        if (craft == null) {
            return; // Il giocatore non sta pilotando un veicolo, lascia gestire al comportamento standard
        }

        // Cancelliamo l'evento per impedire interazioni vanilla dell'orologio
        event.setCancelled(true);

        // 3. Recupera tutti i cannoni presenti all'interno della struttura della nave
        Set<Cannon> vehicleCannons = MovecraftUtils.getCannons(craft);
        if (vehicleCannons == null || vehicleCannons.isEmpty()) {
            player.sendMessage("§c[Cannons] Nessun cannone rilevato su questo veicolo.");
            return;
        }

        // 4. CLICK DESTRO = MIRA GLOBALE (Allinea i cannoni allo sguardo del pilota)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            float playerYaw = player.getLocation().getYaw();
            float playerPitch = player.getLocation().getPitch();

            int targetCount = 0;
            for (Cannon cannon : vehicleCannons) {
                if (cannon.getAngleData() != null) {
                    // Imposta gli angoli orizzontali e verticali in base al mirino del pilota
                    cannon.getAngleData().setHorizontalAngle(playerYaw);
                    cannon.getAngleData().setVerticalAngle(playerPitch);
                    targetCount++;
                }
            }
            player.sendMessage("§a[Cannons] Sincronizzati e mirati " + targetCount + " cannoni nella tua direzione.");
        }

        // 5. CLICK SINISTRO = FUOCO SIMULTANEO + AUTO-RICARICA DA CHEST
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            int firedCount = 0;
            for (Cannon cannon : vehicleCannons) {
                // Chiamiamo il CannonManager del plugin Cannons. 
                // Questo metodo esegue i controlli nativi: se il cannone ha l'autoreload attivo,
                // preleva munizioni e polvere dalla chest adiacente e spara istantaneamente.
                boolean fired = Cannons.getPlugin().getCannonManager().fireCannon(cannon, player, false);
                if (fired) {
                    firedCount++;
                }
            }
            if (firedCount > 0) {
                player.sendMessage("§e[Cannons] Fuoco di sbarramento! Sparati " + firedCount + " cannoni.");
            } else {
                player.sendMessage("§c[Cannons] Impossibile sparare. Controlla munizioni o il cooldown dei cannoni.");
            }
        }
    }
}
