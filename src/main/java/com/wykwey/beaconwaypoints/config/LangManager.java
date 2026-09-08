package com.wykwey.beaconwaypoints.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多语言消息管理
 */
public class LangManager {
    
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String language;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载语言文件
     */
    public void loadLanguages() {
        plugin.saveResource("lang/en.yml", false);
        plugin.saveResource("lang/zh.yml", false);
        loadLanguage("en");
        loadLanguage("zh");
        this.language = plugin.getConfig().getString("language", "en");
    }

    private void loadLanguage(String lang) {
        File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (file.exists()) {
            languages.put(lang, YamlConfiguration.loadConfiguration(file));
        }
    }

    /**
     * 获取消息
     */
    public String getMessage(String key) {
        FileConfiguration config = languages.get(language);
        return config != null ? config.getString(key, key) : key;
    }

    /**
     * 获取消息并替换变量
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    /**
     * 获取字符串列表消息（如 GUI lore 多行文本）
     */
    public List<String> getMessageList(String key) {
        FileConfiguration config = languages.get(language);
        return config != null ? config.getStringList(key) : List.of();
    }

    /**
     * 获取字符串列表消息并逐行替换变量
     */
    public List<String> getMessageList(String key, Map<String, String> placeholders) {
        return getMessageList(key).stream()
            .map(line -> {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    line = line.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                return line;
            })
            .toList();
    }

    /**
     * 重载语言文件
     */
    public void reloadLanguages() {
        languages.clear();
        loadLanguages();
    }
}
