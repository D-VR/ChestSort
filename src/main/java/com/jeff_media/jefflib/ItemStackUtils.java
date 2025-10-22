package com.jeff_media.jefflib;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import de.jeff_media.chestsort.ChestSortPlugin;

/**
 * Simplified replacement for JeffLib's {@code ItemStackUtils}.
 * Provides the functionality required by the ChestSort plugin.
 */
public final class ItemStackUtils {

    private ItemStackUtils() {
        // utility class
    }

    public static ItemStack fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            Bukkit.getLogger().warning("[ChestSort] Unknown material in gui.yml: " + materialName);
            material = Material.STONE;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (section.contains("display-name")) {
                meta.setDisplayName(TextUtils.format(section.getString("display-name")));
            }

            if (section.isList("lore")) {
                List<String> lore = section.getStringList("lore").stream()
                        .map(TextUtils::format)
                        .collect(Collectors.toList());
                meta.setLore(lore);
            } else if (section.isString("lore")) {
                meta.setLore(List.of(TextUtils.format(section.getString("lore"))));
            }

            if (section.isInt("custom-model-data")) {
                try {
                    meta.setCustomModelData(section.getInt("custom-model-data"));
                } catch (NoSuchMethodError ignored) {
                    // custom model data not supported on this server version
                }
            }

            if (meta instanceof Damageable) {
                int damage = section.getInt("damage", 0);
                if (damage > 0) {
                    ((Damageable) meta).setDamage(damage);
                }
            }

            if (section.isConfigurationSection("enchantments")) {
                ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
                if (enchSection != null) {
                    for (String key : enchSection.getKeys(false)) {
                        Enchantment enchantment = resolveEnchantment(key);
                        if (enchantment != null) {
                            int level = enchSection.getInt(key, 1);
                            meta.addEnchant(enchantment, Math.max(1, level), true);
                        } else {
                            Bukkit.getLogger().warning("[ChestSort] Unknown enchantment in gui.yml: " + key);
                        }
                    }
                }
            }

            if (section.getBoolean("unbreakable", false)) {
                try {
                    meta.setUnbreakable(true);
                } catch (NoSuchMethodError ignored) {
                    // not supported on older API versions
                }
            }

            if (section.isList("item-flags")) {
                Collection<String> flags = section.getStringList("item-flags");
                for (String flagName : flags) {
                    try {
                        ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
                        meta.addItemFlags(flag);
                    } catch (IllegalArgumentException ex) {
                        Bukkit.getLogger().warning("[ChestSort] Unknown item flag in gui.yml: " + flagName);
                    }
                }
            }

            if (section.getBoolean("prevent-stacking", false)) {
                ChestSortPlugin plugin = ChestSortPlugin.getInstance();
                if (plugin != null) {
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "prevent-stacking"), PersistentDataType.STRING, UUID.randomUUID().toString());
                }
            }

            item.setItemMeta(meta);
        }

        applyBase64TextureIfPresent(item, section.getString("base64"));

        return item;
    }

    private static Enchantment resolveEnchantment(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(normalized));
        if (enchantment == null) {
            enchantment = Enchantment.getByName(name.toUpperCase(Locale.ROOT));
        }
        return enchantment;
    }

    private static void applyBase64TextureIfPresent(ItemStack item, String base64) {
        if (base64 == null || base64.isEmpty()) {
            return;
        }
        if (item.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof SkullMeta)) {
            return;
        }

        SkullMeta skullMeta = (SkullMeta) meta;
        if (!setProfileUsingReflection(skullMeta, base64)) {
            Bukkit.getLogger().warning("[ChestSort] Failed to apply skull texture from base64 string.");
        }
        item.setItemMeta(skullMeta);
    }

    private static boolean setProfileUsingReflection(SkullMeta meta, String base64) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            Constructor<?> profileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object profile = profileConstructor.newInstance(UUID.randomUUID(), null);

            Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
            Object property = propertyConstructor.newInstance("textures", base64);

            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(profile);
            Method putMethod = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(propertyMap, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
