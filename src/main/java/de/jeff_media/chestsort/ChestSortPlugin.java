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

package de.jeff_media.chestsort;

import de.jeff_media.chestsort.commands.ChestSortCommand;
import de.jeff_media.chestsort.commands.InvSortCommand;
import de.jeff_media.chestsort.commands.TabCompleter;
import de.jeff_media.chestsort.config.Config;
import de.jeff_media.chestsort.config.ConfigUpdater;
import de.jeff_media.chestsort.config.Messages;
import de.jeff_media.chestsort.data.Category;
import de.jeff_media.chestsort.data.PlayerSetting;
import de.jeff_media.chestsort.gui.GUIListener;
import de.jeff_media.chestsort.gui.SettingsGUI;
import de.jeff_media.chestsort.gui.tracker.CustomGUITracker;
import de.jeff_media.chestsort.gui.tracker.CustomGUIType;
import de.jeff_media.chestsort.handlers.ChestSortOrganizer;
import de.jeff_media.chestsort.handlers.ChestSortPermissionsHandler;
import de.jeff_media.chestsort.handlers.Debugger;
import de.jeff_media.chestsort.handlers.Logger;
import de.jeff_media.chestsort.hooks.GenericGUIHook;
import de.jeff_media.chestsort.listeners.ChestSortListener;
import de.jeff_media.chestsort.placeholders.Placeholders;
import de.jeff_media.chestsort.utils.Utils;
import com.jeff_media.jefflib.data.McVersion;
import com.jeff_media.jefflib.NBTAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class ChestSortPlugin extends JavaPlugin {

    private static ChestSortPlugin instance;
    public ChestSortOrganizer organizer; // Must be public for the API
    boolean hotkeyGUI = true;
    private GenericGUIHook genericHook;
    private boolean debug = false;
    private ArrayList<String> disabledWorlds;
    private HashMap<UUID, Long> hotkeyCooldown;
    private Logger lgr;
    private ChestSortListener chestSortListener;
    private Messages messages;
    private Map<String, PlayerSetting> perPlayerSettings = new HashMap<>();
    private ChestSortPermissionsHandler permissionsHandler;
    private SettingsGUI settingsGUI;
    private String sortingMethod;
    private boolean usingMatchingConfig = true;
    private boolean verbose = true;
    private YamlConfiguration guiConfig = new YamlConfiguration();
    private int settingsFingerprint = 0;

    public List<Pattern> blacklistedInventoryHolderClassNames = new ArrayList<>();

    public static ChestSortPlugin getInstance() {
        return instance;
    }

    public YamlConfiguration getGuiConfig() { return guiConfig; }

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
        createGUIConfig();
        reloadConfig();

        // Load disabled-worlds. If it does not exist in the config, it returns null.
        // That's no problem
        setDisabledWorlds((ArrayList<String>) getConfig().getStringList(Config.DISABLED_WORLDS));

        ConfigUpdater.updateConfig();

        createDirectories();

        setDefaultConfigValues();

    }

    private void createGUIConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        if(!guiFile.exists()) {
            saveResource("gui.yml",false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
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

    public void debug(String t) {
        if (isDebug()) getLogger().warning("[DEBUG] " + t);
    }

    public void debug2(String t) {
        if (getConfig().getBoolean(Config.DEBUG2)) getLogger().warning("[DEBUG2] " + t);
    }

    // Dumps all Materials into a csv file with their current category
    void dump() {
        try {
            File file = new File(getDataFolder() + File.separator + "dump.csv");
            FileOutputStream fos;
            fos = new FileOutputStream(file);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
            for (Material mat : Material.values()) {
                bw.write(mat.name() + "," + getOrganizer().getCategoryLinePair(mat.name()).getCategoryName());
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private String getCategoryList() {
        StringBuilder list = new StringBuilder();
        Category[] categories = getOrganizer().categories.toArray(new Category[0]);
        Arrays.sort(categories);
        for (Category category : categories) {
            list.append(category.name).append(" (");
            list.append(category.typeMatches.length).append("), ");
        }
        list = new StringBuilder(list.substring(0, list.length() - 2));
        return list.toString();

    }

    public ArrayList<String> getDisabledWorlds() {
        return disabledWorlds == null ? new ArrayList<>() : disabledWorlds;
    }

    public void setDisabledWorlds(ArrayList<String> disabledWorlds) {
        this.disabledWorlds = disabledWorlds;
    }

    public GenericGUIHook getGenericHook() {
        return genericHook;
    }

    public void setGenericHook(GenericGUIHook genericHook) {
        this.genericHook = genericHook;
    }

    public HashMap<UUID, Long> getHotkeyCooldown() {
        return hotkeyCooldown;
    }

    public void setHotkeyCooldown(HashMap<UUID, Long> hotkeyCooldown) {
        this.hotkeyCooldown = hotkeyCooldown;
    }

    public Logger getLgr() {
        return lgr;
    }

    public void setLgr(Logger lgr) {
        this.lgr = lgr;
    }

    public ChestSortListener getListener() {
        return chestSortListener;
    }

    public void setListener(ChestSortListener chestSortListener) {
        this.chestSortListener = chestSortListener;
    }

    public Messages getMessages() {
        return messages;
    }

    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    public ChestSortOrganizer getOrganizer() {
        return organizer;
    }

    public void setOrganizer(ChestSortOrganizer organizer) {
        this.organizer = organizer;
    }

    public Map<String, PlayerSetting> getPerPlayerSettings() {
        return perPlayerSettings;
    }

    public void setPerPlayerSettings(Map<String, PlayerSetting> perPlayerSettings) {
        this.perPlayerSettings = perPlayerSettings;
    }

    public ChestSortPermissionsHandler getPermissionsHandler() {
        return permissionsHandler;
    }

    public void setPermissionsHandler(ChestSortPermissionsHandler permissionsHandler) {
        this.permissionsHandler = permissionsHandler;
    }

    public PlayerSetting getPlayerSetting(Player p) {
        registerPlayerIfNeeded(p);
        return getPerPlayerSettings().get(p.getUniqueId().toString());
    }

    public SettingsGUI getSettingsGUI() {
        return settingsGUI;
    }

    public void setSettingsGUI(SettingsGUI settingsGUI) {
        this.settingsGUI = settingsGUI;
    }

    public String getSortingMethod() {
        return sortingMethod;
    }

    public void setSortingMethod(String sortingMethod) {
        this.sortingMethod = sortingMethod;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isHotkeyGUI() {
        // TODO: Remove, it's unused
        return hotkeyGUI;
    }

    public boolean isInHotkeyCooldown(UUID uuid) {
        double cooldown = getConfig().getDouble(Config.HOTKEY_COOLDOWN) * 1000;
        if (cooldown == 0) return false;
        long lastUsage = getHotkeyCooldown().containsKey(uuid) ? getHotkeyCooldown().get(uuid) : 0;
        long currentTime = System.currentTimeMillis();
        long difference = currentTime - lastUsage;
        getHotkeyCooldown().put(uuid, currentTime);
        debug("Difference: " + difference);
        return difference <= cooldown;
    }

    public boolean isSortingEnabled(Player p) {
        if (getPerPlayerSettings() == null) {
            setPerPlayerSettings(new HashMap<>());
        }
        registerPlayerIfNeeded(p);
        return getPerPlayerSettings().get(p.getUniqueId().toString()).sortingEnabled;
    }

    public boolean isUsingMatchingConfig() {
        return usingMatchingConfig;
    }

    public void setUsingMatchingConfig(boolean usingMatchingConfig) {
        this.usingMatchingConfig = usingMatchingConfig;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void load(boolean reload) {

        settingsFingerprint = 0;
        File fingerprintFile = new File(getDataFolder(), "settings.fingerprint");
        if(fingerprintFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(fingerprintFile);
            settingsFingerprint = yaml.getInt("v",0);
        }

        if (reload) {
            unregisterAllPlayers();
            reloadConfig();
        }

        createConfig();
        setDebug(getConfig().getBoolean("debug"));

        HandlerList.unregisterAll(this);

        if (isDebug()) {
            Debugger debugger = new Debugger(this);
            getServer().getPluginManager().registerEvents(debugger, this);
        }

        setGenericHook(new GenericGUIHook(this, getConfig().getBoolean("hook-generic")));

        saveDefaultCategories();

        blacklistedInventoryHolderClassNames.clear();
        for(String line : getConfig().getStringList("blocked-inventory-holders-regex")) {
            try {
                Pattern pattern = Pattern.compile(line);
                blacklistedInventoryHolderClassNames.add(pattern);
            } catch (Exception e) {
                getLogger().warning("Invalid regex in blocked-inventory-holders-regex: " + line);
                continue;
            }
        }

        setVerbose(getConfig().getBoolean("verbose"));
        setLgr(new Logger(this, getConfig().getBoolean("log")));
        //noinspection InstantiationOfUtilityClass
        new Messages();
        setOrganizer(new ChestSortOrganizer(this));
        setSettingsGUI(new SettingsGUI(this));
        setListener(new ChestSortListener(this));
        setHotkeyCooldown(new HashMap<>());
        setPermissionsHandler(new ChestSortPermissionsHandler(this));
        setSortingMethod(getConfig().getString("sorting-method"));
        getServer().getPluginManager().registerEvents(getListener(), this);
        getServer().getPluginManager().registerEvents(getSettingsGUI(), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        ChestSortCommand chestsortCommandExecutor = new ChestSortCommand(this);
        TabCompleter tabCompleter = new TabCompleter();
        this.getCommand("sort").setExecutor(chestsortCommandExecutor);
        this.getCommand("sort").setTabCompleter(tabCompleter);
        InvSortCommand invsortCommandExecutor = new InvSortCommand(this);
        this.getCommand("invsort").setExecutor(invsortCommandExecutor);
        this.getCommand("invsort").setTabCompleter(tabCompleter);
        //this.getCommand("chestsortadmin").setExecutor(new AdminCommand(this));

        if (isVerbose()) {
            getLogger().info("Use permissions: " + getConfig().getBoolean("use-permissions"));
            getLogger().info("Current sorting method: " + getSortingMethod());
            getLogger().info("Allow automatic chest sorting:" + getConfig().getBoolean("allow-automatic-sorting"));
            getLogger().info("  |- Chest sorting enabled by default: " + getConfig().getBoolean("sorting-enabled-by-default"));
            getLogger().info("  |- Sort time: " + getConfig().getString("sort-time"));
            getLogger().info("Allow automatic inventory sorting:" + getConfig().getBoolean("allow-automatic-inventory-sorting"));
            getLogger().info("  |- Inventory sorting enabled by default: " + getConfig().getBoolean("inv-sorting-enabled-by-default"));
            getLogger().info("Auto generate category files: " + getConfig().getBoolean("auto-generate-category-files"));
            getLogger().info("Allow hotkeys: " + getConfig().getBoolean("allow-sorting-hotkeys"));
            if (getConfig().getBoolean("allow-sorting-hotkeys")) {
                getLogger().info("Hotkeys enabled by default:");
                getLogger().info("  |- Middle-Click: " + getConfig().getBoolean("sorting-hotkeys.middle-click"));
                getLogger().info("  |- Shift-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-click"));
                getLogger().info("  |- Double-Click: " + getConfig().getBoolean("sorting-hotkeys.double-click"));
                getLogger().info("  |- Shift-Right-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
            }
            getLogger().info("Allow additional hotkeys: " + getConfig().getBoolean("allow-additional-hotkeys"));
            if (getConfig().getBoolean("allow-additional-hotkeys")) {
                getLogger().info("Additional hotkeys enabled by default:");
                getLogger().info("  |- Left-Click: " + getConfig().getBoolean("additional-hotkeys.left-click"));
                getLogger().info("  |- Right-Click: " + getConfig().getBoolean("additional-hotkeys.right-click"));
            }
            getLogger().info("Categories: " + getCategoryList());
        }


        if (getConfig().getBoolean("dump")) {
            dump();
        }

        for (Player p : getServer().getOnlinePlayers()) {
            getPermissionsHandler().addPermissions(p);
        }

        // End Reload

    }

    @Override
    public void onDisable() {
        // We have to unregister every player to save their perPlayerSettings
        for (Player player : getServer().getOnlinePlayers()) {
            if(CustomGUITracker.getType(player.getOpenInventory()) == CustomGUIType.SETTINGS) {
                player.closeInventory();
            }
            unregisterPlayer(player);
            getPermissionsHandler().removePermissions(player);
        }
    }

    @Override
    public void onEnable() {

        instance = this;

        load(false);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders(this).register();
        }
    }

    public void incrementFingerprint() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v",settingsFingerprint + 1);
        settingsFingerprint++;
        try {
            yaml.save(new File(getDataFolder(),"settings.fingerprint"));
            load(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void registerPlayerIfNeeded(Player p) {
        // Players are stored by their UUID, so that name changes don't break player's
        // settings
        UUID uniqueId = p.getUniqueId();

        // Add player to map only if they aren't registered already
        if (!getPerPlayerSettings().containsKey(uniqueId.toString())) {

            // Player settings are stored in a file named after the player's UUID
            File playerFile = new File(getDataFolder() + File.separator + "playerdata",
                    p.getUniqueId() + ".yml");
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            playerConfig.addDefault("sortingEnabled", getConfig().getBoolean("sorting-enabled-by-default"));
            playerConfig.addDefault("invSortingEnabled", getConfig().getBoolean("inv-sorting-enabled-by-default"));
            playerConfig.addDefault("middleClick", getConfig().getBoolean("sorting-hotkeys.middle-click"));
            playerConfig.addDefault("shiftClick", getConfig().getBoolean("sorting-hotkeys.shift-click"));
            playerConfig.addDefault("doubleClick", getConfig().getBoolean("sorting-hotkeys.double-click"));
            playerConfig.addDefault("shiftRightClick", getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
            playerConfig.addDefault("leftClick", getConfig().getBoolean("additional-hotkeys.left-click"));
            playerConfig.addDefault("rightClick", getConfig().getBoolean("additional-hotkeys.right-click"));
            playerConfig.addDefault("leftClickOutside", getConfig().getBoolean("left-click-to-sort-enabled-by-default"));

            boolean activeForThisPlayer;
            boolean invActiveForThisPlayer;
            boolean middleClick;
            boolean shiftClick;
            boolean doubleClick;
            boolean shiftRightClick;
            boolean leftClick;
            boolean rightClick;
            boolean leftClickFromOutside;
            boolean changed;
            boolean hasSeenMessage;

            if (playerFile.exists() || !McVersion.current().isAtLeast(1,14,4)) {
                // If the player settings file does not exist for this player, set it to the
                // default value
                activeForThisPlayer = playerConfig.getBoolean("sortingEnabled");
                invActiveForThisPlayer = playerConfig.getBoolean("invSortingEnabled");
                middleClick = playerConfig.getBoolean("middleClick");
                shiftClick = playerConfig.getBoolean("shiftClick");
                doubleClick = playerConfig.getBoolean("doubleClick");
                shiftRightClick = playerConfig.getBoolean("shiftRightClick");
                leftClickFromOutside = playerConfig.getBoolean("leftClickOutside");
                leftClick = playerConfig.getBoolean("leftClick");
                rightClick = playerConfig.getBoolean("rightClick");
                hasSeenMessage = playerConfig.getBoolean("hasSeenMessage");

                changed = true;

                if (McVersion.current().isAtLeast(1,14,4)) {
                    if (playerFile.delete()) {
                        this.getLogger().info("Converted old .yml playerdata file to NBT tags for player " + p.getName());
                    } else {
                        this.getLogger().warning("Could not remove old playerdata .yml file for player " + p.getName());
                    }
                }
            } else {
                // If the file exists, check if the player has sorting enabled
                // NBT Values

                String fingerprint = getFingerprint();

                activeForThisPlayer = Boolean.parseBoolean(NBTAPI.getNBT(p, "sortingEnabled" + fingerprint, String.valueOf(playerConfig.getBoolean("sortingEnabled"))));
                invActiveForThisPlayer = Boolean.parseBoolean(NBTAPI.getNBT(p, "invSortingEnabled" + fingerprint, String.valueOf(playerConfig.getBoolean("invSortingEnabled", getConfig().getBoolean("inv-sorting-enabled-by-default")))));
                middleClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "middleClick" + fingerprint, String.valueOf(playerConfig.getBoolean("middleClick"))));
                shiftClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "shiftClick" + fingerprint, String.valueOf(playerConfig.getBoolean("shiftClick"))));
                doubleClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "doubleClick" + fingerprint, String.valueOf(playerConfig.getBoolean("doubleClick"))));
                shiftRightClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "shiftRightClick" + fingerprint, String.valueOf(playerConfig.getBoolean("shiftRightClick"))));
                leftClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "leftClick" + fingerprint, String.valueOf(playerConfig.getBoolean("leftClick", getConfig().getBoolean("additional-hotkeys.left-click")))));
                rightClick = Boolean.parseBoolean(NBTAPI.getNBT(p, "rightClick" + fingerprint, String.valueOf(playerConfig.getBoolean("rightClick", getConfig().getBoolean("additional-hotkeys.right-click")))));
                leftClickFromOutside = Boolean.parseBoolean(NBTAPI.getNBT(p, "leftClickOutside" + fingerprint, String.valueOf(playerConfig.getBoolean("leftClickOutside", getConfig().getBoolean("left-click-to-sort-enabled-by-default")))));
                hasSeenMessage = Boolean.parseBoolean(NBTAPI.getNBT(p, "hasSeenMessage" + fingerprint, String.valueOf("false")));
                //System.out.println("Loading playersetting from NBT");
                if(getConfig().getBoolean("show-message-again-after-logout")) {
                    //System.out.println("show-message-again-after-logout is true, sooo...");
                    hasSeenMessage = false;
                }

                changed = true;
            }

            PlayerSetting newSettings = new PlayerSetting(activeForThisPlayer, invActiveForThisPlayer, middleClick, shiftClick, doubleClick, shiftRightClick, leftClick, rightClick, leftClickFromOutside, changed, hasSeenMessage);

            // when "show-message-again-after-logout" is enabled, we don't care if the
            // player already saw the message
            if (!getConfig().getBoolean("show-message-again-after-logout")) {
                NBTAPI.getNBT(p, "hasSeenMessage", String.valueOf(false));
            }

            // Finally add the PlayerSetting object to the map
            getPerPlayerSettings().put(uniqueId.toString(), newSettings);

        }
    }

    private String getFingerprint() {
        String fingerprint = "";
        if(settingsFingerprint > 0) {
            fingerprint = "-" + settingsFingerprint;
        }
        return fingerprint;
    }

    // Saves default category files, when enabled in the config
    private void saveDefaultCategories() {

        // Abort when auto-generate-category-files is set to false in config.yml
        if (!getConfig().getBoolean("auto-generate-category-files", true)) {
            return;
        }

        // Isn't there a smarter way to find all the 9** files in the .jar?
        String[] defaultCategories = {"900-weapons", "905-common-tools", "907-other-tools", "909-food", "910-valuables", "920-armor-and-arrows", "930-brewing",
                "950-redstone", "960-wood", "970-stone", "980-plants", "981-corals", "_ReadMe - Category files"};

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
                name = name + ".txt";
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

    private void setDefaultConfigValues() {
        // If you use an old config file with missing options, the following default
        // values will be used instead
        // for every missing option.
        // By default, sorting is disabled. Every player has to run /chestsort once
        getConfig().addDefault("use-permissions", true);
        getConfig().addDefault("allow-automatic-sorting", true);
        getConfig().addDefault("allow-automatic-inventory-sorting", true);
        getConfig().addDefault("allow-left-click-to-sort", true);
        getConfig().addDefault("left-click-to-sort-enabled-by-default", false);
        getConfig().addDefault("sorting-enabled-by-default", false);
        getConfig().addDefault("inv-sorting-enabled-by-default", false);
        getConfig().addDefault("show-message-when-using-chest", true);
        getConfig().addDefault("show-message-when-using-chest-and-sorting-is-enabled", false);
        getConfig().addDefault("show-message-again-after-logout", true);
        getConfig().addDefault("sorting-method", "{category},{itemsFirst},{name},{color}");
        getConfig().addDefault("allow-player-inventory-sorting", false);
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
        getConfig().addDefault("allow-commands", true);

        getConfig().addDefault("hook-generic", true);
        getConfig().addDefault("prevent-sorting-null-inventories", false);

        getConfig().addDefault("mute-protection-plugins", false);

        getConfig().addDefault("verbose", true); // Prints some information in onEnable()
    }

    private void showOldConfigWarning() {
        getLogger().warning("==============================================");
        getLogger().warning("You were using an old config file. ChestSort");
        getLogger().warning("has updated the file to the newest version.");
        getLogger().warning("Your changes have been kept.");
        getLogger().warning("==============================================");
    }

    void unregisterAllPlayers() {
        if (getPerPlayerSettings() != null && getPerPlayerSettings().size() > 0) {
            for (String s : getPerPlayerSettings().keySet()) {
                Player p = getServer().getPlayer(s);
                if (p != null) {
                    unregisterPlayer(p);
                }
            }
        } else {
            setPerPlayerSettings(new HashMap<>());
        }
    }

    // Unregister a player and save their settings in the playerdata folder
    public void unregisterPlayer(Player p) {
        // File will be named by the player's uuid. This will prevent problems on player
        // name changes.
        UUID uniqueId = p.getUniqueId();

        // When using /reload or some other obscure features, it can happen that players
        // are online
        // but not registered. So, we only continue when the player has been registered
        if (getPerPlayerSettings().containsKey(uniqueId.toString())) {
            PlayerSetting setting = getPerPlayerSettings().get(p.getUniqueId().toString());

            if (McVersion.current().isAtLeast(1,14,4)) {

                for(NamespacedKey key : p.getPersistentDataContainer().getKeys()) {
                    if(key.getKey().equals(new NamespacedKey(this,"test").getKey())) {
                        p.getPersistentDataContainer().remove(key);
                    }
                }

                String fingerprint = getFingerprint();

                NBTAPI.addNBT(p, "sortingEnabled" + fingerprint, String.valueOf(setting.sortingEnabled));
                NBTAPI.addNBT(p, "invSortingEnabled" + fingerprint, String.valueOf(setting.invSortingEnabled));
                NBTAPI.addNBT(p, "hasSeenMessage" + fingerprint, String.valueOf(setting.hasSeenMessage));
                NBTAPI.addNBT(p, "middleClick" + fingerprint, String.valueOf(setting.middleClick));
                NBTAPI.addNBT(p, "shiftClick" + fingerprint, String.valueOf(setting.shiftClick));
                NBTAPI.addNBT(p, "doubleClick" + fingerprint, String.valueOf(setting.doubleClick));
                NBTAPI.addNBT(p, "shiftRightClick" + fingerprint, String.valueOf(setting.shiftRightClick));
                NBTAPI.addNBT(p, "leftClick" + fingerprint, String.valueOf(setting.leftClick));
                NBTAPI.addNBT(p, "rightClick" + fingerprint, String.valueOf(setting.rightClick));
                NBTAPI.addNBT(p, "leftClickOutside" + fingerprint, String.valueOf(setting.leftClickOutside));
            } else {

                File playerFile = new File(getDataFolder() + File.separator + "playerdata", p.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                playerConfig.set("sortingEnabled", setting.sortingEnabled);
                playerConfig.set("invSortingEnabled", setting.invSortingEnabled);
                playerConfig.set("hasSeenMessage", setting.hasSeenMessage);
                playerConfig.set("middleClick", setting.middleClick);
                playerConfig.set("shiftClick", setting.shiftClick);
                playerConfig.set("doubleClick", setting.doubleClick);
                playerConfig.set("shiftRightClick", setting.shiftRightClick);
                playerConfig.set("leftClick", setting.leftClick);
                playerConfig.set("rightClick", setting.rightClick);
                playerConfig.set("leftClickOutside", setting.leftClickOutside);
                try {
                    // Only saved if the config has been changed
                    if (setting.changed) {
                        if (isDebug()) {
                            getLogger().info("PlayerSettings for " + p.getName() + " have changed, saving to file.");
                        }
                        playerConfig.save(playerFile);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            getPerPlayerSettings().remove(uniqueId.toString());
        }
    }

}
