package com.jaquadro.minecraft.storagedrawers.core.recipe;

import com.jaquadro.minecraft.storagedrawers.components.item.KeyringContents;
import com.jaquadro.minecraft.storagedrawers.core.ModDataComponents;
import com.jaquadro.minecraft.storagedrawers.core.ModItems;
import com.jaquadro.minecraft.storagedrawers.core.ModRecipes;
import com.jaquadro.minecraft.storagedrawers.item.ItemKey;
import com.jaquadro.minecraft.storagedrawers.item.ItemKeyring;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

public class KeyringRecipe extends ShapedRecipe
{
    private static KeyringRecipe instance;

    public static KeyringRecipe instance () {
        if (instance == null)
            instance = new KeyringRecipe();
        return instance;
    }

    public static RecipeSerializer<ShapedRecipe> makeSerializer () {
        return new RecipeSerializer<>(
            MapCodec.unit((Supplier<ShapedRecipe>) KeyringRecipe::instance),
            StreamCodec.<RegistryFriendlyByteBuf, ShapedRecipe>of((buf, val) -> { }, buf -> instance()));
    }

    public KeyringRecipe () {
        super(new Recipe.CommonInfo(true),
            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            pattern(),
            new ItemStackTemplate(ModItems.KEYRING.get()));
    }

    private static ShapedRecipePattern pattern () {
        return ShapedRecipePattern.of(Map.of(
            'X', Ingredient.of(Items.IRON_NUGGET),
            '#', Ingredient.of(ModItems.getKeys())),
            " X ", "X#X", " X ");
    }

    @Override
    public ItemStack assemble (CraftingInput inv) {
        ItemStack center = inv.getItem(4);
        if (center.isEmpty() || !(center.getItem() instanceof ItemKey))
            return ItemStack.EMPTY;

        ItemStack result = ItemKeyring.getKeyring(center);
        if (result.isEmpty())
            return ItemStack.EMPTY;

        KeyringContents contents = result.get(ModDataComponents.KEYRING_CONTENTS.get());
        if (contents == null)
            contents = new KeyringContents(new ArrayList<>());

        KeyringContents.Mutable mutable = new KeyringContents.Mutable(contents);
        mutable.tryInsert(center.copy());
        result.set(ModDataComponents.KEYRING_CONTENTS.get(), mutable.toImmutable());

        return result;
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer () {
        return ModRecipes.KEYRING_RECIPE_SERIALIZER.get();
    }
}
