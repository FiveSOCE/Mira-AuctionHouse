package com.mira.auctionhouse.service;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class AuctionAnalyticsService {
    private final MiraAuctionHousePlugin plugin;
    private final File file;

    public AuctionAnalyticsService(MiraAuctionHousePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    public List<Sale> priceHistory(Material material, int limit) {
        if (material == null || !file.isFile()) return List.of();
        List<Sale> out = allSales(material);
        out.sort(Comparator.comparing(Sale::time).reversed());
        if (out.size() > Math.max(1, limit)) return List.copyOf(out.subList(0, Math.max(1, limit)));
        return List.copyOf(out);
    }

    public double averageSalePrice(Material material) {
        return allSales(material).stream().mapToDouble(Sale::price).average().orElse(0D);
    }

    public MarketStats stats(Material material, Duration window) {
        if (material == null) return MarketStats.EMPTY;
        Duration safeWindow = window == null || window.isNegative() || window.isZero() ? Duration.ofDays(7) : window;
        Instant now = Instant.now();
        Instant start = now.minus(safeWindow);
        Instant previousStart = start.minus(safeWindow);

        List<Sale> current = allSales(material).stream()
                .filter(sale -> !sale.time().isBefore(start) && !sale.time().isAfter(now))
                .toList();
        List<Sale> previous = allSales(material).stream()
                .filter(sale -> !sale.time().isBefore(previousStart) && sale.time().isBefore(start))
                .toList();

        if (current.isEmpty()) return new MarketStats(material, safeWindow, 0, 0, 0D, 0D, 0D, 0D, 0D, 0D);

        List<Double> unitPrices = current.stream().map(Sale::unitPrice).sorted().toList();
        double averageListing = current.stream().mapToDouble(Sale::price).average().orElse(0D);
        double averageUnit = unitPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double median = median(unitPrices);
        double low = unitPrices.getFirst();
        double high = unitPrices.getLast();
        int volume = current.stream().mapToInt(Sale::amount).sum();

        double previousAverage = previous.stream().mapToDouble(Sale::unitPrice).average().orElse(0D);
        double trend = previousAverage <= 0D ? 0D : ((averageUnit - previousAverage) / previousAverage) * 100D;

        return new MarketStats(material, safeWindow, current.size(), volume, averageListing, averageUnit, median, low, high, trend);
    }

    public List<Expired> expired(UUID seller, int limit) {
        if (!file.isFile()) return List.of();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Expired> out = new ArrayList<>();
        for (Map<?,?> raw : yaml.getMapList("history")) {
            if (!"EXPIRED".equalsIgnoreCase(String.valueOf(raw.get("action")))) continue;
            if (seller != null && !seller.toString().equals(String.valueOf(raw.get("seller")))) continue;
            Material material;
            try { material = Material.valueOf(String.valueOf(raw.get("material"))); }
            catch (Exception ignored) { continue; }
            String listing = String.valueOf(raw.containsKey("listing") ? raw.get("listing") : "");
            out.add(new Expired(material, asDouble(raw.get("price")), Instant.ofEpochMilli(asLong(raw.get("time"))), listing));
        }
        out.sort(Comparator.comparing(Expired::time).reversed());
        if (out.size() > Math.max(1, limit)) return List.copyOf(out.subList(0, Math.max(1, limit)));
        return List.copyOf(out);
    }

    private List<Sale> allSales(Material material) {
        if (material == null || !file.isFile()) return List.of();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Sale> out = new ArrayList<>();
        for (Map<?,?> raw : yaml.getMapList("history")) {
            if (!"SOLD".equalsIgnoreCase(String.valueOf(raw.get("action")))) continue;
            if (!material.name().equalsIgnoreCase(String.valueOf(raw.get("material")))) continue;
            double price = asDouble(raw.get("price"));
            long time = asLong(raw.get("time"));
            int amount = Math.max(1, asInt(raw.get("amount")));
            String listing = String.valueOf(raw.containsKey("listing") ? raw.get("listing") : "");
            out.add(new Sale(material, price, amount, Instant.ofEpochMilli(time), listing));
        }
        return out;
    }

    private static double median(List<Double> sorted) {
        if (sorted == null || sorted.isEmpty()) return 0D;
        int size = sorted.size();
        if ((size & 1) == 1) return sorted.get(size / 2);
        return (sorted.get((size / 2) - 1) + sorted.get(size / 2)) / 2D;
    }

    private double asDouble(Object value) { return value instanceof Number n ? n.doubleValue() : parseDouble(String.valueOf(value)); }
    private long asLong(Object value) { return value instanceof Number n ? n.longValue() : parseLong(String.valueOf(value)); }
    private int asInt(Object value) { return value instanceof Number n ? n.intValue() : parseInt(String.valueOf(value)); }
    private double parseDouble(String value) { try { return Double.parseDouble(value); } catch (Exception e) { return 0D; } }
    private long parseLong(String value) { try { return Long.parseLong(value); } catch (Exception e) { return 0L; } }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return 1; } }

    public record Sale(Material material, double price, int amount, Instant time, String listingId) {
        public double unitPrice() { return amount <= 0 ? price : price / amount; }
    }

    public record Expired(Material material, double price, Instant time, String listingId) {}

    public record MarketStats(Material material, Duration window, int sales, int volume, double averageListingPrice,
                              double averageUnitPrice, double medianUnitPrice, double lowUnitPrice,
                              double highUnitPrice, double trendPercent) {
        public static final MarketStats EMPTY = new MarketStats(Material.AIR, Duration.ZERO, 0, 0, 0D, 0D, 0D, 0D, 0D, 0D);
    }
}
