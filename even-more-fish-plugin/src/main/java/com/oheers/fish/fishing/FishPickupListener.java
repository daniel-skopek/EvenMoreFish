package com.oheers.fish.fishing;

import com.oheers.fish.api.Logging;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.items.nbt.NbtKeys;
import com.oheers.fish.items.nbt.abstracted.NBTHolder;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Preserves EMF fish NBT when a fish item is picked up by a player.
 * <p>
 * Some item-providing plugins (e.g. Nexo) rebuild their items when a player picks them up,
 * which overwrites the {@code minecraft:custom_data} component that EMF uses to store fish
 * NBT. This listener captures the fish NBT before such plugins run and restores it afterwards.
 */
public class FishPickupListener implements Listener {

    private final Map<UUID, FishNbtData> pendingFish = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureFishNbt(@NonNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item item = event.getItem();
        FishNbtData data = readFishNbt(item.getItemStack());
        if (data != null) {
            pendingFish.put(item.getUniqueId(), data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreFishNbt(@NonNull EntityPickupItemEvent event) {
        Item item = event.getItem();
        FishNbtData data = pendingFish.remove(item.getUniqueId());
        if (data == null) {
            return;
        }
        ItemStack stack = item.getItemStack();
        // If the NBT is still present, another plugin did not strip it, so there is nothing to restore.
        if (stack.isEmpty() || FishManager.getInstance().isFish(stack)) {
            return;
        }
        try {
            NBTHolder<ItemStack> holder = NBTHolder.itemStack(stack);
            holder.setAutoSave(false);
            holder.setString(NbtKeys.EMF_FISH_NAME.get(), data.name());
            holder.setString(NbtKeys.EMF_FISH_RARITY.get(), data.rarity());
            if (data.length() != null) {
                holder.setFloat(NbtKeys.EMF_FISH_LENGTH.get(), data.length());
            }
            if (data.player() != null) {
                holder.setString(NbtKeys.EMF_FISH_PLAYER.get(), data.player());
            }
            if (data.randomIndex() != null) {
                holder.setInteger(NbtKeys.EMF_FISH_RANDOM_INDEX.get(), data.randomIndex());
            }
            holder.save();
            item.setItemStack(stack);
        } catch (Exception exception) {
            Logging.error("Failed to restore EMF fish NBT after item pickup.", exception);
        }
    }

    private @Nullable FishNbtData readFishNbt(@NonNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        try {
            NBTHolder<ItemStack> holder = NBTHolder.itemStack(stack);
            String name = holder.getString(NbtKeys.EMF_FISH_NAME.get());
            String rarity = holder.getString(NbtKeys.EMF_FISH_RARITY.get());
            if (name == null || rarity == null) {
                return null;
            }
            return new FishNbtData(
                name,
                rarity,
                holder.getFloat(NbtKeys.EMF_FISH_LENGTH.get()),
                holder.getString(NbtKeys.EMF_FISH_PLAYER.get()),
                holder.getInteger(NbtKeys.EMF_FISH_RANDOM_INDEX.get())
            );
        } catch (Exception exception) {
            Logging.error("Failed to capture EMF fish NBT during item pickup.", exception);
            return null;
        }
    }

    private record FishNbtData(
        @NonNull String name,
        @NonNull String rarity,
        @Nullable Float length,
        @Nullable String player,
        @Nullable Integer randomIndex
    ) {}

}
