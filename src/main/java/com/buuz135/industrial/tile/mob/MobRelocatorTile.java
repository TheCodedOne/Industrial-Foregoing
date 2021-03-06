package com.buuz135.industrial.tile.mob;

import com.buuz135.industrial.IndustrialForegoing;
import com.buuz135.industrial.item.addon.AdultFilterAddonItem;
import com.buuz135.industrial.proxy.BlockRegistry;
import com.buuz135.industrial.proxy.FluidsRegistry;
import com.buuz135.industrial.tile.CustomColoredItemHandler;
import com.buuz135.industrial.tile.WorkingAreaElectricMachine;
import com.buuz135.industrial.tile.api.IAcceptsAdultFilter;
import com.buuz135.industrial.utils.ItemStackUtils;
import com.buuz135.industrial.utils.WorkUtils;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.ndrei.teslacorelib.inventory.BoundingRectangle;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MobRelocatorTile extends WorkingAreaElectricMachine implements IAcceptsAdultFilter {

    private IFluidTank outExp;
    private ItemStackHandler outItems;

    public MobRelocatorTile() {
        super(MobRelocatorTile.class.getName().hashCode());
    }

    @Override
    protected void initializeInventories() {
        super.initializeInventories();
        outExp = this.addFluidTank(FluidsRegistry.ESSENCE, 8000, EnumDyeColor.LIME, "Experience tank", new BoundingRectangle(50, 25, 18, 54));
        outItems = new ItemStackHandler(12) {
            @Override
            protected void onContentsChanged(int slot) {
                MobRelocatorTile.this.markDirty();
            }
        };
        this.addInventory(new CustomColoredItemHandler(outItems, EnumDyeColor.ORANGE, "Mob drops", 18 * 5 + 3, 25, 4, 3) {
            @Override
            public boolean canInsertItem(int slot, ItemStack stack) {
                return false;
            }

            @Override
            public boolean canExtractItem(int slot) {
                return true;
            }

        });
        this.addInventoryToStorage(outItems, "mob_relocator_out");
    }

    @Override
    public void protectedUpdate() {
        super.protectedUpdate();
        if (this.world.isRemote) return;
        this.getWorld().getEntitiesWithinAABB(EntityXPOrb.class, getWorkingArea().expand(2, 2, 2)).stream().filter(entityXPOrb -> !entityXPOrb.isDead).forEach(entityXPOrb -> {
            if (this.outExp.fill(new FluidStack(FluidsRegistry.ESSENCE, (int) (entityXPOrb.getXpValue() * 20 * BlockRegistry.mobRelocatorBlock.getEssenceMultiplier())), false) > 0) {
                this.outExp.fill(new FluidStack(FluidsRegistry.ESSENCE, (int) (entityXPOrb.getXpValue() * 20 * BlockRegistry.mobRelocatorBlock.getEssenceMultiplier())), true);
                entityXPOrb.setDead();
                this.forceSync();
            } else if (entityXPOrb.xpOrbAge < 4800) {
                entityXPOrb.xpOrbAge = 4800;
            }
        });
    }

    @Override
    public float work() {
        if (WorkUtils.isDisabled(this.getBlockType())) return 0;

        AxisAlignedBB area = getWorkingArea();
        List<EntityLiving> mobs = this.getWorld().getEntitiesWithinAABB(EntityLiving.class, area);
        if (mobs.size() == 0) return 0;
        FakePlayer player = IndustrialForegoing.getFakePlayer(world, pos);
        AtomicBoolean hasWorked = new AtomicBoolean(false);
        mobs.stream().filter(entityLiving -> !hasAddon() || (!(entityLiving instanceof EntityAgeable) || !entityLiving.isChild())).forEach(entityLiving -> {
            entityLiving.attackEntityFrom(new EntityDamageSource("mob_crusher", player) {
                @Override
                public ITextComponent getDeathMessage(EntityLivingBase entityLivingBaseIn) {
                    return new TextComponentTranslation("text.industrialforegoing.chat.crusher_kill", entityLivingBaseIn.getDisplayName().getFormattedText(), TextFormatting.RESET);
                }
            }, Integer.MAX_VALUE);
            hasWorked.set(true);
        });
        List<EntityItem> items = this.getWorld().getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem item : items) {
            if (!item.getItem().isEmpty() && !item.isDead) {
                if (ItemHandlerHelper.insertItem(outItems, item.getItem(), true).isEmpty()) {
                    ItemHandlerHelper.insertItem(outItems, item.getItem(), false);
                    item.setDead();
                } else {
                    item.lifespan = 20 * 60;
                }
            }
        }
        return hasWorked.get() ? 1 : 0;
    }


    @Override
    protected boolean acceptsFluidItem(ItemStack stack) {
        return ItemStackUtils.acceptsFluidItem(stack);
    }

    @Override
    protected void processFluidItems(ItemStackHandler fluidItems) {
        ItemStackUtils.processFluidItems(fluidItems, outExp);
    }

    @Override
    public boolean hasAddon() {
        return this.hasAddon(AdultFilterAddonItem.class);
    }
}
