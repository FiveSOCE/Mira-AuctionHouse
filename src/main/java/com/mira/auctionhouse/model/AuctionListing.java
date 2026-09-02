package com.mira.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record AuctionListing(
        String id,
        UUID sellerId,
        String sellerName,
        double price,
        long createdAt,
        long expiresAt,
        ItemStack item
) {
    public AuctionListing {
        item = item.clone();
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
