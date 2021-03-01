
/*

	ChestSort - maintained by mfnalex / JEFF Media GbR ( www.jeff-media.de )
	
	THANK YOU for your interest in ChestSort :)
	
	ChestSort has been an open-source project from the day it started.
	Without the support of the community, many awesome features
	would be missing. A big THANK YOU to everyone who contributed to
	this project!
	
	If you have bug reports, feature requests etc. please message me at SpigotMC.org:
	https://www.spigotmc.org/members/mfnalex.175238/
	
	Please DO NOT post bug reports or feature requests in the review section at SpigotMC.org. Thank you.
	
	=============================================================================================
	
	TECHNICAL INFORMATION:
	
	If you want to know how the sorting works, have a look at the JeffChestSortOrganizer class.
	
	If you want to contribute, please note that messages sent to player must be made configurable in the config.yml.
	Please have a look at the JeffChestSortMessages class if you want to add a message.
	
*/

package de.jeff_media.ChestSort;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import de.jeff_media.ChestSort.config.Config;
import de.jeff_media.ChestSort.hooks.GenericGUIHook;
import de.jeff_media.ChestSort.hooks.PlayerVaultsHook;
import de.jeff_media.ChestSort.placeholders.ChestSortPlaceholders;
import de.jeff_media.PluginUpdateChecker.PluginUpdateChecker;
import io.papermc.lib.PaperLib;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import at.pcgamingfreaks.Minepacks.Bukkit.API.MinepacksPlugin;
import de.jeff_media.ChestSort.utils.Utils;

public class ChestSortPlugin extends JavaPlugin implements de.jeff_media.ChestSortAPI.ChestSort {

	ChestSortLogger lgr;
	Map<String, ChestSortPlayerSetting> perPlayerSettings = new HashMap<>();
	HashMap<UUID,Long> hotkeyCooldown;
	ChestSortMessages messages;
	ChestSortOrganizer organizer;
	PluginUpdateChecker updateChecker;
	ChestSortListener listener;
	ChestSortSettingsGUI settingsGUI;
	ChestSortPermissionsHandler permissionsHandler;
	String sortingMethod;
	ArrayList<String> disabledWorlds;
	ChestSortAPIHandler api;
	final int currentConfigVersion = 46;
	boolean usingMatchingConfig = true;
	protected boolean debug = false;
	boolean verbose = true;
	final boolean hotkeyGUI = true;
	
	public boolean hookCrackShot = false;
	public boolean hookInventoryPages = false;
	public boolean hookMinepacks = false;

	public GenericGUIHook genericHook;
	public PlayerVaultsHook playerVaultsHook;
	
	private static long updateCheckInterval = 4*60*60; // in seconds. We check on startup and every 4 hours
	
	String mcVersion; 	// 1.13.2 = 1_13_R2
						// 1.14.4 = 1_14_R1
						// 1.8.0  = 1_8_R1
	int mcMinorVersion; // 14 for 1.14, 13 for 1.13, ...

	@Override
	public ChestSortAPIHandler getAPI() {
		return this.api;
	}

	// Public API method to sort any given inventory
	@Deprecated
	@Override
	public void sortInventory(Inventory inv) {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		getLogger().warning(String.format("%s has performed a call to a deprecated ChestSort API method. This is NOT a ChestSort error.",stackTraceElements[2]));
		api.sortInventory(inv);
	}

	// Public API method to sort any given inventory inbetween startSlot and endSlot
	@Deprecated
	@Override
	public void sortInventory(Inventory inv, int startSlot, int endSlot) {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		getLogger().warning(String.format("%s has performed a call to a deprecated ChestSort API method. This is NOT a ChestSort error.",stackTraceElements[2]));
		api.sortInventory(inv, startSlot, endSlot);
	}
	
	// Public API method to check if player has automatic chest sorting enabled
	@Deprecated
	@Override
	public boolean sortingEnabled(Player p) {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		getLogger().warning(String.format("%s has performed a call to a deprecated ChestSort API method. This is NOT a ChestSort error.",stackTraceElements[2]));
		return isSortingEnabled(p);
	}
	
