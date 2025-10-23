# Important

This is a fork of the original ChestSort which strips out update checker functionality and the JeffLib library.
This is because the original ChestSort Author shutdown his services, which the original code relied on to check for updates and to pull the JeffLib during compilation.
The JeffLib replacements are probably feature incomplete, and may contain bugs.

**My goal was to make the Plugin run/compilable for modern minecraft servers.**
**I take no responsibility for any issues or data loss you experience by running this plugin.**

# ChestSort

1.8 to 1.21.10 compatible Minecraft-/Spigot-Plugin to allow automatic chest and inventory sorting.

## Download & more information

Please see the related topic at spigotmc.org for information regarding the commands, permissions and download links:

https://www.spigotmc.org/resources/1-13-chestsort.59773/

## Maven repository

If you want to use ChestSort as dependency for your own plugin, you can use our public maven repository. More information can be found in the [API documentation](https://github.com/JEFF-Media-GbR/Spigot-ChestSort/blob/master/HOW_TO_USE_API.md).

## Building .jar file

Run `mvn package`.
~~To build the .jar file, you will need maven. Also, the CrackShot library is in no public repository, so please create a directory called `lib` and put the latest CrackShot.jar file [(available here)](https://www.spigotmc.org/resources/crackshot-guns.48301/) inside it.~~ (Not required as of ChestSort 9.6.0+)

## API

If you want to use ChestSort's advanced sorting features for your own plugin, you can use the ChestSort API. It provides methods to sort any given inventory, following the rules you have specified in your ChestSort's plugin.yml and the corresponding category files.

More information about the API can be found [HERE](https://github.com/JEFF-Media-GbR/Spigot-ChestSort/blob/master/HOW_TO_USE_API.md).

## Technical stuff

ChestSort takes an instance of org.bukkit.inventory.Inventory and copies the contents. The resulting array is sorted by rules defined in the config.yml. This takes far less than one millisecond for a whole chest. So there should be no problems even on big servers, where hundreds of players are using chests at the same time.
The plugin should cause no lag at all.
