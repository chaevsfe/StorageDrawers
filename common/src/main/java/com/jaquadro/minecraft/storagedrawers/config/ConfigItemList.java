package com.jaquadro.minecraft.storagedrawers.config;

import com.jaquadro.minecraft.storagedrawers.ModServices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigItemList
{
    private final List<String> listedNamespaces = new ArrayList<>();
    private final List<Item> listedItems = new ArrayList<>();
    private final List<String> knownRules = new ArrayList<>();
    private Set<String> appliedThisPass;
    private boolean initialized;

    public ConfigItemList () { }

    public void initialize () {
        listedNamespaces.clear();
        listedItems.clear();
        initialized = true;
        appliedThisPass = new HashSet<>();

        try {
            innerInitialize();

            for (String rule : List.copyOf(knownRules)) {
                register(rule);
            }
        }
        finally {
            appliedThisPass = null;
        }
    }

    protected void innerInitialize () { }

    public boolean isListed (ItemStack stack) {
        Item item = stack.getItem();

        if (listedItems.contains(item))
            return true;

        if(!listedNamespaces.isEmpty()) {
            ResourceKey<Item> resourceKey = BuiltInRegistries.ITEM.getResourceKey(item).orElse(null);
            if (resourceKey != null) {
                String namespace = resourceKey.identifier().getNamespace();
                if (listedNamespaces.contains(namespace))
                    return true;
            }
        }

        return false;
    }

    public boolean registerNamespace (@NotNull String namespace) {
        if (namespace.isEmpty())
            return false;

        unregisterNamespace(namespace);
        listedNamespaces.add(namespace);

        logRegisterNamespace(namespace);

        return true;
    }

    protected void logRegisterNamespace (@NotNull String namespace) { }

    public boolean registerItem (@NotNull ItemStack item) {
        if (item.isEmpty())
            return false;

        unregisterItem(item);
        listedItems.add(item.getItem());

        logRegisterItem(item);

        return true;
    }

    protected void logRegisterItem (@NotNull ItemStack item) { }

    public void register (List<String> entries) {
        entries.forEach(this::register);
    }

    public boolean register (String entry) {
        if (!knownRules.contains(entry))
            knownRules.add(entry);

        if (!initialized)
            return true;

        if (appliedThisPass != null && !appliedThisPass.add(entry))
            return true;

        String[] parts = entry.split("\\s*:\\s*");
        if (parts.length == 1)
            return registerNamespace(parts[0]);

        Identifier resource = Identifier.tryParse(entry);
        if (resource == null) {
            ModServices.log.warn("Skipping invalid item list entry '{}'", entry);
            return false;
        }

        Item item = BuiltInRegistries.ITEM.getValue(resource);

        return registerItem(new ItemStack(item));
    }

    public boolean unregisterNamespace (@NotNull String namespace) {
        if (namespace.isEmpty())
            return false;

        return listedNamespaces.remove(namespace);
    }

    public boolean unregisterItem (@NotNull ItemStack stack) {
        if (stack.isEmpty())
            return false;

        return listedItems.remove(stack.getItem());
    }

}
