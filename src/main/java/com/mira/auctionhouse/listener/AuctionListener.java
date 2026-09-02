package com.mira.auctionhouse.listener;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.gui.AuctionGuiService;
import com.mira.auctionhouse.gui.AuctionHolder;
import com.mira.auctionhouse.model.AuctionListing;
import com.mira.auctionhouse.service.AuctionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class AuctionListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final MiraAuctionHousePlugin plugin;
    private final AuctionService service;
    private final AuctionGuiService gui;

    public AuctionListener(MiraAuctionHousePlugin plugin, AuctionService service, AuctionGuiService gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (holder.type()) {
            case BROWSE -> handleBrowse(player, holder, slot);
            case MY -> handleMy(player, holder, slot);
            case CONFIRM -> handleConfirm(player, holder, slot);
            case CLAIMS -> {
                if (slot == 49) {
                    int claimed = service.claimAll(player);
                    plugin.msg(player, claimed > 0 ? "&aClaimed &f" + claimed + " &aitem stack(s)." : "&eNothing could be claimed.");
                    gui.openClaims(player);
                } else if (slot == 53) gui.openBrowse(player, 0);
            }
            case HISTORY -> {
                if (slot == 53) gui.openBrowse(player, 0);
            }
        }
    }

    private void handleBrowse(Player player, AuctionHolder holder, int slot) {
        if (slot < 45) {
            AuctionListing listing = gui.listingAt(player, slot, false);
            if (listing != null) gui.openConfirm(player, listing);
            return;
        }
        switch (slot) {
            case 45 -> gui.beginSearch(player);
            case 46 -> gui.cycleCategory(player);
            case 47 -> gui.openMy(player, 0);
            case 48 -> gui.openBrowse(player, holder.page() - 1);
            case 49 -> gui.openBrowse(player, holder.page());
            case 50 -> gui.openBrowse(player, holder.page() + 1);
            case 51 -> gui.openClaims(player);
            case 52 -> gui.openHistory(player);
            case 53 -> player.closeInventory();
        }
    }

    private void handleMy(Player player, AuctionHolder holder, int slot) {
        if (slot < 45) {
            AuctionListing listing = gui.listingAt(player, slot, true);
            if (listing != null && service.cancel(player, listing.id(), false)) {
                plugin.msg(player, "&aCancelled listing &f" + itemName(listing.item()) + "&a. The item is now in /ah claim.");
                gui.openMy(player, holder.page());
            }
            return;
        }
        if (slot == 48) gui.openMy(player, holder.page() - 1);
        else if (slot == 49) gui.openBrowse(player, 0);
        else if (slot == 50) gui.openMy(player, holder.page() + 1);
    }

    private void handleConfirm(Player player, AuctionHolder holder, int slot) {
        if (slot == 15) {
            gui.openBrowse(player, 0);
            return;
        }
        if (slot != 11) return;
        AuctionService.PurchaseResult result = service.purchase(player, holder.context());
        switch (result) {
            case SUCCESS -> plugin.msg(player, "&aPurchase complete. The item has been delivered to you or placed in /ah claim if your inventory was full.");
            case NOT_FOUND -> plugin.msg(player, "&cThat listing no longer exists.");
            case OWN_LISTING -> plugin.msg(player, "&cYou cannot buy your own listing.");
            case INSUFFICIENT_FUNDS -> plugin.msg(player, "&cYou do not have enough money.");
            case ECONOMY_ERROR -> plugin.msg(player, "&cThe economy transaction failed. No purchase was completed.");
            case NO_PERMISSION -> plugin.msg(player, "&cYou do not have permission to buy Auction House items.");
        }
        gui.openBrowse(player, 0);
    }

    private static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component displayName = meta.displayName();
            if (displayName != null) return LEGACY.serialize(displayName);
        }

        StringBuilder out = new StringBuilder();
        for (String part : item.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AuctionHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!gui.isAwaitingSearch(player.getUniqueId())) return;
        event.setCancelled(true);
        event.getRecipients().clear();
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) gui.submitSearch(player, input);
        });
    }
}