	boolean isSortingEnabled(Player p) {
		if (perPlayerSettings == null) {
			perPlayerSettings = new HashMap<>();
		}
		listener.plugin.registerPlayerIfNeeded(p);
		return perPlayerSettings.get(p.getUniqueId().toString()).sortingEnabled;
	}

	public void debug(String t) {
		if(debug) getLogger().warning("[DEBUG] "+t);
	}

	public void debug2(String t) {
		if(getConfig().getBoolean(Config.DEBUG2)) getLogger().warning("[DEBUG2] "+t);
	}

	// Creates the default configuration file
	// Also checks the config-version of an already existing file. If the existing
	// config is too
	// old (generated prior to ChestSort 2.0.0), we rename it to config.old.yml so
	// that users
	// can start off with a new config file that includes all new options. However,
	// on most
	// updates, the file will not be touched, even if new config options were added.
	// You will instead
	// get a warning in the console that you should consider adding the options
	// manually. If you do
	// not add them, the default values will be used for any unset values.
	void createConfig() {

		// This saves the config.yml included in the .jar file, but it will not
		// overwrite an existing config.yml
		this.saveDefaultConfig();
		reloadConfig();
		
		// Load disabled-worlds. If it does not exist in the config, it returns null.
		// That's no problem
		disabledWorlds = (ArrayList<String>) getConfig().getStringList("disabled-worlds");

		// Config version prior to 5? Then it must have been generated by ChestSort 1.x
		/*if (getConfig().getInt("config-version", 0) < 5) {
			renameConfigIfTooOld();

			// Using old config version, but it's no problem. We just print a warning and
			// use the default values later on

		} else*/

		if (getConfig().getInt("config-version", 0) != currentConfigVersion) {
			showOldConfigWarning();
			ChestSortConfigUpdater configUpdater = new ChestSortConfigUpdater(this);
			configUpdater.updateConfig();
			usingMatchingConfig = true;
			//createConfig();
		}

		createDirectories();

		setDefaultConfigValues();

	}

	private void setDefaultConfigValues() {
		// If you use an old config file with missing options, the following default
		// values will be used instead
		// for every missing option.
		// By default, sorting is disabled. Every player has to run /chestsort once
		getConfig().addDefault("use-permissions", true);
		getConfig().addDefault("allow-automatic-sorting",true);
		getConfig().addDefault("allow-automatic-inventory-sorting",true);
		getConfig().addDefault("sorting-enabled-by-default", false);
		getConfig().addDefault("inv-sorting-enabled-by-default", false);
		getConfig().addDefault("show-message-when-using-chest", true);
		getConfig().addDefault("show-message-when-using-chest-and-sorting-is-enabled", false);
		getConfig().addDefault("show-message-again-after-logout", true);
		getConfig().addDefault("sorting-method", "{category},{itemsFirst},{name},{color}");
		getConfig().addDefault("allow-player-inventory-sorting", false);
		getConfig().addDefault("check-for-updates", "true");
		getConfig().addDefault("check-interval", 4);
		getConfig().addDefault("auto-generate-category-files", true);
		getConfig().addDefault("sort-time", "close");
		getConfig().addDefault("allow-sorting-hotkeys", true);
		getConfig().addDefault("allow-additional-hotkeys", true);
		getConfig().addDefault("sorting-hotkeys.middle-click", true);
		getConfig().addDefault("sorting-hotkeys.shift-click", true);
		getConfig().addDefault("sorting-hotkeys.double-click", true);
		getConfig().addDefault("sorting-hotkeys.shift-right-click", true);
		getConfig().addDefault("additional-hotkeys.left-click", false);
		getConfig().addDefault("additional-hotkeys.right-click", false);
		getConfig().addDefault("dump", false);
		getConfig().addDefault("log", false);
		
		getConfig().addDefault("hook-crackshot", true);
		getConfig().addDefault("hook-crackshot-prefix", "crackshot_weapon");
		getConfig().addDefault("hook-inventorypages", true);
		getConfig().addDefault("hook-minepacks", true);
		getConfig().addDefault("hook-generic",true);
		
		getConfig().addDefault("verbose", true); // Prints some information in onEnable()
	}

