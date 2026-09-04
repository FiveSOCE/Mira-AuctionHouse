package com.mira.auctionhouse.listener;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.service.AuctionAnalyticsService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
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
        String[] args = event.getMessage().substring(1).trim().split("\s+");
        if (args.length < 2 || !isAlias(args[0])) return;
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("pricehistory") || sub.equals("prices")) {
            event.setCancelled(true);
            priceHistory(event.getPlayer(), args);
        } else if (sub.equals("market") || sub.equals("marketstats")) {
            event.setCancelled(true);
            market(event.getPlayer(), args);
        } else if (sub.equals("expired") || sub.equals("expiredhistory")) {
            event.setCancelled(true);
            expired(event.getPlayer());
        }
    }

    private void priceHistory(Player player, String[] args) {
        if (args.length < 3) { plugin.msg(player, "&eUsage: /ah pricehistory <material>"); return; }
        Material material = material(args[2]);
        if (material == null) { plugin.msg(player, "&cUnknown material."); return; }

        var history = analytics.priceHistory(material, 10);
        var week = analytics.stats(material, Duration.ofDays(7));
        plugin.msg(player, "&dPrice History: &f" + pretty(material.name())
                + " &7| 7d avg/unit &a$" + money(week.averageUnitPrice())
                + " &7| median &a$" + money(week.medianUnitPrice()));
        if (history.isEmpty()) plugin.msg(player, "&7No completed sales recorded yet.");
        for (var sale : history) {
            plugin.msg(player, "&8" + TIME.format(sale.time()) + " &f$" + money(sale.price())
                    + " &7for x" + sale.amount() + " &8($" + money(sale.unitPrice()) + "/unit) &7listing " + sale.listingId());
        }
    }

    private void market(Player player, String[] args) {
        if (args.length < 3) {
            plugin.msg(player, "&eUsage: /ah market <material> [24h|7d|30d]");
            return;
        }
        Material material = material(args[2]);
        if (material == null) { plugin.msg(player, "&cUnknown material."); return; }
        String windowName = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "7d";
        Duration window = switch (windowName) {
            case "24h", "1d" -> Duration.ofHours(24);
            case "30d" -> Duration.ofDays(30);
            default -> Duration.ofDays(7);
        };
        var stats = analytics.stats(material, window);
        plugin.msg(player, "&dMarket: &f" + pretty(material.name()) + " &7(" + windowName + ")");
        if (stats.sales() == 0) {
            plugin.msg(player, "&7No completed sales in this window.");
            return;
        }
        plugin.msg(player, "&7Sales: &f" + stats.sales() + " &7| Units: &f" + stats.volume()
                + " &7| Avg listing: &a$" + money(stats.averageListingPrice()));
        plugin.msg(player, "&7Avg/unit: &a$" + money(stats.averageUnitPrice())
                + " &7| Median: &a$" + money(stats.medianUnitPrice()));
        plugin.msg(player, "&7Range: &f$" + money(stats.lowUnitPrice()) + " &7- &f$" + money(stats.highUnitPrice())
                + " &7| Trend: " + trend(stats.trendPercent()));
    }

    private void expired(Player player) {
        var history = analytics.expired(player.getUniqueId(), 10);
        plugin.msg(player, "&dExpired Listing History");
        if (history.isEmpty()) plugin.msg(player, "&7No expired listings recorded.");
        for (var row : history) plugin.msg(player, "&8" + TIME.format(row.time()) + " &f" + pretty(row.material().name()) + " &7listed at &f$" + money(row.price()) + " &8(" + row.listingId() + ")");
    }

    private static Material material(String raw) {
        try { return Material.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (Exception ex) { return null; }
    }

    private static String trend(double value) {
        String prefix = value > 0D ? "&a+" : value < 0D ? "&c" : "&7";
        return prefix + String.format(Locale.US, "%.1f%%", value);
    }

    private static boolean isAlias(String value) { return value.equalsIgnoreCase("ah") || value.equalsIgnoreCase("auctionhouse") || value.equalsIgnoreCase("auction"); }
    private static String money(double v) { return String.format(Locale.US, "%,.2f", v); }
    private static String pretty(String value) { StringBuilder out = new StringBuilder(); for (String p : value.toLowerCase(Locale.ROOT).split("_")) { if (!out.isEmpty()) out.append(' '); out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); } return out.toString(); }
}
