package pt.henrique.market.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pt.henrique.market.Market;
import pt.henrique.market.hook.PlayerSettingsHook;
import pt.henrique.market.util.TextUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Manages plugin messages and localization.
 * Loads ALL available lang files on startup and supports per-player locale
 * via the PlayerSettings hook, falling back to the global config language.
 */
public class MessageManager {

    private final Market plugin;

    /** All loaded language configs keyed by locale code (e.g. "en_US"). */
    private final Map<String, YamlConfiguration> languageConfigs = new LinkedHashMap<>();

    /** Per-locale message caches. */
    private final Map<String, Map<String, String>> messageCaches = new HashMap<>();

    /** Per-locale search alias caches. */
    private final Map<String, Map<String, String>> aliasesByLocale = new HashMap<>();

    private DecimalFormat currencyFormatter;
    private String currentLanguage; // global default from config.yml

    private PlayerSettingsHook playerSettingsHook;

    public MessageManager(Market plugin) {
        this.plugin = plugin;
        reload();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════

    public void reload() {
        languageConfigs.clear();
        messageCaches.clear();
        aliasesByLocale.clear();

        // Global default language
        currentLanguage = plugin.getConfig().getString("language", "en_US");

        // Ensure default files exist on disk
        saveDefaultLanguageFiles();

        // Load EVERY .yml from the lang/ folder (except alias files)
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (langFolder.isDirectory()) {
            File[] files = langFolder.listFiles((dir, name) ->
                    name.endsWith(".yml") && !name.startsWith("aliases_"));
            if (files != null) {
                for (File f : files) {
                    String locale = f.getName().replace(".yml", "");
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

                    // Merge jar defaults for en_US so missing keys are never null
                    if (locale.equals("en_US")) {
                        InputStream defaultStream = plugin.getResource("lang/en_US.yml");
                        if (defaultStream != null) {
                            cfg.setDefaults(YamlConfiguration.loadConfiguration(
                                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
                        }
                    }
                    languageConfigs.put(locale, cfg);
                    messageCaches.put(locale, new HashMap<>());
                }
            }
        }

        // Guarantee the global default is loaded (fall back to en_US)
        if (!languageConfigs.containsKey(currentLanguage)) {
            plugin.getLogger().warning("Language file not found: " + currentLanguage + ".yml, falling back to en_US");
            currentLanguage = "en_US";
        }

        // Load search alias files
        loadAliases(langFolder);

        // Currency formatter
        String format = plugin.getConfigManager().getCurrencyFormat();
        try {
            currencyFormatter = new DecimalFormat(format.replace("$", ""));
        } catch (Exception e) {
            currencyFormatter = new DecimalFormat("#,##0.00");
        }

        plugin.getLogger().info("Loaded " + languageConfigs.size() + " language(s): "
                + String.join(", ", languageConfigs.keySet())
                + " (default: " + currentLanguage + ")");
    }

    private void saveDefaultLanguageFiles() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        String[] defaults = {"en_US.yml", "pt_PT.yml", "fr_FR.yml",
                "aliases_en_US.yml", "aliases_pt_PT.yml", "aliases_fr_FR.yml"};
        for (String name : defaults) {
            File file = new File(langFolder, name);
            if (!file.exists()) {
                plugin.saveResource("lang/" + name, false);
            }
        }
    }

    private void loadAliases(File langFolder) {
        for (String locale : languageConfigs.keySet()) {
            File aliasFile = new File(langFolder, "aliases_" + locale + ".yml");
            Map<String, String> aliases = new HashMap<>();
            if (aliasFile.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(aliasFile);
                for (String key : cfg.getKeys(false)) {
                    String value = cfg.getString(key, "");
                    if (!value.isEmpty()) {
                        aliases.put(key.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
                    }
                }
            }
            // Also load inline search-aliases from the main lang file (backwards compat)
            YamlConfiguration langCfg = languageConfigs.get(locale);
            if (langCfg != null) {
                ConfigurationSection section = langCfg.getConfigurationSection("servershop.search-aliases");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        String value = section.getString(key, "");
                        if (!value.isEmpty()) {
                            aliases.putIfAbsent(key.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
            aliasesByLocale.put(locale, aliases);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Player language resolution
    // ═══════════════════════════════════════════════════════════════

    public void setPlayerSettingsHook(PlayerSettingsHook hook) {
        this.playerSettingsHook = hook;
    }

    /**
     * Resolves the language for a specific player:
     * 1. PlayerSettings API (if available and the returned locale is loaded)
     * 2. Global config default
     */
    public String getPlayerLanguage(Player player) {
        if (player != null && playerSettingsHook != null && playerSettingsHook.isAvailable()) {
            String lang = playerSettingsHook.getLanguage(player);
            if (lang != null && languageConfigs.containsKey(lang)) {
                return lang;
            }
        }
        return currentLanguage;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Message retrieval — locale-aware overloads
    // ═══════════════════════════════════════════════════════════════

    // ── getRaw ─────────────────────────────────────────────────

    /** Gets raw string for the global default language. */
    public String getRaw(String path) {
        return getRaw(currentLanguage, path);
    }

    /** Gets raw string for a specific locale. */
    public String getRaw(String locale, String path) {
        Map<String, String> cache = messageCaches.getOrDefault(locale, messageCaches.get(currentLanguage));
        if (cache != null && cache.containsKey(path)) {
            return cache.get(path);
        }
        YamlConfiguration cfg = languageConfigs.getOrDefault(locale, languageConfigs.get(currentLanguage));
        if (cfg == null) return "&cMissing message: " + path;
        String message = cfg.getString(path);
        // Fallback to default language if key missing in this locale
        if (message == null && !locale.equals(currentLanguage)) {
            YamlConfiguration defCfg = languageConfigs.get(currentLanguage);
            if (defCfg != null) message = defCfg.getString(path);
        }
        if (message == null) {
            // Ultimate fallback to en_US
            YamlConfiguration enCfg = languageConfigs.get("en_US");
            if (enCfg != null) message = enCfg.getString(path);
        }
        if (message == null) message = "&cMissing message: " + path;
        if (cache != null) cache.put(path, message);
        return message;
    }

    /** Gets raw string for a player's locale. */
    public String getRawP(Player player, String path) {
        return getRaw(getPlayerLanguage(player), path);
    }

    // ── get (Component) ────────────────────────────────────────

    public Component get(String path) {
        return TextUtil.colorize(getRaw(path));
    }

    public Component get(String locale, String path) {
        return TextUtil.colorize(getRaw(locale, path));
    }

    public Component getP(Player player, String path) {
        return TextUtil.colorize(getRawP(player, path));
    }

    // ── get with placeholders ──────────────────────────────────

    public Component get(String path, Map<String, String> placeholders) {
        return get(currentLanguage, path, placeholders);
    }

    public Component get(String locale, String path, Map<String, String> placeholders) {
        String message = getRaw(locale, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return TextUtil.colorize(message);
    }

    public Component getP(Player player, String path, Map<String, String> placeholders) {
        return get(getPlayerLanguage(player), path, placeholders);
    }

    public Component get(String path, String placeholder, String value) {
        return get(currentLanguage, path, placeholder, value);
    }

    public Component get(String locale, String path, String placeholder, String value) {
        String message = getRaw(locale, path).replace("{" + placeholder + "}", value);
        return TextUtil.colorize(message);
    }

    public Component getP(Player player, String path, String placeholder, String value) {
        return get(getPlayerLanguage(player), path, placeholder, value);
    }

    // ── getPrefixed ────────────────────────────────────────────

    public Component getPrefixed(String path) {
        return getPrefixed(currentLanguage, path);
    }

    public Component getPrefixed(String locale, String path) {
        return TextUtil.colorize(getRaw(locale, "prefix") + getRaw(locale, path));
    }

    public Component getPrefixedP(Player player, String path) {
        return getPrefixed(getPlayerLanguage(player), path);
    }

    public Component getPrefixed(String path, Map<String, String> placeholders) {
        return getPrefixed(currentLanguage, path, placeholders);
    }

    public Component getPrefixed(String locale, String path, Map<String, String> placeholders) {
        String message = getRaw(locale, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return TextUtil.colorize(getRaw(locale, "prefix") + message);
    }

    public Component getPrefixedP(Player player, String path, Map<String, String> placeholders) {
        return getPrefixed(getPlayerLanguage(player), path, placeholders);
    }

    public Component getPrefixed(String path, String placeholder, String value) {
        return getPrefixed(currentLanguage, path, placeholder, value);
    }

    public Component getPrefixed(String locale, String path, String placeholder, String value) {
        String message = getRaw(locale, path).replace("{" + placeholder + "}", value);
        return TextUtil.colorize(getRaw(locale, "prefix") + message);
    }

    public Component getPrefixedP(Player player, String path, String placeholder, String value) {
        return getPrefixed(getPlayerLanguage(player), path, placeholder, value);
    }

    // ── Lists ──────────────────────────────────────────────────

    public List<String> getList(String path) {
        return getList(currentLanguage, path);
    }

    public List<String> getList(String locale, String path) {
        YamlConfiguration cfg = languageConfigs.getOrDefault(locale, languageConfigs.get(currentLanguage));
        if (cfg == null) return List.of();
        List<String> list = cfg.getStringList(path);
        if (list.isEmpty() && !locale.equals(currentLanguage)) {
            YamlConfiguration defCfg = languageConfigs.get(currentLanguage);
            if (defCfg != null) list = defCfg.getStringList(path);
        }
        if (list.isEmpty()) {
            YamlConfiguration enCfg = languageConfigs.get("en_US");
            if (enCfg != null) list = enCfg.getStringList(path);
        }
        return list;
    }

    public List<String> getListP(Player player, String path) {
        return getList(getPlayerLanguage(player), path);
    }

    public List<Component> getComponentList(String path) {
        return getList(path).stream().map(TextUtil::colorize).toList();
    }

    public List<Component> getComponentList(String locale, String path) {
        return getList(locale, path).stream().map(TextUtil::colorize).toList();
    }

    public List<Component> getComponentListP(Player player, String path) {
        return getComponentList(getPlayerLanguage(player), path);
    }

    // ── Buttons & Lore (shortcuts) ─────────────────────────────

    public String getButton(String buttonKey) {
        return getRaw("buttons." + buttonKey);
    }

    public String getButton(String locale, String buttonKey) {
        return getRaw(locale, "buttons." + buttonKey);
    }

    public String getButtonP(Player player, String buttonKey) {
        return getRaw(getPlayerLanguage(player), "buttons." + buttonKey);
    }

    public List<String> getLore(String loreKey) {
        return getList("lore." + loreKey);
    }

    public List<String> getLore(String locale, String loreKey) {
        return getList(locale, "lore." + loreKey);
    }

    public List<String> getLoreP(Player player, String loreKey) {
        return getList(getPlayerLanguage(player), "lore." + loreKey);
    }

    public List<String> getLore(String loreKey, Map<String, String> placeholders) {
        return getLore(currentLanguage, loreKey, placeholders);
    }

    public List<String> getLore(String locale, String loreKey, Map<String, String> placeholders) {
        return getList(locale, "lore." + loreKey).stream()
                .map(line -> {
                    String result = line;
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                    }
                    return result;
                }).toList();
    }

    public List<String> getLoreP(Player player, String loreKey, Map<String, String> placeholders) {
        return getLore(getPlayerLanguage(player), loreKey, placeholders);
    }

    // ── Filters & Sort ─────────────────────────────────────────

    public String getFilter(String filterKey) {
        return getRaw("filters." + filterKey);
    }

    public String getFilter(String locale, String filterKey) {
        return getRaw(locale, "filters." + filterKey);
    }

    public String getSort(String sortKey) {
        return getRaw("sort." + sortKey);
    }

    public String getSort(String locale, String sortKey) {
        return getRaw(locale, "sort." + sortKey);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Currency
    // ═══════════════════════════════════════════════════════════════

    public String formatCurrency(double amount) {
        String symbol = plugin.getConfigManager().getCurrencySymbol();
        return symbol + currencyFormatter.format(amount);
    }

    public String formatCurrencyCents(long cents) {
        return formatCurrency(cents / 100.0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search aliases
    // ═══════════════════════════════════════════════════════════════

    /** Gets search aliases for the default language (backwards-compatible). */
    public Map<String, String> getSearchAliases() {
        return getSearchAliases(currentLanguage);
    }

    /** Gets search aliases for a specific locale. */
    public Map<String, String> getSearchAliases(String locale) {
        return aliasesByLocale.getOrDefault(locale, Collections.emptyMap());
    }

    /** Gets search aliases for a player's locale. */
    public Map<String, String> getSearchAliasesP(Player player) {
        return getSearchAliases(getPlayerLanguage(player));
    }
}
