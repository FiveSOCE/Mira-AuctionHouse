package com.mira.auctionhouse;

import com.mira.auctionhouse.command.AuctionCommand;
import com.mira.auctionhouse.gui.AuctionGuiService;
import com.mira.auctionhouse.listener.AuctionAnalyticsCommandListener;
import com.mira.auctionhouse.listener.AuctionListener;
import com.mira.auctionhouse.service.AuctionAnalyticsService;
import com.mira.auctionhouse.service.AuctionService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiraAuctionHousePlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String CHAT_PREFIX = "&5&lMira &8>> &r";

    private Economy economy;
    private AuctionService auctions;
    private AuctionGuiService gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider not found. MiraAuctionHouse cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctions = new AuctionService(this, economy);
        gui = new AuctionGuiService(this, auctions);
        AuctionAnalyticsService analytics = new AuctionAnalyticsService(this);
        AuctionCommand command = new AuctionCommand(this, auctions, gui);
        PluginCommand pluginCommand = getCommand("auctionhouse");
        if (pluginCommand == null) throw new IllegalStateException("auctionhouse command missing from plugin.yml");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new AuctionListener(this, auctions, gui), this);
        getServer().getPluginManager().registerEvents(new AuctionAnalyticsCommandListener(this, analytics), this);

        long period = Math.max(20L, getConfig().getLong("expiry-check-seconds", 60L) * 20L);
        getServer().getScheduler().runTaskTimer(this, auctions::expireNow, period, period);
        getLogger().info("MiraAuctionHouse v" + getPluginMeta().getVersion() + " enabled with market price analytics.");
    }

    public void reloadAll() { reloadConfig(); auctions.reload(); }

    public void msg(CommandSender sender, String message) {
        sender.sendMessage(LEGACY.deserialize(CHAT_PREFIX + message));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) return false;
        economy = provider.getProvider();
        return economy != null;
    }
}
