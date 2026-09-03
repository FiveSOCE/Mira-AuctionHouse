package com.mira.auctionhouse.listener;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.service.AuctionAnalyticsService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class AuctionAnalyticsCommandListener implements Listener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MiraAuctionHousePlugin plugin;
    private final AuctionAnalyticsService analytics;

    public AuctionAnalyticsCommandListener(MiraAuctionHousePlugin plugin, AuctionAnalyticsService analytics) {
        this.plugin = plugin;
        this.analytics = analytics;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (args.length < 2 || !isAlias(args[0])) return;
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("pricehistory") || sub.equals("prices")) {
            event.setCancelled(true);
            priceHistory(event.getPlayer(), args);
        } else if (sub.equals("expired") || sub.equals("expiredhistory")) {
            event.setCancelled(true);
            expired(event.getPlayer());
        }
    }

    private void priceHistory(Player player, String[] args) {
        if (args.length < 3) { plugin.msg(player, "&eUsage: /ah pricehistory <material>"); return; }
        Material material;
        try { material = Material.valueOf(args[2].toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (Exception ex) { plugin.msg(player, "&cUnknown material."); return; }
        var history = analytics.priceHistory(material, 10);
        double average = analytics.averageSalePrice(material);
        plugin.msg(player, "&dPrice History: &f" + pretty(material.name()) + " &7| Average sale: &a$" + money(average));
        if (history.isEmpty()) plugin.msg(player, "&7No completed sales recorded yet.");
        for (var sale : history) plugin.msg(player, "&8" + TIME.format(sale.time()) + " &f$" + money(sale.price()) + " &7listing " + sale.listingId());
    }

    private void expired(Player player) {
        var history = analytics.expired(player.getUniqueId(), 10);
        plugin.msg(player, "&dExpired Listing History");
        if (history.isEmpty()) plugin.msg(player, "&7No expired listings recorded.");
        for (var row : history) plugin.msg(player, "&8" + TIME.format(row.time()) + " &f" + pretty(row.material().name()) + " &7listed at &f$" + money(row.price()) + " &8(" + row.listingId() + ")");
    }

    private static boolean isAlias(String value) { return value.equalsIgnoreCase("ah") || value.equalsIgnoreCase("auctionhouse") || value.equalsIgnoreCase("auction"); }
    private static String money(double v) { return String.format(Locale.US, "%,.2f", v); }
    private static String pretty(String value) { StringBuilder out = new StringBuilder(); for (String p : value.toLowerCase(Locale.ROOT).split("_")) { if (!out.isEmpty()) out.append(' '); out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); } return out.toString(); }
}
