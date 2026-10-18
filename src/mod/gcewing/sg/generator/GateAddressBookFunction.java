package gcewing.sg.generator;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import gcewing.sg.util.SGAddressing;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;

import java.util.Random;

/**
 * Loot function "sgcraft:gate_address_book": turns the stack into a written book holding the address of one random
 * world-generated stargate from generator.yml. When the world has no generated gates the stack is emptied, so no book
 * is placed.
 */
public class GateAddressBookFunction extends LootFunction {

    public GateAddressBookFunction(LootCondition[] conditions) {
        super(conditions);
    }

    @Override
    public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
        World world = context.getWorld();
        String address = world == null ? null : GeneratorAddressRegistry.randomAddress(world, "", rand);
        if (address == null) {
            return ItemStack.EMPTY;
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "Stargate Address");
        tag.setString("author", "Unknown");
        tag.setInteger("generation", 3); // "Tattered", it was found, not written

        // A translatable component, so each player reads the page in their own language.
        ITextComponent page = new TextComponentTranslation("sgcraft.book.gateAddress.page")
                .appendText("\n\n")
                .appendSibling(new TextComponentString(SGAddressing.formatAddress(address, "-", "-")).setStyle(new Style().setBold(true)));
        NBTTagList pages = new NBTTagList();
        pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(page)));
        tag.setTag("pages", pages);
        tag.setBoolean("resolved", true);

        book.setTagCompound(tag);
        return book;
    }

    public static class Serializer extends LootFunction.Serializer<GateAddressBookFunction> {

        public Serializer() {
            super(new ResourceLocation("sgcraft", "gate_address_book"), GateAddressBookFunction.class);
        }

        @Override
        public void serialize(JsonObject object, GateAddressBookFunction function, JsonSerializationContext context) {
        }

        @Override
        public GateAddressBookFunction deserialize(JsonObject object, JsonDeserializationContext context, LootCondition[] conditions) {
            return new GateAddressBookFunction(conditions);
        }
    }
}
