package gcewing.sg.features.naquadahgenerator;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.SGCraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;

// Naquadah generators (SG-1, SGA): Tau'ri reactors making power from refined naquadah, for stargates (and their shields)
// within reach, and for other mods' machines as Forge Energy. Energy is in gate energy units (as gates use it) unless
// said otherwise; Forge Energy is SGCraft.FPPerSGEnergyUnit times that.
public final class NaquadahGenerators {

    // The two marks: the Mark II puts out six times the power, but runs in a barely controlled overload, getting less
    // from its fuel
    public enum Mark {
        I(4, 20000, 1.0),
        II(24, 60000, 0.5);

        // Energy it makes a tick while running, how much it holds ready, and the share of its fuel's energy it gets
        public double energyPerTick, capacity, efficiency;

        Mark(double energyPerTick, double capacity, double efficiency) {
            this.energyPerTick = energyPerTick;
            this.capacity = capacity;
            this.efficiency = efficiency;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    // What its fuel is worth: raw naquadah as much as a DHD gets from it, naquadah alloy ingots four times that
    public static double naquadahEnergy = 96000;
    public static double ingotEnergy = 384000;

    public static SoundEvent humSound;

    private NaquadahGenerators() {}

    public static void registerSounds(SGCraft mod) {
        humSound = mod.newSound("naquadah_generator_hum");
    }

    public static void configure(BaseConfiguration cfg) {
        naquadahEnergy = Math.max(0, cfg.getDouble("naquadah-generator", "naquadahEnergy", naquadahEnergy));
        ingotEnergy = Math.max(0, cfg.getDouble("naquadah-generator", "ingotEnergy", ingotEnergy));
        for (Mark mark : Mark.values()) {
            String m = "mark" + mark.name();
            mark.energyPerTick = Math.max(0, cfg.getDouble("naquadah-generator", m + "EnergyPerTick", mark.energyPerTick));
            mark.capacity = Math.max(1, cfg.getDouble("naquadah-generator", m + "Capacity", mark.capacity));
            mark.efficiency = Math.max(0, cfg.getDouble("naquadah-generator", m + "Efficiency", mark.efficiency));
        }
    }

    // What a fuel item is worth (one of them), or 0 if it isn't fuel
    public static double fuelEnergy(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() == SGCraft.naquadah) {
            return naquadahEnergy;
        }
        if (stack.getItem() == SGCraft.naquadahIngot) {
            return ingotEnergy;
        }
        return 0;
    }
}
