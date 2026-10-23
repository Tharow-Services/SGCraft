//------------------------------------------------------------------------------------------------
//
//   SG Craft - The names players know worlds by
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

// Under Sponge each world has its own name, but only a loaded world can be asked for it: one not loaded used to be
// shown by its kind of dimension, which for Sponge's extra worlds is "overworld". So each world's name is kept as it
// loads (they all load at startup unless set not to), in config/SGCraft/world-names.properties, to name it while it
// isn't loaded.
public final class WorldNames {

    private static final Path FILE = Paths.get(".", "config", "SGCraft", "world-names.properties");
    private static Map<Integer, String> known; // by dimension, read from the file when first needed

    private WorldNames() {}

    // Notes the name of a world that has loaded on the server
    public static void record(World world) {
        if (world == null || world.isRemote) {
            return;
        }
        String name = world.getWorldInfo().getWorldName();
        int dimension = world.provider.getDimension();
        if (name != null && !name.equals(known().put(dimension, name))) {
            save();
        }
    }

    // The name of a world, as players know it: under Sponge each has its own; under plain Forge every dimension shares
    // the save's name, so any but the overworld goes by its dimension's own name (the_nether, the_end...)
    public static String nameOf(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        WorldServer overworld = DimensionManager.getWorld(0);
        String name = world != null ? world.getWorldInfo().getWorldName() : known().get(dimension);
        String overworldName = overworld != null ? overworld.getWorldInfo().getWorldName() : known().get(0);
        if (name != null && (dimension == 0 || overworldName == null || !name.equals(overworldName))) {
            return name;
        }
        return DimensionManager.isDimensionRegistered(dimension) ? DimensionManager.getProviderType(dimension).getName()
            : "dimension " + dimension;
    }

    private static Map<Integer, String> known() {
        if (known == null) {
            known = new HashMap<>();
            if (Files.exists(FILE)) {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(FILE)) {
                    properties.load(in);
                } catch (IOException e) {
                    System.err.println("SGCraft: couldn't read " + FILE + ": " + e);
                }
                for (String key : properties.stringPropertyNames()) {
                    try {
                        known.put(Integer.parseInt(key.trim()), properties.getProperty(key));
                    } catch (NumberFormatException e) {
                        // not a dimension: ignored
                    }
                }
            }
        }
        return known;
    }

    private static void save() {
        Properties properties = new Properties();
        for (Map.Entry<Integer, String> entry : known.entrySet()) {
            properties.setProperty(entry.getKey().toString(), entry.getValue());
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "SGCraft: each world's name by dimension, to name worlds not loaded");
            }
        } catch (IOException e) {
            System.err.println("SGCraft: couldn't save " + FILE + ": " + e);
        }
    }
}
