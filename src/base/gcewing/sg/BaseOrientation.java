//------------------------------------------------------------------------------------------------
//
//   Greg's Mod Base for 1.8 - Block orientation handlers
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import gcewing.sg.BaseBlock.IOrientationHandler;

public class BaseOrientation {

    public static boolean debugPlacement = false;

    public static IOrientationHandler orient4WaysByState = new Orient4WaysByState();

    //------------------------------------------------------------------------------------------------

    public static class PropertyTurn extends PropertyEnum<EnumFacing> {

        protected static EnumFacing[] values = {
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH
        };
        
        protected static Collection<EnumFacing> valueList = Arrays.asList(values);
        
        public PropertyTurn(String name) {
            super(name, EnumFacing.class, valueList);
        }
    
    }

    //------------------------------------------------------------------------------------------------

    public static class Orient4WaysByState implements IOrientationHandler {
    
        public static IProperty<EnumFacing> FACING = new PropertyTurn("facing");
    
        public void defineProperties(BaseBlock block) {
            block.addProperty(FACING);
        }
        
        public IBlockState onBlockPlaced(Block block, World world, BlockPos pos, EnumFacing side, 
            float hitX, float hitY, float hitZ, IBlockState baseState, EntityLivingBase placer)
        {
            EnumFacing dir = placer.getHorizontalFacing();
            if (debugPlacement)
                System.out.printf("BaseOrientation.Orient4WaysByState: Placing block with FACING = %s\n", dir);
            return baseState.withProperty(FACING, dir);
        }
        
        public Trans3 localToGlobalTransformation(IBlockAccess world, BlockPos pos, IBlockState state, Vector3 origin) {
            EnumFacing f = state.getValue(FACING);
            int i;
            switch (f) {
                case NORTH: i = 0; break;
                case WEST: i = 1; break;
                case SOUTH: i = 2; break;
                case EAST: i = 3; break;
                default: i = 0;
            }
            return new Trans3(origin).turn(i);
        }

    }

//------------------------------------------------------------------------------------------------

}
