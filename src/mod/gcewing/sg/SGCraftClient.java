//------------------------------------------------------------------------------------------------
//
//   SG Craft - Client Proxy
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import gcewing.sg.features.cityshield.CityShieldTE;
import gcewing.sg.features.obelisk.client.ObeliskBeamEffects;
import gcewing.sg.features.zat.client.ZatBolts;
import gcewing.sg.features.obelisk.AsgardObeliskTE;
import gcewing.sg.features.obelisk.client.AsgardObeliskRenderer;
import net.minecraftforge.common.MinecraftForge;
import gcewing.sg.features.cityshield.client.CityShieldRenderer;

// import gcewing.sg.ic2.*; //[IC2]

import gcewing.sg.client.gui.DHDFuelScreen;
import gcewing.sg.client.gui.DHDScreen;
import gcewing.sg.client.gui.PowerScreen;
import gcewing.sg.client.gui.SGBaseScreen;
import gcewing.sg.client.gui.SGGui;
import gcewing.sg.client.renderer.DHDTERenderer;
import gcewing.sg.client.renderer.IrisRenderer;
import gcewing.sg.client.renderer.SGBaseTERenderer;
import gcewing.sg.entity.EntityStargateIris;
import gcewing.sg.features.ic2.zpm.interfacecart.ZPMInterfaceCartScreen;
import gcewing.sg.features.ic2.zpm.modulehub.ZpmHubScreen;
import gcewing.sg.features.zpm.console.ZPMConsoleScreen;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;

public class SGCraftClient extends BaseModClient<SGCraft> {

    public SGCraftClient(SGCraft mod) {
        super(mod);
        //debugSound = true;
        //debugModelRegistration = true;
    }
    
    @Override
    protected void registerScreens() {
        // The white-out and sound of an Asgard obelisk beaming the player
        MinecraftForge.EVENT_BUS.register(ObeliskBeamEffects.class);
        // The zat's bolts of lightning
        MinecraftForge.EVENT_BUS.register(ZatBolts.class);
        //System.out.printf("SGCraft: ProxyClient.registerScreens\n");
        addScreen(SGGui.SGBase, SGBaseScreen.class);
        addScreen(SGGui.SGController, DHDScreen.class);
        addScreen(SGGui.DHDFuel, DHDFuelScreen.class);
        addScreen(SGGui.PowerUnit, PowerScreen.class);
        addScreen(SGGui.ZPMInterfaceCart, ZPMInterfaceCartScreen.class);
        addScreen(SGGui.ZPMConsole, ZPMConsoleScreen.class);
        addScreen(SGGui.ZPMHub, ZpmHubScreen.class);
    }

    @Override
    protected void registerTileEntityRenderers() {
        addTileEntityRenderer(SGBaseTE.class, new SGBaseTERenderer());
        addTileEntityRenderer(DHDTE.class, new DHDTERenderer());
        addTileEntityRenderer(AsgardObeliskTE.class, new AsgardObeliskRenderer());
        if (BaseMod.isModLoaded("malisiscore")) {
            addTileEntityRenderer(CityShieldTE.class, new CityShieldRenderer());
        }
    }
    
    @Override
    protected void registerEntityRenderers() {
        addEntityRenderer(EntityStargateIris.class, IrisRenderer.class);
    }

}
