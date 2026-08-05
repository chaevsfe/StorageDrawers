package com.jaquadro.minecraft.storagedrawers.core.recipe;

import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.DetachedDrawerData;
import com.jaquadro.minecraft.storagedrawers.components.item.DetachedDrawerContents;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.core.ModDataComponents;
import com.jaquadro.minecraft.storagedrawers.core.ModItems;
import com.jaquadro.minecraft.storagedrawers.core.ModRecipes;
import com.jaquadro.minecraft.storagedrawers.item.ItemDetachedDrawer;
import com.jaquadro.minecraft.storagedrawers.item.ItemUpgradeStorage;
import com.jaquadro.minecraft.storagedrawers.ModServices;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UpgradeDetachedDrawerRecipe extends CustomRecipe
{
    private static UpgradeDetachedDrawerRecipe instance;

    // Singleton: StreamCodec must hand back the same instance the MapCodec produces.
    public static UpgradeDetachedDrawerRecipe instance () {
        if (instance == null)
            instance = new UpgradeDetachedDrawerRecipe();
        return instance;
    }

    public static RecipeSerializer<UpgradeDetachedDrawerRecipe> makeSerializer () {
        return new RecipeSerializer<>(
            MapCodec.unit((Supplier<UpgradeDetachedDrawerRecipe>) UpgradeDetachedDrawerRecipe::instance),
            StreamCodec.<RegistryFriendlyByteBuf, UpgradeDetachedDrawerRecipe>of((buf, val) -> { }, buf -> instance()));
    }

    // Recipe.assemble no longer receives a HolderLookup.Provider in 26.2, but the drawer
    // NBT round-trip needs one. Vanilla always calls matches() first, so stash it there.
    private HolderLookup.Provider lastRegistries;

    public UpgradeDetachedDrawerRecipe () { }

    @Override
    public boolean matches(@NotNull CraftingInput inv, @NotNull Level world) {
        lastRegistries = world.registryAccess();
        return findContext(inv) != null;
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull CraftingInput inv) {
        HolderLookup.Provider access = lastRegistries;
        if (access == null) {
            ModServices.reportOnce("UpgradeDetachedDrawerRecipe.assemble", new IllegalStateException(
                "assemble() called without a preceding matches(); no registry access available"));
            return ItemStack.EMPTY;
        }

        Context ctx = findContext(inv);
        if (ctx == null)
            return ItemStack.EMPTY;

        ItemStack ret = ctx.drawer.copy();
        CustomData cdata = ret.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        var input = TagValueInput.create(ProblemReporter.DISCARDING, access, cdata.copyTag());
        DetachedDrawerData data = new DetachedDrawerData(input);
        int cap = data.getStorageMultiplier();

        if (ctx.upgrades.isEmpty()) {
            ret = ModItems.DETACHED_DRAWER.get().getDefaultInstance();
            data = new DetachedDrawerData();
            data.setStorageMultiplier(cap);
        } else {
            int addedCap = ctx.storageMult * ModCommonConfig.INSTANCE.DRAWERS.baseStackStorage.get()
                * ModCommonConfig.INSTANCE.DRAWERS.fullDrawers1x1.unitsPerSlot.get();
            data.setStorageMultiplier(data.getStorageMultiplier() + addedCap);
        }

        // TODO: Move away from CUSTOM_DATA
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, access);
        data.serializeNBT(output);
        ret.set(DataComponents.CUSTOM_DATA, CustomData.of(output.buildResult()));

        ItemStack savedItem = data.getStoredItemPrototype().copyWithCount(data.getStoredItemCount());
        DetachedDrawerContents contents = new DetachedDrawerContents(savedItem, cap, data.isHeavy());
        ret.set(ModDataComponents.DETACHED_DRAWER_CONTENTS.get(), contents);

        return ret;
    }

    private static class Context {
        ItemStack drawer = ItemStack.EMPTY;
        List<ItemStack> upgrades = new ArrayList<>();
        int storageMult = 0;
    }

    // Read off the component rather than the CUSTOM_DATA blob: this runs inside matches(), which
    // has no registry access of its own, and DETACHED_DRAWER_CONTENTS is written alongside the
    // blob everywhere a detached drawer is produced (BlockDrawers.pullDrawer, assemble below).
    private static boolean holdsItems (ItemStack drawer) {
        DetachedDrawerContents contents = drawer.get(ModDataComponents.DETACHED_DRAWER_CONTENTS.get());
        return contents != null && contents.getItemCount() > 0;
    }

    @Nullable
    private Context findContext(CraftingInput inv) {
        Context ret = new Context();
        for (int x = 0; x < inv.size(); x++) {
            ItemStack stack = inv.getItem(x);
            if (stack.isEmpty())
                continue;

            if (stack.getItem() instanceof ItemDetachedDrawer) {
                if (!ret.drawer.isEmpty())
                    return null;
                ret.drawer = stack;
            } else if (stack.getItem() instanceof ItemUpgradeStorage)
                ret.upgrades.add(stack);
            else
                return null;
        }

        if (ret.drawer.isEmpty())
            return null;

        // With no upgrade in the grid, assemble() takes its "normalise back to the plain
        // DETACHED_DRAWER item" branch, which rebuilds the stack from a fresh, empty
        // DetachedDrawerData. That is only ever right for a drawer holding nothing: on a
        // filled one it drops the stored stack, and since the input is consumed and nothing
        // is returned, those items are destroyed outright. Refuse the match instead.
        if (ret.upgrades.isEmpty() && holdsItems(ret.drawer))
            return null;

        for (ItemStack upgrade : ret.upgrades) {
            if (upgrade.getItem() instanceof ItemUpgradeStorage storageUpgrade)
                ret.storageMult += ModCommonConfig.INSTANCE.UPGRADES.getLevelMult(storageUpgrade.level.getLevel());
        }

        return ret;
    }

    @Override
    @NotNull
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.DETACHED_UPGRADE_RECIPE_SERIALIZER.get();
    }
}