	private void createDirectories() {
		// Create a playerdata folder that contains all the perPlayerSettings as .yml
		File playerDataFolder = new File(getDataFolder().getPath() + File.separator + "playerdata");
		if (!playerDataFolder.getAbsoluteFile().exists()) {
			playerDataFolder.mkdir();
		}

		// Create a categories folder that contains text files. ChestSort includes
		// default category files,
		// but you can also create your own
		File categoriesFolder = new File(getDataFolder().getPath() + File.separator + "categories");
		if (!categoriesFolder.getAbsoluteFile().exists()) {
			categoriesFolder.mkdir();
		}
	}

	private void showOldConfigWarning() {
		getLogger().warning("==============================================");
		getLogger().warning("You were using an old config file. ChestSort");
		getLogger().warning("has updated the file to the newest version.");
		getLogger().warning("Your changes have been kept.");
		getLogger().warning("==============================================");
	}

	@Override
	public void onDisable() {
		// We have to unregister every player to save their perPlayerSettings
		for (Player p : getServer().getOnlinePlayers()) {
			unregisterPlayer(p);
			permissionsHandler.removePermissions(p);
		}
	}

	public ChestSortPlayerSetting getPlayerSetting(Player p) {
		registerPlayerIfNeeded(p);
		return perPlayerSettings.get(p.getUniqueId().toString());
	}

