package com.mira.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AuctionHolder implements InventoryHolder {
    public enum Type { BROWSE, MY, CONFIRM, CLAIMS, HISTORY }

    private final Type type;
    private final int page;
    private final String context;
    private Inventory inventory;

    public AuctionHolder(Type type, int page, String context) {
        this.type = type;
        this.page = page;
        this.context = context == null ? "" : context;
    }

    public Type type() { return type; }
    public int page() { return page; }
    public String context() { return context; }
    public void bind(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
