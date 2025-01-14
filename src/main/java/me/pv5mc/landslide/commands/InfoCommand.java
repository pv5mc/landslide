package me.pv5mc.landslide.commands;

/*
This file is part of Landslide

Landslide is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Landslide is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Landslide.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.pv5mc.dhutils.DHValidate;
import me.pv5mc.dhutils.MessagePager;
import me.pv5mc.dhutils.MiscUtil;
import me.pv5mc.dhutils.commands.AbstractCommand;
import me.pv5mc.landslide.LandslidePlugin;
import me.pv5mc.landslide.PerWorldConfiguration;

public class InfoCommand extends AbstractCommand {

    public InfoCommand() {
        super("landslide info", 0, 1);
        setPermissionNode("landslide.commands.info");
        setUsage("/<command> info [<world-name>]");
    }

    private static final String BULLET = ChatColor.LIGHT_PURPLE + "\u2022 " + ChatColor.RESET;

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        World w;
        if (args.length == 0) {
            notFromConsole(sender);
            w = ((Player) sender).getWorld();
        } else {
            w = Bukkit.getWorld(args[0]);
            DHValidate.notNull(w, "Unknown world: " + args[0]);
        }

        MessagePager pager = MessagePager.getPager(sender).clear().setParseColours(true);

        LandslidePlugin lPlugin = (LandslidePlugin) plugin;
        PerWorldConfiguration pwc = lPlugin.getPerWorldConfig();
        pager.add("Landslide information for world " + ChatColor.GOLD + ChatColor.BOLD + w.getName() + ":");
        pager.add(BULLET + "Landslides are " + col((pwc.isEnabled(w) ? "enabled" : "disabled")));
        pager.add(BULLET + "Landslides can occur " + col((pwc.getOnlyWhenRaining(w) ? "only when raining" : "in any weather")));
        pager.add(BULLET + "Blocks may " + col((pwc.getHorizontalSlides(w) ? "slide horizontally off other blocks" : "only drop vertically")));
        pager.add(BULLET + "Blocks " + col((pwc.getSlideIntoLiquid(w) ? "may" : "may not")) + " slide sideways into liquids");
        pager.add(BULLET + "Cliff stability is " + col(pwc.getCliffStability(w) + "%"));
        pager.add(BULLET + "Falling blocks will " + col((pwc.getDropItems(w) ? "drop an item" : "be destroyed")) + " if they can't be placed");
        pager.add(BULLET + "Falling blocks have a " + col(pwc.getExplodeEffectChance(w) + "%") + " chance to play a sound effect on landing");
        pager.add(BULLET + "Falling blocks will " + col((pwc.getFallingBlocksBounce(w) ? "bounce down slopes" : "always stop where they land")));
        pager.add(BULLET + "Falling blocks will do " + col(pwc.getFallingBlockDamage(w)) + " damage to entities in the way");

        pager.add(BULLET + "Snow must be " + col(pwc.getSnowSlideThickness(w)) + " layers thick before it will slide");
        int check = plugin.getConfig().getInt("snow.check_interval");
        pager.add(BULLET + "Snow accumulation/melting is " + col(check > 0 ? "checked every " + check + "s" : "not checked"));
        if (check > 0) {
            int fc = pwc.getSnowFormChance(w), mc = pwc.getSnowMeltChance(w);
            int fr = pwc.getSnowFormRate(w), mr = pwc.getSnowMeltRate(w);
            String sf = fr == 1 ? "" : "s", sm = mr == 1 ? "" : "s";
            pager.add(BULLET + "Snow has a " + col(fc + "%") + " chance to accumulate by " + col(fr) + " layer" + sf + " when snowing");
            pager.add(BULLET + "Snow has a " + col(mc + "%") + " chance to evaporate by " + col(mr) + " layer" + sm + " when sunny");
            pager.add(BULLET + "Snow can " + col((plugin.getConfig().getBoolean("snow.melt_away") ? "" : "not ") + "melt away completely in the sun"));
        }

        Map<String, Integer> slideChances = getChances(plugin.getConfig(), w.getName(), "slide_chance");
        if (!slideChances.isEmpty()) {
            pager.add(BULLET + "Block slide chances:");
            for (String mat : MiscUtil.asSortedList(slideChances.keySet())) {
                pager.add("  " + BULLET + mat.toUpperCase() + ": " + col(slideChances.get(mat) + "%"));
            }
        }

        Map<String, Integer> dropChances = getChances(plugin.getConfig(), w.getName(), "drop_chance");
        if (!dropChances.isEmpty()) {
            pager.add(BULLET + "Block drop chances:");
            for (String mat : MiscUtil.asSortedList(dropChances.keySet())) {
                pager.add("  " + BULLET + mat.toUpperCase() + ": " + col(dropChances.get(mat) + "%"));
            }
        }

        Map<String, String> transforms = getTransforms(plugin.getConfig(), w.getName());
        if (!transforms.isEmpty()) {
            pager.add(BULLET + "Material transformations when blocks slide:");
            for (String mat : MiscUtil.asSortedList(transforms.keySet())) {
                pager.add("  " + BULLET + mat.toUpperCase() + " => " + col(transforms.get(mat)));
            }
        }

        pager.showPage();

        return true;
    }

    private String col(String s) {
        return ChatColor.YELLOW + s + ChatColor.RESET;
    }

    private String col(int i) {
        return ChatColor.YELLOW + Integer.toString(i) + ChatColor.RESET;
    }

    private Map<String, Integer> getChances(Configuration config, String worldName, String what) {
        Map<String, Integer> res = new HashMap<String, Integer>();
        ConfigurationSection cs = config.getConfigurationSection(what);
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                if (cs.getInt(k) > 0) {
                    res.put(k, cs.getInt(k));
                }
            }
        }

        ConfigurationSection wcs = config.getConfigurationSection("worlds." + worldName + "." + what);
        if (wcs != null) {
            for (String k : wcs.getKeys(false)) {
                if (wcs.getInt(k) > 0) {
                    res.put(k, wcs.getInt(k));
                }
            }
        }
        return res;
    }

    private Map<String, String> getTransforms(Configuration config, String worldName) {
        Map<String, String> res = new HashMap<String, String>();
        ConfigurationSection cs = config.getConfigurationSection("transform");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                if (!cs.getString(k).equalsIgnoreCase(k)) {
                    res.put(k, cs.getString(k).toUpperCase());
                }
            }
        }

        ConfigurationSection wcs = config.getConfigurationSection("worlds." + worldName + ".transform");
        if (wcs != null) {
            for (String k : wcs.getKeys(false)) {
                if (!wcs.getString(k).equalsIgnoreCase(k)) {
                    res.put(k, wcs.getString(k).toUpperCase());
                }
            }
        }
        return res;
    }
}