	@Override
	public void onEnable() {
		
		String tmpVersion = getServer().getClass().getPackage().getName();
		mcVersion = tmpVersion.substring(tmpVersion.lastIndexOf('.') + 1);
		tmpVersion = mcVersion.substring(mcVersion.indexOf("_")+1);
		mcMinorVersion = Integer.parseInt(tmpVersion.substring(0,tmpVersion.indexOf("_")));
		
		load(false);

		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
			new ChestSortPlaceholders(this).register();
		}
	}

	private String getCategoryList() {
		StringBuilder list = new StringBuilder();
		ChestSortCategory[] categories = organizer.categories.toArray(new ChestSortCategory[0]);
		Arrays.sort(categories);
		for(ChestSortCategory category : categories) {
			list.append(category.name).append(" (");
			list.append(category.typeMatches.length).append("), ");
		}
		list = new StringBuilder(list.substring(0, list.length() - 2));
		return list.toString();
		
	}

	private void registerMetrics() {
		// Metrics will need json-simple with 1.14 API.
		Metrics bStats = new Metrics(this,3089);
		
		bStats.addCustomChart(new Metrics.SimplePie("sorting_method", () -> sortingMethod));
		bStats.addCustomChart(new Metrics.SimplePie("config_version",
				() -> Integer.toString(getConfig().getInt("config-version", 0))));
		bStats.addCustomChart(
				new Metrics.SimplePie("check_for_updates", () -> getConfig().getString("check-for-updates", "true")));
		bStats.addCustomChart(
				new Metrics.SimplePie("update_interval", () -> Long.toString(updateCheckInterval)));

		bStats.addCustomChart(new Metrics.SimplePie("allow_automatic_sorting",
				() -> Boolean.toString(getConfig().getBoolean("allow-automatic-sorting"))));
		bStats.addCustomChart(new Metrics.SimplePie("allow_automatic_inv_sorting",
				() -> Boolean.toString(getConfig().getBoolean("allow-automatic-inventory-sorting"))));

		bStats.addCustomChart(new Metrics.SimplePie("show_message_when_using_chest",
				() -> Boolean.toString(getConfig().getBoolean("show-message-when-using-chest"))));
		bStats.addCustomChart(new Metrics.SimplePie("show_message_when_using_chest_and_sorting_is_enabl", () -> Boolean
				.toString(getConfig().getBoolean("show-message-when-using-chest-and-sorting-is-enabled"))));
		bStats.addCustomChart(new Metrics.SimplePie("show_message_again_after_logout",
				() -> Boolean.toString(getConfig().getBoolean("show-message-again-after-logout"))));
		bStats.addCustomChart(new Metrics.SimplePie("sorting_enabled_by_default",
				() -> Boolean.toString(getConfig().getBoolean("sorting-enabled-by-default"))));
		bStats.addCustomChart(new Metrics.SimplePie("inv_sorting_enabled_by_default",
				() -> Boolean.toString(getConfig().getBoolean("inv-sorting-enabled-by-default"))));
		bStats.addCustomChart(
				new Metrics.SimplePie("using_matching_config_version", () -> Boolean.toString(usingMatchingConfig)));
		bStats.addCustomChart(new Metrics.SimplePie("sort_time", () -> getConfig().getString("sort-time")));
		bStats.addCustomChart(new Metrics.SimplePie("auto_generate_category_files",
				() -> Boolean.toString(getConfig().getBoolean("auto-generate-category-files"))));
		bStats.addCustomChart(new Metrics.SimplePie("allow_hotkeys",
				() -> Boolean.toString(getConfig().getBoolean("allow-sorting-hotkeys"))));
		bStats.addCustomChart(new Metrics.SimplePie("allow_additional_hotkeys",
				() -> Boolean.toString(getConfig().getBoolean("allow-additional-hotkeys"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_middle_click",
				() -> Boolean.toString(getConfig().getBoolean("sorting-hotkeys.middle-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_shift_click",
				() -> Boolean.toString(getConfig().getBoolean("sorting-hotkeys.shift-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_double_click",
				() -> Boolean.toString(getConfig().getBoolean("sorting-hotkeys.double-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_shift_right_click",
				() -> Boolean.toString(getConfig().getBoolean("sorting-hotkeys.shift-right-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_left_click",
				() -> Boolean.toString(getConfig().getBoolean("additional-hotkeys.left-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("hotkey_right_click",
				() -> Boolean.toString(getConfig().getBoolean("additional-hotkeys.right-click"))));
		bStats.addCustomChart(new Metrics.SimplePie("use_permissions",
				() -> Boolean.toString(getConfig().getBoolean("use-permissions"))));
		
	}

	// Saves default category files, when enabled in the config
	private void saveDefaultCategories() {

		// Abort when auto-generate-category-files is set to false in config.yml
		if (!getConfig().getBoolean("auto-generate-category-files", true)) {
			return;
		}

		// Isn't there a smarter way to find all the 9** files in the .jar?
		String[] defaultCategories = { "900-weapons", "905-common-tools", "907-other-tools", "909-food", "910-valuables", "920-armor-and-arrows", "930-brewing",
				"950-redstone", "960-wood", "970-stone", "980-plants", "981-corals","_ReadMe - Category files" };

		// Delete all files starting with 9..
		for (File file : new File(getDataFolder().getAbsolutePath() + File.separator + "categories" + File.separator)
				.listFiles((directory, fileName) -> {
					if (!fileName.endsWith(".txt")) {
						return false;
					}
					// Category between 900 and 999-... are default
					// categories
					return fileName.matches("(?i)9\\d\\d.*\\.txt$");
				})) {

			boolean delete = true;

			for (String name : defaultCategories) {
				name=name+".txt";
				if (name.equalsIgnoreCase(file.getName())) {
					delete = false;
					break;
				}
			}
			if (delete) {
				file.delete();
				getLogger().warning("Deleting deprecated default category file " + file.getName());
			}

		}

		for (String category : defaultCategories) {

			FileOutputStream fopDefault = null;
			File fileDefault;

			try {
				InputStream in = getClass().getResourceAsStream("/categories/" + category + ".default.txt");

				fileDefault = new File(getDataFolder().getAbsolutePath() + File.separator + "categories"
						+ File.separator + category + ".txt");
				fopDefault = new FileOutputStream(fileDefault);

				// overwrites existing files, on purpose.
				fileDefault.createNewFile();

				// get the content in bytes
				byte[] contentInBytes = Utils.getBytes(in);

				fopDefault.write(contentInBytes);
				fopDefault.flush();
				fopDefault.close();

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (fopDefault != null) {
						fopDefault.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// Unregister a player and save their settings in the playerdata folder
	void unregisterPlayer(Player p) {
		// File will be named by the player's uuid. This will prevent problems on player
		// name changes.
		UUID uniqueId = p.getUniqueId();

		// When using /reload or some other obscure features, it can happen that players
		// are online
		// but not registered. So, we only continue when the player has been registered
		if (perPlayerSettings.containsKey(uniqueId.toString())) {
			ChestSortPlayerSetting setting = perPlayerSettings.get(p.getUniqueId().toString());
			
			File playerFile = new File(getDataFolder() + File.separator + "playerdata",
					p.getUniqueId().toString() + ".yml");
			YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
			playerConfig.set("sortingEnabled", setting.sortingEnabled);
			playerConfig.set("invSortingEnabled",setting.invSortingEnabled);
			playerConfig.set("hasSeenMessage", setting.hasSeenMessage);
			playerConfig.set("middleClick",setting.middleClick);
			playerConfig.set("shiftClick",setting.shiftClick);
			playerConfig.set("doubleClick",setting.doubleClick);
			playerConfig.set("shiftRightClick",setting.shiftRightClick);
			playerConfig.set("leftClick",setting.leftClick);
			playerConfig.set("rightClick",setting.rightClick);
			try {
				// Only saved if the config has been changed
				if(setting.changed) {
					if(debug) {
						getLogger().info("PlayerSettings for "+p.getName()+" have changed, saving to file.");
					}
					playerConfig.save(playerFile);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			perPlayerSettings.remove(uniqueId.toString());
		}
	}
	
	void load(boolean reload) {
		
		if(reload) {
			unregisterAllPlayers();
			reloadConfig();
			if(updateChecker != null) {
				updateChecker.stop();
			}
		}
		
		createConfig();
		debug = getConfig().getBoolean("debug");
		
		HandlerList.unregisterAll(this);
		
		if(debug) {
			ChestSortDebugger debugger = new ChestSortDebugger(this);
			getServer().getPluginManager().registerEvents(debugger, this);
		}
		
		hookCrackShot = getConfig().getBoolean("hook-crackshot")
				&& Bukkit.getPluginManager().getPlugin("CrackShot") instanceof Plugin;

		hookInventoryPages = getConfig().getBoolean("hook-inventorypages")
				&& Bukkit.getPluginManager().getPlugin("InventoryPages") instanceof Plugin;
			
		hookMinepacks = getConfig().getBoolean("hook-minepacks")
				&& Bukkit.getPluginManager().getPlugin("Minepacks") instanceof MinepacksPlugin;

		genericHook = new GenericGUIHook(this,getConfig().getBoolean("hook-generic"));

		saveDefaultCategories();

		verbose = getConfig().getBoolean("verbose");
		lgr = new ChestSortLogger(this,getConfig().getBoolean("log"));
		messages = new ChestSortMessages(this);
		organizer = new ChestSortOrganizer(this);
		settingsGUI = new ChestSortSettingsGUI(this);
		try {
			if(Class.forName("net.md_5.bungee.api.chat.BaseComponent") != null) {
				updateChecker = new PluginUpdateChecker(this, "https://api.jeff-media.de/chestsort/chestsort-latest-version.txt", "https://chestsort.de", "https://chestsort.de/changelog", "https://chestsort.de/donate");
			} else {
				getLogger().severe("You are using an unsupported server software! Consider switching to Spigot or Paper!");
				getLogger().severe("The Update Checker will NOT work when using CraftBukkit instead of Spigot/Paper!");
				PaperLib.suggestPaper(this);
			}
		} catch (ClassNotFoundException e) {
			getLogger().severe("You are using an unsupported server software! Consider switching to Spigot or Paper!");
			getLogger().severe("The Update Checker will NOT work when using CraftBukkit instead of Spigot/Paper!");
			PaperLib.suggestPaper(this);
		}
		listener = new ChestSortListener(this);
		api = new ChestSortAPIHandler(this);
		hotkeyCooldown = new HashMap<>();
		permissionsHandler = new ChestSortPermissionsHandler(this);
		updateCheckInterval = (int) (getConfig().getDouble("check-interval")*60*60);
		sortingMethod = getConfig().getString("sorting-method");
		playerVaultsHook = new PlayerVaultsHook(this);
		getServer().getPluginManager().registerEvents(listener, this);
		getServer().getPluginManager().registerEvents(settingsGUI, this);
		ChestSortChestSortCommand chestsortCommandExecutor = new ChestSortChestSortCommand(this);
		ChestSortTabCompleter tabCompleter = new ChestSortTabCompleter();
		this.getCommand("sort").setExecutor(chestsortCommandExecutor);
		this.getCommand("sort").setTabCompleter(tabCompleter);
		ChestSortInvSortCommand invsortCommandExecutor = new ChestSortInvSortCommand(this);
		this.getCommand("invsort").setExecutor(invsortCommandExecutor);
		this.getCommand("invsort").setTabCompleter(tabCompleter);

		if (verbose) {
			getLogger().info("Use permissions: " + getConfig().getBoolean("use-permissions"));
			getLogger().info("Current sorting method: " + sortingMethod);
			getLogger().info("Allow automatic chest sorting:" + getConfig().getBoolean("allow-automatic-sorting"));
			getLogger().info("  |- Chest sorting enabled by default: " + getConfig().getBoolean("sorting-enabled-by-default"));
			getLogger().info("  |- Sort time: " + getConfig().getString("sort-time"));
			getLogger().info("Allow automatic inventory sorting:" + getConfig().getBoolean("allow-automatic-inventory-sorting"));
			getLogger().info("  |- Inventory sorting enabled by default: " + getConfig().getBoolean("inv-sorting-enabled-by-default"));
			getLogger().info("Auto generate category files: " + getConfig().getBoolean("auto-generate-category-files"));
			getLogger().info("Allow hotkeys: " + getConfig().getBoolean("allow-sorting-hotkeys"));
			if(getConfig().getBoolean("allow-sorting-hotkeys")) {
				getLogger().info("Hotkeys enabled by default:");
				getLogger().info("  |- Middle-Click: " + getConfig().getBoolean("sorting-hotkeys.middle-click"));
				getLogger().info("  |- Shift-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-click"));
				getLogger().info("  |- Double-Click: " + getConfig().getBoolean("sorting-hotkeys.double-click"));
				getLogger().info("  |- Shift-Right-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
			}
			getLogger().info("Allow additional hotkeys: " + getConfig().getBoolean("allow-additional-hotkeys"));
			if(getConfig().getBoolean("allow-additional-hotkeys")) {
				getLogger().info("Additional hotkeys enabled by default:");
				getLogger().info("  |- Left-Click: " + getConfig().getBoolean("additional-hotkeys.left-click"));
				getLogger().info("  |- Right-Click: " + getConfig().getBoolean("additional-hotkeys.right-click"));
			}
			getLogger().info("Check for updates: " + getConfig().getString("check-for-updates"));
			if(getConfig().getString("check-for-updates").equalsIgnoreCase("true")) {
				getLogger().info("Check interval: " + getConfig().getString("check-interval") + " hours ("+updateCheckInterval+" seconds)");
			}
			getLogger().info("Categories: " + getCategoryList());
		}

		if(updateChecker!=null) {
			if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("true")) {
				updateChecker.check(updateCheckInterval);
			} // When set to on-startup, we check right now (delay 0)
			else if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("on-startup")) {
				updateChecker.check();
			}
		}

		registerMetrics();
		
		if(getConfig().getBoolean("dump")) {
			dump();
		}
		
		for(Player p : getServer().getOnlinePlayers()) {
			permissionsHandler.addPermissions(p);
		}
		
		// End Reload
		
	}

	void unregisterAllPlayers() {
		if(perPlayerSettings!=null && perPlayerSettings.size()>0) {
			Iterator<String> it = perPlayerSettings.keySet().iterator();
			while(it.hasNext()) {
				Player p = getServer().getPlayer(it.next());
				if(p != null) {
					unregisterPlayer(p);
				}
			}
		} else {
			perPlayerSettings = new HashMap<>();
		}
	}
	
	// Dumps all Materials into a csv file with their current category
	void dump() {
		try {
			File file = new File(getDataFolder() + File.separator + "dump.csv");
			FileOutputStream fos;
			fos = new FileOutputStream(file);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
			for(Material mat : Material.values()) {
				bw.write(mat.name()+","+organizer.getCategoryLinePair(mat.name()).getCategoryName());
				bw.newLine();
			}
			bw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean isInHotkeyCooldown(UUID uuid) {
		double cooldown = getConfig().getDouble(Config.HOTKEY_COOLDOWN)*1000;
		if(cooldown==0) return false;
		long lastUsage = hotkeyCooldown.containsKey(uuid) ? hotkeyCooldown.get(uuid) : 0;
		long currentTime = System.currentTimeMillis();
		long difference = currentTime-lastUsage;
		hotkeyCooldown.put(uuid,currentTime);
		debug("Difference: "+difference);
		return difference <= cooldown;
	}

	void registerPlayerIfNeeded(Player p) {
		// Players are stored by their UUID, so that name changes don't break player's
		// settings
		UUID uniqueId = p.getUniqueId();
	
		// Add player to map only if they aren't registered already
		if (!perPlayerSettings.containsKey(uniqueId.toString())) {
	
			// Player settings are stored in a file named after the player's UUID
			File playerFile = new File(getDataFolder() + File.separator + "playerdata",
					p.getUniqueId().toString() + ".yml");
			YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
			
			playerConfig.addDefault("invSortingEnabled", getConfig().getBoolean("inv-sorting-enabled-by-default"));
			playerConfig.addDefault("middleClick", getConfig().getBoolean("sorting-hotkeys.middle-click"));
			playerConfig.addDefault("shiftClick", getConfig().getBoolean("sorting-hotkeys.shift-click"));
			playerConfig.addDefault("doubleClick", getConfig().getBoolean("sorting-hotkeys.double-click"));
			playerConfig.addDefault("shiftRightClick", getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
			playerConfig.addDefault("leftClick", getConfig().getBoolean("additional-hotkeys.left-click"));
			playerConfig.addDefault("rightClick", getConfig().getBoolean("additional-hotkeys.right-click"));
	
			boolean activeForThisPlayer;
			boolean invActiveForThisPlayer;
			boolean middleClick, shiftClick, doubleClick, shiftRightClick, leftClick, rightClick;
			boolean changed = false;
	
			if (!playerFile.exists()) {
				// If the player settings file does not exist for this player, set it to the
				// default value
				activeForThisPlayer = getConfig().getBoolean("sorting-enabled-by-default");
				invActiveForThisPlayer = getConfig().getBoolean("inv-sorting-enabled-by-default");
				middleClick = getConfig().getBoolean("sorting-hotkeys.middle-click");
				shiftClick = getConfig().getBoolean("sorting-hotkeys.shift-click");
				doubleClick = getConfig().getBoolean("sorting-hotkeys.double-click");
				shiftRightClick = getConfig().getBoolean("sorting-hotkeys.shift-right-click");
				leftClick = getConfig().getBoolean("additional-hotkeys.left-click");
				rightClick = getConfig().getBoolean("additional-hotkeys.right-click");
				
				if(debug) {
					getLogger().info("Player "+p.getName()+" does not have player settings yet, using default values.");
				}
				
				// Because this is new a file, we have to save it on shutdown/disconnect
				changed=true;
			} else {
				// If the file exists, check if the player has sorting enabled
				activeForThisPlayer = playerConfig.getBoolean("sortingEnabled");
				invActiveForThisPlayer = playerConfig.getBoolean("invSortingEnabled",getConfig().getBoolean("inv-sorting-enabled-by-default"));
				middleClick = playerConfig.getBoolean("middleClick");
				shiftClick = playerConfig.getBoolean("shiftClick");
				doubleClick = playerConfig.getBoolean("doubleClick");
				shiftRightClick = playerConfig.getBoolean("shiftRightClick");
				leftClick = playerConfig.getBoolean("leftClick",getConfig().getBoolean("additional-hotkeys.left-click"));
				rightClick = playerConfig.getBoolean("rightClick",getConfig().getBoolean("additional-hotkeys.right-click"));
			}
	
			ChestSortPlayerSetting newSettings = new ChestSortPlayerSetting(activeForThisPlayer,invActiveForThisPlayer,middleClick,shiftClick,doubleClick,shiftRightClick,leftClick,rightClick,changed);
	
			// when "show-message-again-after-logout" is enabled, we don't care if the
			// player already saw the message
			if (!getConfig().getBoolean("show-message-again-after-logout")) {
				newSettings.hasSeenMessage = playerConfig.getBoolean("hasSeenMessage");
			}
	
			// Finally add the PlayerSetting object to the map
			perPlayerSettings.put(uniqueId.toString(), newSettings);
	
		}
	}

}
