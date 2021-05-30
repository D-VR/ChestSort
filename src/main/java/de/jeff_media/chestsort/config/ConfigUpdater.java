package de.jeff_media.chestsort.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

import de.jeff_media.chestsort.ChestSortPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.jeff_media.chestsort.utils.Utils;

public class ConfigUpdater {

	final ChestSortPlugin plugin;

	public ConfigUpdater(ChestSortPlugin jeffChestSortPlugin) {
		this.plugin = jeffChestSortPlugin;
	}

	// Admins hate config updates. Just relax and let ChestSort update to the newest
	// config version
	// Don't worry! Your changes will be kept

	public void updateConfig() {
		
		// hotkeys has been renamed to sorting-hotkeys
		if(plugin.getConfig().isSet("hotkeys.middle-click")) {
			plugin.getConfig().set("sorting-hotkeys.middle-click", plugin.getConfig().getBoolean("hotkeys.middle-click"));
			plugin.getConfig().set("sorting-hotkeys.shift-click", plugin.getConfig().getBoolean("hotkeys.shift-click"));
			plugin.getConfig().set("sorting-hotkeys.double-click", plugin.getConfig().getBoolean("hotkeys.double-click"));
			plugin.getConfig().set("sorting-hotkeys.shift-right-click", plugin.getConfig().getBoolean("hotkeys.shift-right-click"));
		}

		// allow-hotkeys has been renamed to allow-sorting-hotkeys
		if(plugin.getConfig().isSet("allow-hotkeys")) {
			plugin.getConfig().set("allow-sorting-hotkeys",plugin.getConfig().getBoolean("allow-hotkeys"));
		}

		try {
			Files.deleteIfExists(new File(plugin.getDataFolder().getAbsolutePath()+File.separator+"config.old.yml").toPath());
		} catch (IOException ignored) {

		}

		if (plugin.debug)
			plugin.getLogger().info("rename config.yml -> config.old.yml");
		Utils.renameFileInPluginDir(plugin, "config.yml", "config.old.yml");
		if (plugin.debug)
			plugin.getLogger().info("saving new config.yml");
		plugin.saveDefaultConfig();

		File oldConfigFile = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.old.yml");
		FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);

		try {
			oldConfig.load(oldConfigFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		Map<String, Object> oldValues = oldConfig.getValues(false);

		// Read default config to keep comments
		ArrayList<String> linesInDefaultConfig = new ArrayList<>();
		try {

			Scanner scanner = new Scanner(
					new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml"),"UTF-8");
			while (scanner.hasNextLine()) {
				linesInDefaultConfig.add(scanner.nextLine() + "");
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ArrayList<String> newLines = new ArrayList<>();
		for (String line : linesInDefaultConfig) {
			String newline = line;
			if (line.startsWith("config-version:")) {
				// dont replace config-version
			} else if (line.startsWith("disabled-worlds:")) {
				newline = null;
				newLines.add("disabled-worlds:");
				if (plugin.disabledWorlds != null) {
					for (String disabledWorld : plugin.disabledWorlds) {
						newLines.add("- " + disabledWorld);
					}
				}
			} else if (line.startsWith("sorting-hotkeys:") || line.startsWith("additional-hotkeys:")) {
				// dont replace hotkeys root part
			} else if (line.startsWith("  middle-click:")) {
				newline = "  middle-click: " + plugin.getConfig().getBoolean("sorting-hotkeys.middle-click");
			} else if (line.startsWith("  shift-click:")) {
				newline = "  shift-click: " + plugin.getConfig().getBoolean("sorting-hotkeys.shift-click");
			} else if (line.startsWith("  double-click:")) {
				newline = "  double-click: " + plugin.getConfig().getBoolean("sorting-hotkeys.double-click");
			} else if (line.startsWith("  shift-right-click:")) {
				newline = "  shift-right-click: " + plugin.getConfig().getBoolean("sorting-hotkeys.shift-right-click");
			} else if (line.startsWith("  left-click:")) {
				newline = "  left-click: " + plugin.getConfig().getBoolean("additional-hotkeys.left-click");
			} else if (line.startsWith("  right-click:")) {
				newline = "  right-click: " + plugin.getConfig().getBoolean("additional-hotkeys.right-click");
			} else {
				for (String node : oldValues.keySet()) {
					if (line.startsWith(node + ":")) {

						String quotes = "";

						if (node.equalsIgnoreCase("sorting-method")) // needs single quotes
							quotes = "'";
						if (node.startsWith("message-")) // needs double quotes
							quotes = "\"";

						newline = node + ": " + quotes + oldValues.get(node).toString() + quotes;
						if (plugin.debug)
							plugin.getLogger().info("Updating config node " + newline);
						break;
					}
				}
			}
			if (newline != null)
				newLines.add(newline);
		}

		BufferedWriter fw;
		String[] linesArray = newLines.toArray(new String[linesInDefaultConfig.size()]);
		try {
			fw = Files.newBufferedWriter(new File(plugin.getDataFolder().getAbsolutePath(),"config.yml").toPath(), StandardCharsets.UTF_8);
			for (String s : linesArray) {
				fw.write(s + "\n");
			}
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Utils.renameFileInPluginDir(plugin, "config.yml.default", "config.yml");

	}

}
