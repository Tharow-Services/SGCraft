package gcewing.sg.features.zpm;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.List;

public class ZPMItem extends Item {
  public static final String ENERGY = "AvailableEnergy";
  public static final String LOADED = "loadedIntoCart";
  final static DecimalFormat dFormat = new DecimalFormat("###,###,###,##0");

  public ZPMItem() {}

  // A tainted ZPM is one with the Infinity enchantment (id 51), as generated gates' chests sometimes hold
  public static boolean isTainted(ItemStack stack) {
    NBTTagList enchantments = stack.getEnchantmentTagList();
    for (int i = 0; i < enchantments.tagCount(); i++) {
      if (enchantments.getCompoundTagAt(i).getShort("id") == 51) {
        return true;
      }
    }
    return false;
  }
  // Nothing here... yet.

  @SideOnly(Side.CLIENT)
  @Override
  public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, ITooltipFlag advanced) {
    super.addInformation(stack, player, tooltip, advanced);

    final NBTTagCompound compound = stack.getTagCompound();
    if (compound != null) {
      if (compound.hasKey(ZPMItem.LOADED, 99 /* number */)) {
        // A tainted ZPM shows only "Infinity" (its enchantment), to fool the player: never its power
        if (!compound.getBoolean(ZPMItem.LOADED) && !isTainted(stack)) {
          tooltip.add("Power: " + dFormat.format(compound.getDouble(ZPMItem.ENERGY)));
        }
      }
      return;
    }
  }
}
