package me.pv5mc.landslide;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.material.MaterialData;

import me.pv5mc.dhutils.LogUtils;

import java.util.HashMap;
import java.util.Map;

public class BlockTransform {
    private final Map<MaterialData, MaterialData> map = new HashMap<MaterialData, MaterialData>();

    public BlockTransform() {
    }

    public void clear() {
        map.clear();
    }

    public void processConfig(ConfigurationSection cs) {
        clear();

        for (String m1 : cs.getKeys(false)) {
            String m2 = cs.getString(m1);
            add(m1, m2);
        }
    }

    public void add(String s1, String s2) {
        MaterialData mat1 = LandslidePlugin.parseMaterialData(s1);
        MaterialData mat2 = s2.isEmpty() ? mat1 : LandslidePlugin.parseMaterialData(s2);
        if (mat1 == null || mat2 == null) {
            LogUtils.warning("invalid material transform: " + s1 + " -> " + s2);
        } else {
            if (mat2.getData() == (byte) -1) {
                mat2.setData((byte) 0);
            }
            add(mat1, mat2);
        }
    }

    public void add(MaterialData m1, MaterialData m2) {
        if (m1 == m2) {
            map.remove(m1);
        } else {
            map.put(m1, m2);
        }
    }

    public MaterialData get(MaterialData m) {
        MaterialData res = map.get(m);
        if (res == null) {
            res = map.get(new MaterialData(m.getItemType(), (byte) -1));
        }
        return res;
    }
}
