package at.pavlov.cannons.hooks.movecraft.listener;

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

        // 2. Verifica se il giocatore sta effettivamente pilotando un veicolo Movecraft
        // Utilizziamo il registro ufficiale di Movecraft per trovare il Craft pilotato dal player
        Craft craft = Movecraft.getRegistry().getPilotedCraftByPlayer(player);
        if (craft == null) {
            return; // Il giocatore non sta pilotando, lascia gestire a Cannons la logica normale
        }

        // Intercettiamo l'evento per evitare che l'orologio faccia altre azioni vanilla
        event.setCancelled(true);

        // 3. Recupera tutti i cannoni presenti all'interno della struttura del veicolo
        Set<Cannon> vehicleCannons = MovecraftUtils.getCannons(craft);
        if (vehicleCannons.isEmpty()) {
            player.sendMessage("§c[Cannons] Nessun cannone rilevato su questo veicolo.");
            return;
        }

        // 4. GESTIONE INPUT: CLICK DESTRO = MIRA GENERALE (Allinea i cannoni allo sguardo del pilota)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Otteniamo la direzione dello sguardo del pilota (Yaw e Pitch attuali)
            float playerYaw = player.getLocation().getYaw();
            float playerPitch = player.getLocation().getPitch();

            int targetCount = 0;
            for (Cannon cannon : vehicleCannons) {
                // Imposta l'angolo del cannone basandoti sullo sguardo del pilota.
                // Nota: I metodi esatti nel tuo database Cannons potrebbero essere 
                // cannon.getAngleData().setYaw() / setPitch() o simili basati su Cannons.txt
                cannon.getAngleData().setHorizontalAngle(playerYaw);
                cannon.getAngleData().setVerticalAngle(playerPitch);
                
                // Forza il ricalcolo e l'aggiornamento visivo dei blocchi del cannone nel mondo
                cannon.updateCannonSign(); 
                targetCount++;
            }
            player.sendMessage("§a[Cannons] Sincronizzati e mirati " + targetCount + " cannoni nella tua direzione.");
        }

        // 5. GESTIONE INPUT: CLICK SINISTRO = INNESCO SPARO + AUTORELOAD DA CHEST
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            int firedCount = 0;
            for (Cannon cannon : vehicleCannons) {
                // Eseguiamo il metodo nativo di sparo di Cannons.
                // Se nel file di configurazione (.yml) del cannone hai impostato l'autoreload da chest,
                // il metodo c.fire() controllerà automaticamente la chest adiacente,
                // preleverà la polvere da sparo e il proiettile, ricaricherà e sparerà in un colpo solo.
                boolean fired = cannon.fire(player, false); 
                if (fired) {
                    firedCount++;
                }
            }
            if (firedCount > 0) {
                player.sendMessage("§e[Cannons] Fuoco di sbarco! Sparati " + firedCount + " cannoni.");
            } else {
                player.sendMessage("§c[Cannons] Impossibile sparare. Controlla munizioni nelle chest o il cooldown dei cannoni.");
            }
        }
    }
}
