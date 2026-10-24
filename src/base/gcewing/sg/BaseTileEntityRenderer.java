//------------------------------------------------------------------------------------------------
//
//   Greg's Mod Base - Generic Tile Entity Renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.tileentity.*;

import gcewing.sg. BaseModClient.IRenderTarget;

public abstract class BaseTileEntityRenderer extends TileEntitySpecialRenderer<BaseTileEntity> {

    protected static BaseGLRenderTarget target = new BaseGLRenderTarget();

    @Override
    public void render(BaseTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        Trans3 t = te.localToGlobalTransformation(Vector3.blockCenter(x, y, z));
        target.start(true);
        render(te, partialTicks, destroyStage, t, target);
        target.finish();
    }
    
    public void render(BaseTileEntity te, float dt, int destroyStage,
        Trans3 t, IRenderTarget target) {}

}
