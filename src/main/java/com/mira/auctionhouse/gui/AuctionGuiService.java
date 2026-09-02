package com.mira.auctionhouse.gui;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.model.AuctionListing;
import com.mira.auctionhouse.service.AuctionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionGuiService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int PAGE_SIZE = 45;
    private static final List<String> CATEGORIES = List.of("ALL", "WEAPONS", "TOOLS", "ARMOR", "BLOCKS", "RESOURCES", "FOOD", "POTIONS", "ENCHANTMENTS", "SPAWNERS", "KEYS", "MISC");

    private final MiraAuctionHousePlugin plugin;
    private final AuctionService service;
    private final Map<UUID, String> search = new ConcurrentHashMap<>();
    private final Map<UUID, String> category = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();
    private final DecimalFormat money = new DecimalFormat("#,##0.##");

    public AuctionGuiService(MiraAuctionHousePlugin plugin, AuctionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void openBrowse(Player player, int requestedPage) {
        if (!player.hasPermission("miraauctionhouse.use")) {
            plugin.msg(player, "&cYou do not have permission to use the Auction House.");
            return;
        }
        String term = search.getOrDefault(player.getUniqueId(), "");
        String cat = category.getOrDefault(player.getUniqueId(), "ALL");
        List<AuctionListing> listings = service.newestFirst(term, cat);
        int page = clampPage(requestedPage, listings.size());
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Type.BROWSE, page, "");
        Inventory inv = Bukkit.createInventory(holder, 54, text("&5Mira Auction House"));
        holder.bind(inv);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < listings.size(); slot++) {
            inv.setItem(slot, listingIcon(listings.get(start + slot), false));
        }
        inv.setItem(45, button(Material.COMPASS, "&fSearch", List.of(term.isBlank() ? "&7No search filter" : "&7Current: &f" + term, "&eClick to type a search")));
        inv.setItem(46, button(Material.CHEST, "&fCategory: &d" + pretty(cat), List.of("&eClick to cycle categories")));
        inv.setItem(47, button(Material.BOOK, "&fMy Listings", List.of("&7Manage your active listings")));
        inv.setItem(48, button(Material.ARROW, "&fPrevious", List.of()));
        inv.setItem(49, button(Material.CLOCK, "&fRefresh", List.of("&7Newest listings are always shown first")));
        inv.setItem(50, button(Material.ARROW, "&fNext", List.of()));
        inv.setItem(51, button(Material.HOPPER, "&fClaims", List.of("&7Expired/cancelled/overflow items")));
        inv.setItem(52, button(Material.WRITABLE_BOOK, "&fHistory", List.of("&7Your sale and purchase history")));
        inv.setItem(53, button(Material.BARRIER, "&cClose", List.of()));
        player.openInventory(inv);
    }

    public void openMy(Player player, int requestedPage) {
        List<AuctionListing> listings = service.bySeller(player.getUniqueId());
        int page = clampPage(requestedPage, listings.size());
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Type.MY, page, "");
        Inventory inv = Bukkit.createInventory(holder, 54, text("&5My Auction Listings"));
        holder.bind(inv);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < listings.size(); slot++) inv.setItem(slot, listingIcon(listings.get(start + slot), true));
        inv.setItem(48, button(Material.ARROW, "&fPrevious", List.of()));
        inv.setItem(49, button(Material.BARRIER, "&cBack", List.of()));
        inv.setItem(50, button(Material.ARROW, "&fNext", List.of()));
        player.openInventory(inv);
    }

    public void openConfirm(Player player, AuctionListing listing) {
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Type.CONFIRM, 0, listing.id());
        Inventory inv = Bukkit.createInventory(holder, 27, text("&5Confirm Purchase"));
        holder.bind(inv);
        inv.setItem(13, listingIcon(listing, false));
        inv.setItem(11, button(Material.LIME_CONCRETE, "&aBuy for $" + money.format(listing.price()), List.of("&7Click to confirm purchase")));
        inv.setItem(15, button(Material.RED_CONCRETE, "&cCancel", List.of()));
        player.openInventory(inv);
    }

    public void openClaims(Player player) {
        List<ItemStack> items = service.claims(player.getUniqueId());
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Type.CLAIMS, 0, "");
        Inventory inv = Bukkit.createInventory(holder, 54, text("&5Auction House Claims"));
        holder.bind(inv);
        for (int i = 0; i < Math.min(45, items.size()); i++) inv.setItem(i, items.get(i));
        inv.setItem(49, button(Material.HOPPER, "&aClaim All", List.of("&7Delivers as much as your inventory can hold")));
        inv.setItem(53, button(Material.BARRIER, "&cBack", List.of()));
        player.openInventory(inv);
    }

    public void openHistory(Player player) {
        List<Map<String, Object>> rows = service.history(player.getUniqueId());
        AuctionHolder holder = new AuctionHolder(AuctionHolder.Type.HISTORY, 0, "");
        Inventory inv = Bukkit.createInventory(holder, 54, text("&5Auction House History"));
        holder.bind(inv);
        List<Map<String, Object>> newest = new ArrayList<>(rows);
        Collections.reverse(newest);
        for (int i = 0; i < Math.min(45, newest.size()); i++) {
            Map<String, Object> row = newest.get(i);
            Material material = Material.matchMaterial(String.valueOf(row.getOrDefault("material", "PAPER")));
            if (material == null) material = Material.PAPER;
            String action = String.valueOf(row.getOrDefault("action", "UNKNOWN"));
            double price = row.get("price") instanceof Number n ? n.doubleValue() : 0.0D;
            inv.setItem(i, button(material, "&f" + pretty(action), List.of("&7Listing: &f" + row.get("listing"), "&7Price: &a$" + money.format(price))));
        }
        inv.setItem(53, button(Material.BARRIER, "&cBack", List.of()));
        player.openInventory(inv);
    }

    public void beginSearch(Player player) {
        if (!player.hasPermission("miraauctionhouse.search")) return;
        awaitingSearch.add(player.getUniqueId());
        player.closeInventory();
        plugin.msg(player, "&eType your Auction House search in chat. Type &fclear &eto remove the filter.");
    }

    public boolean isAwaitingSearch(UUID uuid) { return awaitingSearch.contains(uuid); }

    public void submitSearch(Player player, String input) {
        if (!awaitingSearch.remove(player.getUniqueId())) return;
        String value = input == null ? "" : input.trim();
        if (value.equalsIgnoreCase("clear")) value = "";
        search.put(player.getUniqueId(), value);
        openBrowse(player, 0);
    }

    public void cycleCategory(Player player) {
        String current = category.getOrDefault(player.getUniqueId(), "ALL");
        int next = (CATEGORIES.indexOf(current) + 1) % CATEGORIES.size();
        category.put(player.getUniqueId(), CATEGORIES.get(next));
        openBrowse(player, 0);
    }

    public AuctionListing listingAt(Player player, int slot, boolean mine) {
        List<AuctionListing> values = mine ? service.bySeller(player.getUniqueId()) : service.newestFirst(search.getOrDefault(player.getUniqueId(), ""), category.getOrDefault(player.getUniqueId(), "ALL"));
        int page = 0;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof AuctionHolder holder) page = holder.page();
        int index = page * PAGE_SIZE + slot;
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }

    private ItemStack listingIcon(AuctionListing listing, boolean mine) {
        ItemStack item = listing.item();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(text("&7Seller: &f" + listing.sellerName()));
        lore.add(text("&7Price: &a$" + money.format(listing.price())));
        if (listing.expiresAt() != Long.MAX_VALUE) lore.add(text("&7Expires in: &f" + formatRemaining(listing.expiresAt() - System.currentTimeMillis())));
        lore.add(text(mine ? "&cClick to cancel and return to claims" : "&eClick to purchase"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        meta.lore(loreLines.stream().map(AuctionGuiService::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String legacy) { return LEGACY.deserialize(legacy); }

    private static int clampPage(int requested, int size) {
        int max = Math.max(0, (size - 1) / PAGE_SIZE);
        return Math.max(0, Math.min(max, requested));
    }

    private static String pretty(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String formatRemaining(long millis) {
        long seconds = Math.max(0, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
