package com.mira.auctionhouse.service;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Sale> out = new ArrayList<>();
        for (Map<?,?> raw : yaml.getMapList("history")) {
            if (!"SOLD".equalsIgnoreCase(String.valueOf(raw.get("action")))) continue;
            if (!material.name().equalsIgnoreCase(String.valueOf(raw.get("material")))) continue;
            double price = asDouble(raw.get("price"));
            long time = asLong(raw.get("time"));
            String listing = String.valueOf(raw.containsKey("listing") ? raw.get("listing") : "");
            out.add(new Sale(material, price, Instant.ofEpochMilli(time), listing));
        }
        out.sort(Comparator.comparing(Sale::time).reversed());
        if (out.size() > Math.max(1, limit)) return List.copyOf(out.subList(0, Math.max(1, limit)));
        return List.copyOf(out);
    }

    public double averageSalePrice(Material material) {
        List<Sale> all = priceHistory(material, Integer.MAX_VALUE);
        return all.stream().mapToDouble(Sale::price).average().orElse(0D);
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

    private double asDouble(Object value) { return value instanceof Number n ? n.doubleValue() : parseDouble(String.valueOf(value)); }
    private long asLong(Object value) { return value instanceof Number n ? n.longValue() : parseLong(String.valueOf(value)); }
    private double parseDouble(String value) { try { return Double.parseDouble(value); } catch (Exception e) { return 0D; } }
    private long parseLong(String value) { try { return Long.parseLong(value); } catch (Exception e) { return 0L; } }

    public record Sale(Material material, double price, Instant time, String listingId) {}
    public record Expired(Material material, double price, Instant time, String listingId) {}
}
