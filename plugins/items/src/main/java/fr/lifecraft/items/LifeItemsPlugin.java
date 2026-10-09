package fr.lifecraft.items;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** LifeItems : minerai « Éclat de faille » et équipement de faille, sans resource pack. */
public final class LifeItemsPlugin extends JavaPlugin {

    private Settings settings;
    private CustomItems items;
    private RecipeManager recipes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        items = new CustomItems(this, settings);
        recipes = new RecipeManager(this, items);
        recipes.register(settings);

        getServer().getPluginManager().registerEvents(new OreListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getServer().getPluginManager().registerEvents(recipes, this);

        PluginCommand cmd = getCommand("lifeitems");
        if (cmd != null) {
            LifeItemsCommand handler = new LifeItemsCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("LifeItems activé : " + items.ids().size() + " objets, "
                + recipes.keys().size() + " recettes.");
    }

    @Override
    public void onDisable() {
        if (recipes != null) {
            recipes.unregister();
        }
    }

    /** Recharge config.yml et les recettes. */
    void reload() {
        reloadConfig();
        settings = new Settings(this);
        items.setSettings(settings);
        recipes.register(settings);
    }

    Settings settings() {
        return settings;
    }

    CustomItems items() {
        return items;
    }

    RecipeManager recipes() {
        return recipes;
    }
}
