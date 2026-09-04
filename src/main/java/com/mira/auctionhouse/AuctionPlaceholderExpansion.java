package com.mira.auctionhouse;

import com.mira.auctionhouse.service.AuctionAnalyticsService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;

public final class AuctionPlaceholderExpansion extends PlaceholderExpansion {
    private final AuctionAnalyticsService analytics;

    public AuctionPlaceholderExpansion(AuctionAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Override public @NotNull String getIdentifier() { return "miraauctionhouse"; }
    @Override public @NotNull String getAuthor() { return "FiveS"; }
    @Override public @NotNull String getVersion() { return "0.1.3"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] parts = params.toLowerCase(Locale.ROOT).split("_");
        if (parts.length < 3) return null;
        String metric = parts[parts.length - 1];
        String windowRaw = parts[parts.length - 2];
        String materialRaw = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 2));

        Material material;
        try { material = Material.valueOf(materialRaw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }

        Duration window = switch (windowRaw) {
            case "24h", "1d" -> Duration.ofHours(24);
            case "30d" -> Duration.ofDays(30);
            case "7d" -> Duration.ofDays(7);
            default -> null;
        };
        if (window == null) return null;

        var stats = analytics.stats(material, window);
        return switch (metric) {
            case "avg" -> money(stats.averageUnitPrice());
            case "median" -> money(stats.medianUnitPrice());
            case "low" -> money(stats.lowUnitPrice());
            case "high" -> money(stats.highUnitPrice());
            case "trend" -> String.format(Locale.US, "%.1f", stats.trendPercent());
            case "volume" -> Integer.toString(stats.volume());
            case "sales" -> Integer.toString(stats.sales());
            default -> null;
        };
    }

    private static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
