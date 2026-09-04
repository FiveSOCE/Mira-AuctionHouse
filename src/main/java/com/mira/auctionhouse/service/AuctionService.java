package com.mira.auctionhouse.service;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.model.AuctionListing;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public final class AuctionService {
    private final MiraAuctionHousePlugin plugin;
    private final Economy economy;
    private final File file;
    private final Map<String, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final DecimalFormat money = new DecimalFormat("#,##0.##");

    public AuctionService(MiraAuctionHousePlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        reload();
    }

    public synchronized void reload() {
        listings.clear();
        claims.clear();
        history.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("listings");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                String path = "listings." + id;
                try {
                    UUID seller = UUID.fromString(Objects.requireNonNull(yaml.getString(path + ".seller-uuid")));
                    String sellerName = yaml.getString(path + ".seller-name", "Unknown");
                    double price = yaml.getDouble(path + ".price");
                    long created = yaml.getLong(path + ".created-at");
                    long expires = yaml.getLong(path + ".expires-at");
                    ItemStack item = yaml.getItemStack(path + ".item");
                    if (item != null && !item.getType().isAir()) {
                        listings.put(id, new AuctionListing(id, seller, sellerName, price, created, expires, item));
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping malformed auction listing " + id + ": " + ex.getMessage());
                }
            }
        }
        ConfigurationSection claimRoot = yaml.getConfigurationSection("claims");
        if (claimRoot != null) {
            for (String rawUuid : claimRoot.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    List<?> raw = yaml.getList("claims." + rawUuid, List.of());
                    List<ItemStack> items = new ArrayList<>();
                    for (Object value : raw) if (value instanceof ItemStack item) items.add(item.clone());
                    if (!items.isEmpty()) claims.put(uuid, items);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        for (Map<?, ?> raw : yaml.getMapList("history")) {
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((k, v) -> row.put(String.valueOf(k), v));
            history.add(row);
        }
        expireNow();
    }

    public synchronized boolean createListing(Player seller, double price) {
        if (!seller.hasPermission("miraauctionhouse.sell")) return false;
        if (!Double.isFinite(price) || price <= 0.0D) return false;
        if (activeCount(seller.getUniqueId()) >= listingLimit(seller)) return false;
        ItemStack held = seller.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || isBlacklisted(held)) return false;

        ItemStack listed = held.clone();
        seller.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        long now = System.currentTimeMillis();
        long expires = seller.hasPermission("miraauctionhouse.noexpiry")
                ? Long.MAX_VALUE
                : now + plugin.getConfig().getLong("listing-duration-hours", 48L) * 3_600_000L;
        String id = UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        listings.put(id, new AuctionListing(id, seller.getUniqueId(), seller.getName(), price, now, expires, listed));
        addHistory("LISTED", id, seller.getUniqueId(), null, price, listed);
        save();
        return true;
    }

    public synchronized PurchaseResult purchase(Player buyer, String id) {
        if (!buyer.hasPermission("miraauctionhouse.buy")) return PurchaseResult.NO_PERMISSION;
        expireNow();
        AuctionListing listing = listings.get(id);
        if (listing == null) return PurchaseResult.NOT_FOUND;
        if (listing.sellerId().equals(buyer.getUniqueId())) return PurchaseResult.OWN_LISTING;
        if (!economy.has(buyer, listing.price())) return PurchaseResult.INSUFFICIENT_FUNDS;

        var withdraw = economy.withdrawPlayer(buyer, listing.price());
        if (!withdraw.transactionSuccess()) return PurchaseResult.ECONOMY_ERROR;
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
        var deposit = economy.depositPlayer(seller, listing.price());
        if (!deposit.transactionSuccess()) {
            economy.depositPlayer(buyer, listing.price());
            return PurchaseResult.ECONOMY_ERROR;
        }

        listings.remove(id);
        ItemStack item = listing.item();
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(item.clone());
        if (!overflow.isEmpty()) overflow.values().forEach(extra -> addClaim(buyer.getUniqueId(), extra));
        addHistory("SOLD", id, listing.sellerId(), buyer.getUniqueId(), listing.price(), item);
        save();
        Player onlineSeller = Bukkit.getPlayer(listing.sellerId());
        if (onlineSeller != null) plugin.msg(onlineSeller, "&aYour listing sold for &f$" + money.format(listing.price()) + "&a.");
        return PurchaseResult.SUCCESS;
    }

    public synchronized boolean cancel(Player player, String id, boolean admin) {
        AuctionListing listing = listings.get(id);
        if (listing == null) return false;
        if (!admin && !listing.sellerId().equals(player.getUniqueId())) return false;
        listings.remove(id);
        addClaim(listing.sellerId(), listing.item());
        addHistory(admin ? "ADMIN_REMOVED" : "CANCELLED", id, listing.sellerId(), null, listing.price(), listing.item());
        save();
        return true;
    }

    public synchronized void expireNow() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<AuctionListing> iterator = listings.values().iterator();
        while (iterator.hasNext()) {
            AuctionListing listing = iterator.next();
            if (listing.expiresAt() == Long.MAX_VALUE || listing.expiresAt() > now) continue;
            iterator.remove();
            addClaim(listing.sellerId(), listing.item());
            addHistory("EXPIRED", listing.id(), listing.sellerId(), null, listing.price(), listing.item());
            changed = true;
            Player seller = Bukkit.getPlayer(listing.sellerId());
            if (seller != null) plugin.msg(seller, "&eAn Auction House listing expired. Use &f/ah claim &eto reclaim it.");
        }
        if (changed) save();
    }

    public synchronized List<AuctionListing> newestFirst() {
        expireNow();
        return listings.values().stream().sorted(Comparator.comparingLong(AuctionListing::createdAt).reversed()).toList();
    }

    public synchronized List<AuctionListing> newestFirst(String search, String category) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return newestFirst().stream().filter(listing -> {
            ItemStack item = listing.item();
            if (!term.isBlank()) {
                String material = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                String display = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName().toLowerCase(Locale.ROOT) : "";
                if (!material.contains(term) && !display.contains(term)) return false;
            }
            return category == null || category.equalsIgnoreCase("ALL") || category(item).equalsIgnoreCase(category);
        }).toList();
    }

    public synchronized List<AuctionListing> bySeller(UUID seller) {
        return newestFirst().stream().filter(l -> l.sellerId().equals(seller)).toList();
    }

    public synchronized List<ItemStack> claims(UUID player) {
        return claims.getOrDefault(player, List.of()).stream().map(ItemStack::clone).toList();
    }

    public synchronized int claimAll(Player player) {
        List<ItemStack> pending = new ArrayList<>(claims.getOrDefault(player.getUniqueId(), List.of()));
        if (pending.isEmpty()) return 0;
        List<ItemStack> remaining = new ArrayList<>();
        int delivered = 0;
        for (ItemStack item : pending) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (overflow.isEmpty()) delivered++;
            else remaining.addAll(overflow.values());
        }
        if (remaining.isEmpty()) claims.remove(player.getUniqueId()); else claims.put(player.getUniqueId(), remaining);
        save();
        return delivered;
    }

    public synchronized List<Map<String, Object>> history(UUID player) {
        return history.stream().filter(row -> Objects.equals(row.get("seller"), player.toString()) || Objects.equals(row.get("buyer"), player.toString())).toList();
    }

    public int activeCount(UUID seller) {
        return (int) listings.values().stream().filter(l -> l.sellerId().equals(seller)).count();
    }

    public int listingLimit(Player player) {
        int best = 0;
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("listing-limits");
        if (limits != null) {
            for (String permission : limits.getKeys(false)) if (player.hasPermission(permission)) best = Math.max(best, limits.getInt(permission));
        }
        return best > 0 ? best : 5;
    }

    public String category(ItemStack item) {
        String n = item.getType().name();
        if (n.contains("SWORD") || n.contains("BOW") || n.contains("CROSSBOW") || n.contains("TRIDENT") || n.contains("MACE")) return "WEAPONS";
        if (n.contains("PICKAXE") || n.contains("AXE") || n.contains("SHOVEL") || n.contains("HOE") || n.contains("SHEARS") || n.contains("FISHING_ROD")) return "TOOLS";
        if (n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS") || n.equals("SHIELD")) return "ARMOR";
        if (item.getType().isBlock()) return "BLOCKS";
        if (n.contains("POTION") || n.equals("GLASS_BOTTLE")) return "POTIONS";
        if (n.equals("ENCHANTED_BOOK") || n.equals("BOOK")) return "ENCHANTMENTS";
        if (n.equals("SPAWNER")) return "SPAWNERS";
        if (n.contains("KEY") || n.equals("TRIPWIRE_HOOK")) return "KEYS";
        if (item.getType().isEdible()) return "FOOD";
        if (n.contains("INGOT") || n.contains("NUGGET") || n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("COAL") || n.contains("REDSTONE") || n.contains("LAPIS") || n.contains("QUARTZ")) return "RESOURCES";
        return "MISC";
    }

    public boolean isBlacklisted(ItemStack item) {
        List<String> materials = plugin.getConfig().getStringList("blacklist.materials");
        if (materials.stream().anyMatch(v -> v.equalsIgnoreCase(item.getType().name()))) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String display = meta.hasDisplayName() ? meta.getDisplayName().toLowerCase(Locale.ROOT) : "";
            if (plugin.getConfig().getStringList("blacklist.display-name-contains").stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(display::contains)) return true;
            for (String raw : plugin.getConfig().getStringList("blacklist.pdc-keys")) {
                NamespacedKey key = NamespacedKey.fromString(raw);
                if (key != null && meta.getPersistentDataContainer().getKeys().contains(key)) return true;
            }
        }
        return false;
    }

    private void addClaim(UUID uuid, ItemStack item) {
        claims.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(item.clone());
    }

    private void addHistory(String action, String listingId, UUID seller, UUID buyer, double price, ItemStack item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", action);
        row.put("listing", listingId);
        row.put("seller", seller == null ? null : seller.toString());
        row.put("buyer", buyer == null ? null : buyer.toString());
        row.put("price", price);
        row.put("time", System.currentTimeMillis());
        row.put("material", item.getType().name());
        row.put("amount", Math.max(1, item.getAmount()));
        history.add(row);
        int max = Math.max(100, plugin.getConfig().getInt("history-limit", 1000));
        while (history.size() > max) history.remove(0);
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AuctionListing listing : listings.values()) {
            String path = "listings." + listing.id();
            yaml.set(path + ".seller-uuid", listing.sellerId().toString());
            yaml.set(path + ".seller-name", listing.sellerName());
            yaml.set(path + ".price", listing.price());
            yaml.set(path + ".created-at", listing.createdAt());
            yaml.set(path + ".expires-at", listing.expiresAt());
            yaml.set(path + ".item", listing.item());
        }
        for (var entry : claims.entrySet()) yaml.set("claims." + entry.getKey(), entry.getValue());
        yaml.set("history", history);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save auctions.yml", ex);
        }
    }

    public enum PurchaseResult {
        SUCCESS, NOT_FOUND, OWN_LISTING, INSUFFICIENT_FUNDS, ECONOMY_ERROR, NO_PERMISSION
    }
}
