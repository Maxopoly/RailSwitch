package sh.okx.railswitch.switches;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import sh.okx.railswitch.glue.CitadelGlue;
import sh.okx.railswitch.settings.SettingsManager;
import vg.civcraft.mc.civmodcore.api.BlockAPI;
import vg.civcraft.mc.civmodcore.api.EntityAPI;

/**
 * Switch listener that implements switch functionality.
 */
public class SwitchListener implements Listener {

    public static final String WILDCARD = "*";

    public static final CitadelGlue CITADEL_GLUE = new CitadelGlue();

    /**
     * Event handler for rail switches. Will determine if a switch exists at the target location, and if so will process
     * it accordingly, allowing it to trigger or not trigger depending on the rider's set destination, the listed
     * destinations on the switch, and the switch type.
     *
     * @param event The block redstone event to base the switch's existence on.
     */
    @EventHandler
    public void onSwitchTrigger(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        // Block must be a detector rail being triggered
        if (!BlockAPI.isValidBlock(block)
                || block.getType() != Material.DETECTOR_RAIL
                || event.getNewCurrent() != 15) {
            return;
        }
        // Check that the block above the rail is a sign
        Block above = block.getRelative(BlockFace.UP);
        if (!Tag.SIGNS.isTagged(above.getType())
                || !(above.getState() instanceof Sign)) {
            return;
        }
        // Check that the sign has a valid switch type
        String[] lines = ((Sign) above.getState()).getLines();
        SwitchType type = SwitchType.find(lines[0]);
        if (type == null) {
            return;
        }
        // Check that a player is triggering the switch
        // NOTE: The event doesn't provide the information and so the next best thing is searching for a
        //       player who is nearby and riding a minecart.
        Player player = null; {
            double searchDistance = Double.MAX_VALUE;
            for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 3, 3, 3)) {
                if (!EntityAPI.isPlayer(entity)) {
                    continue;
                }
                Entity vehicle = entity.getVehicle();
                // TODO: This should be abstracted into CivModCore
                if (vehicle == null
                        || vehicle.getType() != EntityType.MINECART
                        || !(vehicle instanceof Minecart)) {
                    continue;
                }
                double distance = block.getLocation().distanceSquared(entity.getLocation());
                if (distance < searchDistance) {
                    searchDistance = distance;
                    player = (Player) entity;
                }
            }
        }
        if (player == null) {
            return;
        }
        // If Citadel is enabled, check that the sign and the rail are on the same group
        if (CITADEL_GLUE.isEnabled()) {
            if (!CITADEL_GLUE.doSignAndRailHaveSameReinforcement(above, block)) {
                return;
            }
        }
        // Determine whether a player has a destination that matches one of the destinations
        // listed on the switch signs, or match if there's a wildcard.
        boolean matched = false;
        String[] playerDestinations = SettingsManager.getDestination(player).split(" ");
        if (playerDestinations.length > 0) {
            String[] switchDestinations = Arrays.copyOfRange(lines, 1, lines.length);
            matcher:
            for (String playerDestination : playerDestinations) {
                if (playerDestination.equals(WILDCARD)) {
                    matched = true;
                    break;
                }
                for (String switchDestination : switchDestinations) {
                    if (switchDestination.equals(WILDCARD)
                            || playerDestination.equalsIgnoreCase(switchDestination)) {
                        matched = true;
                        break matcher;
                    }
                }
            }
        }
        switch (type) {
            case NORMAL:
                event.setNewCurrent(matched ? 15 : 0);
                break;
            case INVERTED:
                event.setNewCurrent(matched ? 0 : 15);
                break;
        }
    }

}
