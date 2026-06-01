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

        // 1. Controlla se il giocatore ha in mano l'orologio
        if (item == null || item.getType() != Material.CLOCK) {
            return;
        }

        // 2. Trova il veicolo Movecraft usando l'istanza corretta del plugin
        Craft craft = Movecraft.getInstance().getCraftManager().getCraftByPlayer(player);
        if (craft == null) {
            return; 
        }

        // Cancelliamo l'evento vanilla dell'orologio
        event.setCancelled(true);

        // 3. Recupera tutti i cannoni a bordo del veicolo tramite l'utility di Cannons
        Set<Cannon> vehicleCannons = MovecraftUtils.getCannons(craft);
        if (vehicleCannons == null || vehicleCannons.isEmpty()) {
            player.sendMessage("§c[Cannons] Nessun cannone rilevato su questo veicolo.");
            return;
        }

        // 4. CLICK DESTRO = MIRA GLOBALE (Allinea tutti i cannoni allo sguardo del pilota)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            float playerYaw = player.getLocation().getYaw();
            float playerPitch = player.getLocation().getPitch();

            int targetCount = 0;
            for (Cannon cannon : vehicleCannons) {
                if (cannon.getAimingData() != null) {
                    // Nel Cannons moderno i dati di puntamento sono memorizzati in AimingData
                    cannon.getAimingData().setHorizontalAngle(playerYaw);
                    cannon.getAimingData().setVerticalAngle(playerPitch);
                    targetCount++;
                }
            }
            player.sendMessage("§a[Cannons] Sincronizzati e mirati " + targetCount + " cannoni nella tua direzione.");
        }

        // 5. CLICK SINISTRO = FUOCO DI GRUPPO SFRUTTANDO IL CANNON MANAGER NATIVO
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            int firedCount = 0;
            
            for (Cannon cannon : vehicleCannons) {
                // Impostiamo temporaneamente il giocatore come operatore Master del cannone usando l'interfaccia LinkingDataHolder
                cannon.addCannonOperator(player, true);

                // Chiamiamo il CannonManager del plugin per far sparare il cannone in modo sicuro.
                // Passando l'operatore e attivando il cannone come Master, il plugin innescherà autonomamente
                // il CannonLinkFiringEvent per gestire la raffica di gruppo in modo nativo.
                boolean fired = Cannons.getPlugin().getCannonManager().fireCannon(cannon, player, false);
                
                // Puliamo i dati rimuovendo l'operatore dopo lo sparo per non lasciare residui in memoria
                cannon.removeCannonOperator();

                if (fired) {
                    firedCount++;
                }
            }
            
            if (firedCount > 0) {
                player.sendMessage("§e[Cannons] Fuoco di sbarramento! Innescati " + firedCount + " sistemi d'arma.");
            } else {
                player.sendMessage("§c[Cannons] Impossibile sparare. Controlla munizioni o cooldown.");
            }
        }
    }
}
