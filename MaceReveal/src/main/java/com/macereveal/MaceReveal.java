package com.macereveal;

import org.bukkit.plugin.java.JavaPlugin;

public class MaceReveal extends JavaPlugin {

    private static MaceReveal instance;
    private RevealManager revealManager;

    @Override
    public void onEnable() {
        instance = this;
        revealManager = new RevealManager(this);
        getServer().getPluginManager().registerEvents(new MaceListener(this), this);
        getLogger().info("MaceReveal enabled.");
    }

    @Override
    public void onDisable() {
        if (revealManager != null) {
            revealManager.cancelAll();
        }
        getLogger().info("MaceReveal disabled.");
    }

    public static MaceReveal getInstance() {
        return instance;
    }

    public RevealManager getRevealManager() {
        return revealManager;
    }
}
