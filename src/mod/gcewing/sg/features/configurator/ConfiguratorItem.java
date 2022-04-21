package gcewing.sg.features.configurator;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.SGCraft;
import gcewing.sg.features.configurator.network.ConfiguratorNetworkHandler;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

import javax.annotation.Nullable;

public class ConfiguratorItem extends Item {

    private SGBaseTE toLinkTE = null;

  public ConfiguratorItem() {}

  @SideOnly(Side.CLIENT)
  @Override
  public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, ITooltipFlag advanced) {
    super.addInformation(stack, player, tooltip, advanced);
  }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
      if (!world.isRemote && player.isSneaking()) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof DHDTE) {
                if (toLinkTE != null) {
                    ((DHDTE) te).linkToStargate(toLinkTE);
                    sendBasicMsg(player, "configuratorLink");
                    toLinkTE = null;
                }
            }
            if (te instanceof SGBaseTE) {
                toLinkTE = (SGBaseTE)te;
                sendBasicMsg(player, "gateLocationSaved");
            }
        }

        return EnumActionResult.PASS;
    }


  @Override
  public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer player, EnumHand handIn) {
      if (!worldIn.isRemote && !player.isSneaking()) {
          TileEntity localGateTE = GateUtil.locateLocalGate(worldIn, new BlockPos(player.posX, player.posY, player.posZ), SGCraft.toolSearchRange, false);

          if (localGateTE instanceof SGBaseTE) {
              SGBaseTE localGate = (SGBaseTE) localGateTE;

              if (localGate.homeAddress == null) {
                  // Fallback since the Gate doesn't have an established address until its merged.
                  return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(handIn));
              }

              boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

              if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && localGate.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
                  ConfiguratorNetworkHandler.openGuiAtClient(localGate, player, 1, isPermissionsAdmin,
                      SGCraft.hasPermission(player, "sgcraft.configurator.secondsToStayOpen"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.ringRotationSpeed"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.maxEnergyBuffer"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.energyPerFuelItem"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.gateOpeningsPerFuelItem"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.distanceFactorMultiplier"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.interDimensionalMultiplier"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.oneWayTravel"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.hasIrisUpgrade"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.hasChevronUpgrade"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.gateType"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.reverseWormholeKills"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.closeFromEitherEnd"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.preserveInventory"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.noPowerRequired"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.chevronsLockOnDial"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.returnToPreviousIrisState"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.transientDamage"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.transparency"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.useDHDFuelSource"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.allowRedstoneOutput"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.allowRedstoneInput"),
                      SGCraft.hasPermission(player, "sgcraft.gui.gateAccessMenu") && localGate.allowAdminAccess(player.getName()),
                      SGCraft.hasPermission(player, "sgcraft.gui.playerAccessMenu") && localGate.allowAdminAccess(player.getName()),
                      SGCraft.hasPermission(player, "sgcraft.configurator.canPlayerBreakPlayerGate"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.canPlayerBreakGeneratedGate"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.displayGateAddress"),
                      SGCraft.hasPermission(player, "sgcraft.configurator.isGenerated"));

              } else {
                  if (!SGCraft.hasPermission(player, "sgcraft.gui.configurator"))
                      sendErrorMsg(player, "configuratorPermission");
                  if (!localGate.allowAdminAccess(player.getName()))
                      sendErrorMsg(player, "insufficientPlayerAdminAccessPermission");

                  return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(handIn));
              }

              return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));  //Both Server & Client expect a returned value.
          } else {
              sendErrorMsg(player, "cantFindStargate");
              return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(handIn));
          }
      }

      return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));  //Both Server & Client expect a returned value.
  }
}
