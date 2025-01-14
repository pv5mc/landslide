package me.pv5mc.landslide;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

import me.pv5mc.dhutils.Debugger;
import me.pv5mc.dhutils.MiscUtil;

import java.util.Random;

/**
 * Note that all listener methods run with EventPriority.HIGHEST, giving other plugins a chance
 * to cancel the various events first.
 */
public class EventListener implements Listener {

    private final LandslidePlugin plugin;

    // sounds to play at random when blocks land
    private static final Sound[] landingSounds = new Sound[]{
            Sound.STEP_GRAVEL,
            Sound.STEP_SAND,
            Sound.STEP_STONE,
            Sound.DIG_GRASS,
            Sound.DIG_GRAVEL,
            Sound.DIG_SAND,
    };

    public EventListener(LandslidePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
            return;
        }
        checkForSlide(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockLanded(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
            return;
        }

        Block block = event.getBlock();
        FallingBlock fb = (FallingBlock) event.getEntity();

        if (block.getType() == fb.getMaterial()) {
            // looks like a block starting to fall - we don't care about that here
            return;
        }
        if (!block.getType().isBlock()) {
            // looks like a non-vanilla block (are we running MCPC+ maybe?)
            event.setCancelled(true);
            if (plugin.getPerWorldConfig().getDropItems(block.getWorld())) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(fb.getMaterial(), 1, fb.getBlockData()));
            }
            return;
        }
        if (block.getType() == Material.AIR && block.getRelative(BlockFace.DOWN).getType() == Material.SNOW) {
            // trying to land on a thick snow layer - doesn't work well, so just drop an item
            event.setCancelled(true);
            if (plugin.getPerWorldConfig().getDropItems(block.getWorld())) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(fb.getMaterial(), 1, fb.getBlockData()));
            }
            return;
        }
        if (block.getType() == Material.SNOW && (fb.getMaterial() == Material.SNOW || fb.getMaterial() == Material.SNOW_BLOCK)) {
            plugin.getSnowHandler().handleSnowAccumulation(block, fb);
        }

        Debugger.getInstance().debug(2, "falling block landed! " + fb.getMaterial() + " -> " + block);
        if (checkForSlide(block, event.getTo(), event.getData(), true, plugin.getPerWorldConfig().getFallingBlocksBounce(fb.getWorld()))) {
            // the block continues to slide - don't waste time forming a true block
            // (checkForSlide() will have already created a new FallingBlock entity)
            event.setCancelled(true);
        } else {
            // the block has landed
            if (plugin.getRandom().nextInt(100) < plugin.getPerWorldConfig().getExplodeEffectChance(fb.getWorld())) {
                if (fb.getMaterial() == Material.SNOW || fb.getMaterial() == Material.SNOW_BLOCK) {
                    // snow falling is nice and quiet!
                    fb.getWorld().playSound(fb.getLocation(), Sound.STEP_SNOW, 2.0f, 0.5f);
                } else if (SlideManager.isSolid(fb.getMaterial())) {
                    fb.getWorld().playSound(fb.getLocation(), landingSounds[plugin.getRandom().nextInt(landingSounds.length)], 2.0f, 0.5f);
                }
            }
        }

        // See if the block we landed on can be dislodged; but only "heavy" (aka solid) falling blocks will dislodge blocks they land on
        if (SlideManager.isSolid(fb.getMaterial())) {
            checkForSlide(block.getRelative(BlockFace.DOWN));
        }

        // anything living standing in the way?
        int dmg = plugin.getPerWorldConfig().getFallingBlockDamage(fb.getWorld());
        if (dmg > 0 && SlideManager.isSolid(fb.getMaterial())) {
            Location loc = block.getLocation();
            for (Entity e : loc.getChunk().getEntities()) {
                if (e instanceof LivingEntity) {
                    e.getLocation(loc);
                    if (loc.getBlockX() == block.getX() && loc.getBlockY() == block.getY() && loc.getBlockZ() == block.getZ()) {
                        ((LivingEntity) e).damage(dmg);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void vanillaBlockFalling(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType().hasGravity() && event.getEntity() instanceof FallingBlock) {
            FallingBlock fb = (FallingBlock) event.getEntity();
            if (block.getType() == fb.getMaterial()) {
                int chanceToSlide = plugin.getPerWorldConfig().getSlideChance(block.getWorld(), new MaterialData(block.getType(), block.getData()));
                if (plugin.getRandom().nextInt(100) > chanceToSlide) {
                    event.setCancelled(true);
                }
                if (plugin.getSlideManager().wouldSlide(block) == null) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSnowItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (item.getItemStack().getType() == Material.SNOW || item.getItemStack().getType() == Material.SNOW_BLOCK) {
            short thickness = item.getItemStack().getType() == Material.SNOW ? item.getItemStack().getDurability() : 7;
            Block b = item.getLocation().getBlock();
            if (b.getType() == Material.AIR) {
                // item could spawn in the block above a thick (thickness >= 6) snow layer
                b = b.getRelative(BlockFace.DOWN);
            }
            if (b.getType() == Material.SNOW) {
                byte newThickness = (byte) (b.getData() + thickness + 1);
                if (newThickness > 7) {
                    b.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
                    b.getRelative(BlockFace.UP).setTypeIdAndData(Material.SNOW.getId(), (byte) (newThickness - 8), true);
                } else if (newThickness == 7) {
                    b.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
                } else {
                    b.setData(newThickness);
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
            return;
        }
        Debugger.getInstance().debug(2, "Block physics: " + event.getBlock());
        checkForSlide(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!explosionEventApplies(event.getEntity())) {
            return;
        }

        // we don't drop items; instead any affected blocks have a chance to become a (high-speed) falling block
        event.setCancelled(true);

        Location centre = event.getLocation();

        double distMax0 = 0.0;
        for (Block b : event.blockList()) {
            distMax0 = Math.max(distMax0, b.getLocation().distanceSquared(centre));
        }

        // work out a good direction to bias the block flinging - try to send them towards open air
        Block centreBlock = centre.getBlock();
        if (!SlideManager.isSolid(centreBlock.getType())) {
            centreBlock = findAdjacentSolid(centreBlock);
            centre = centreBlock.getLocation();
        }
        Random rnd = plugin.getRandom();
        Vector dirModifier = new Vector(rnd.nextDouble() * 0.5, rnd.nextDouble() * 0.5, rnd.nextDouble() * 0.5);
        for (BlockFace face : LandslidePlugin.allFaces) {
            Block b1 = centreBlock.getRelative(face);
            if (!SlideManager.isSolid(b1.getRelative(face).getType()) && SlideManager.isSolid(b1.getRelative(face.getOppositeFace()).getType())) {
                dirModifier.add(new Vector(face.getModX(), face.getModY(), face.getModZ()));
            }
        }

        final double distMax = Math.sqrt(distMax0);
        final double forceMult = plugin.getConfig().getDouble("explosions.force_mult", 1.0);
        final int yieldChance = plugin.getConfig().getInt("explosions.yield_chance", 50);
        boolean primeTNT = plugin.getConfig().getBoolean("explosions.prime_tnt", true);

        Debugger.getInstance().debug("explosion: cause = " + event.getEntity() + ", " + event.blockList().size() + " blocks affected, radius = " + distMax);
        for (Block b : event.blockList()) {
            if (!SlideManager.isSolid(b.getType())) {
                // maybe some other plugin has already changed this block (e.g. CreeperHeal?)
                continue;
            }
            if (b.getType() == Material.TNT && primeTNT) {
                b.setType(Material.AIR);
                Entity tnt = b.getWorld().spawn(b.getLocation(), TNTPrimed.class);
                ((TNTPrimed) tnt).setFuseTicks(20);
                continue;
            }
            if (rnd.nextInt(100) > yieldChance) {
                b.setType(Material.AIR);
                continue;
            }
            double xOff = b.getX() - centre.getBlockX();
            double yOff = b.getY() - centre.getBlockY();
            double zOff = b.getZ() - centre.getBlockZ();
            if (xOff == 0 && yOff == 0 && zOff == 0) {
                yOff = 0.1;  // can't have a zero vector here; it won't normalize properly
            }
            double dist = Math.sqrt(xOff * xOff + yOff * yOff + zOff * zOff);
            double power = Math.abs(distMax - dist) / 3.0;
            Vector vec = new Vector(xOff, yOff, zOff).normalize().multiply(forceMult * power);
            plugin.getSlideManager().scheduleBlockFling(b, vec.clone(), dirModifier.clone());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        SlideOTron wand = SlideOTron.getWand(player);
        if (wand == null) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("landslide.use.wand")) {
            player.getWorld().createExplosion(player.getEyeLocation().add(player.getLocation().getDirection().normalize()), 0.0f);
            Vector vel = player.getLocation().getDirection().normalize().multiply(-5.0);
            vel.setY(1.2);
            player.setVelocity(vel);
            player.damage(4);
            MiscUtil.errorMessage(player, "The Slide-O-Tron\u2122 explodes in your face!");
            player.setItemInHand(null);
            return;
        }
        int quantity = player.getItemInHand().getAmount();
        switch (event.getAction()) {
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
                wand.activate(plugin, player);
                break;
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                wand.setMode(wand.getMode().nextMode());
                player.setItemInHand(wand.toItemStack(quantity));
                break;
            default:
                break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void mouseWheel(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        SlideOTron wand = SlideOTron.getWand(player);
        if (wand == null) {
            return;
        }
        event.setCancelled(true);
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta == 0) {
            return;
        } else if (delta >= 6) {
            delta -= 9;
        } else if (delta <= -6) {
            delta += 9;
        }
        wand.setPower(wand.getPower() - delta);
        player.setItemInHand(wand.toItemStack(player.getItemInHand().getAmount()));
    }

    private Block findAdjacentSolid(Block b) {
        for (BlockFace face : LandslidePlugin.allFaces) {
            Block b1 = b.getRelative(face);
            if (SlideManager.isSolid(b.getType())) {
                return b1;
            }
        }
        return b;
    }

    private boolean explosionEventApplies(Entity e) {
        if (e instanceof Creeper && plugin.getConfig().getBoolean("fancy_explosions.creeper")) {
            return true;
        } else if (e instanceof TNTPrimed && plugin.getConfig().getBoolean("fancy_explosions.tnt")) {
            return true;
        } else if (e instanceof EnderDragon && plugin.getConfig().getBoolean("fancy_explosions.enderdragon")) {
            return true;
        } else if (plugin.getConfig().getBoolean("fancy_explosions.other")) {
            return true;
        }
        return false;
    }

    private boolean checkForSlide(Block block, Material mat, byte data, boolean immediate, boolean justLanded) {
        World world = block.getWorld();
        Debugger.getInstance().debug(2, "check for slide: " + block + " immediate=" + immediate + " justLanded=" + justLanded);

        MaterialData matData = new MaterialData(mat, data);
        int slideChance = plugin.getPerWorldConfig().getSlideChance(world, matData);
        int dropChance = plugin.getPerWorldConfig().getDropChance(world, matData);
        int bounceChance = plugin.getConfig().getInt("bounce_chance");
        if (justLanded && bounceChance <= plugin.getRandom().nextInt(100)) {
            return false;
        }
        boolean orphan = plugin.isOrphan(block);
        boolean weatherStopsSlide = plugin.getPerWorldConfig().getOnlyWhenRaining(world) && !world.hasStorm();

        if ((slideChance > 0 || dropChance > 0) && orphan && plugin.getConfig().getBoolean("drop_slidy_floaters")) {
            return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
        } else if (orphan && plugin.getConfig().getBoolean("drop_nonslidy_floaters")) {
            return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
        }
        if (slideChance > plugin.getRandom().nextInt(100) && !weatherStopsSlide || justLanded && slideChance > 0) {
            if (block.getType() == Material.SNOW) {
                // special case; snow can slide off in layers, and the minimum thickness is configurable
                if (block.getData() < plugin.getPerWorldConfig().getSnowSlideThickness(world) - 1) {
                    return false;
                }
            }
            if (plugin.getPerWorldConfig().getOnlyWhenRaining(world) && !world.hasStorm()) {
                return false;
            }
            BlockFace face = plugin.getSlideManager().wouldSlide(block);
            // sand/gravel/anvil dropping down will be handled by vanilla mechanics
            if (face != null && (face != BlockFace.DOWN || !block.getType().hasGravity())) {
                return plugin.getSlideManager().scheduleBlockSlide(block, face, mat, data, immediate);
            }
        } else if (dropChance > plugin.getRandom().nextInt(100)) {
            BlockFace face = plugin.getSlideManager().wouldSlide(block);
            if (face == BlockFace.DOWN) {
                return plugin.getSlideManager().scheduleBlockSlide(block, face, mat, data, immediate);
            }
        }
        return false;
    }

    private boolean checkForSlide(Block block) {
        return checkForSlide(block, block.getType(), block.getData(), false, false);
    }

}
