package com.shyamstudio.celestcombatXtra.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyamstudio.celestcombatXtra.Scheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Update checker that fetches the latest version from Modrinth and GitHub.
 */
public class UpdateChecker implements Listener {
    private static final String USER_AGENT = "CelestCombat-UpdateChecker/1.0";
    private static final String MODRINTH_SLUG = "celestcombat-xtra";
    private static final String GITHUB_REPO = "vanillaxtra/celestcombatxtra";

    private final JavaPlugin plugin;
    private boolean updateAvailable = false;
    private final String currentVersion;
    private String latestVersion = "";
    private String modrinthUrl = "";
    private String githubUrl = "";

    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_BRIGHT_GREEN = "\u001B[92m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_BRIGHT_BLUE = "\u001B[94m";

    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate) {
                displayConsoleUpdateMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    private void displayConsoleUpdateMessage() {
        String frameColor = CONSOLE_BRIGHT_BLUE;

        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor + CONSOLE_BRIGHT_GREEN +
                "         Celest Combat Update Available" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "Current version: " + CONSOLE_YELLOW + formatConsoleText(currentVersion, 31) + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "Latest version: " + CONSOLE_BRIGHT_GREEN + formatConsoleText(latestVersion, 32) + CONSOLE_RESET);
        plugin.getLogger().info("");
        if (!modrinthUrl.isEmpty()) {
            plugin.getLogger().info(frameColor +
                    CONSOLE_RESET + "Modrinth:" + CONSOLE_RESET);
            plugin.getLogger().info(frameColor + " " +
                    CONSOLE_BRIGHT_GREEN + formatConsoleText(modrinthUrl, 51) + CONSOLE_RESET);
        }
        if (!githubUrl.isEmpty()) {
            plugin.getLogger().info(frameColor +
                    CONSOLE_RESET + "GitHub:" + CONSOLE_RESET);
            plugin.getLogger().info(frameColor + " " +
                    CONSOLE_BRIGHT_GREEN + formatConsoleText(githubUrl, 51) + CONSOLE_RESET);
        }
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
    }

    private String formatConsoleText(String text, int maxLength) {
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text + " ".repeat(maxLength - text.length());
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        modrinthResult = null;
        githubResult = null;

        return CompletableFuture.allOf(
                fetchModrinthVersion(),
                fetchGitHubVersion()
        ).thenApply(ignored -> {
            Version current = new Version(currentVersion);
            Version latest = Version.ZERO;
            String bestVersionLabel = "";

            RemoteVersion modrinth = modrinthResult;
            RemoteVersion github = githubResult;

            if (modrinth != null && modrinth.version.compareTo(latest) > 0) {
                latest = modrinth.version;
                bestVersionLabel = modrinth.versionLabel;
            }
            if (github != null && github.version.compareTo(latest) > 0) {
                latest = github.version;
                bestVersionLabel = github.versionLabel;
            }

            if (modrinth != null) {
                modrinthUrl = modrinth.pageUrl;
            }
            if (github != null) {
                githubUrl = github.pageUrl;
            }

            if (latest.compareTo(current) > 0) {
                latestVersion = bestVersionLabel.isEmpty() ? latest.toString() : bestVersionLabel;
                updateAvailable = true;
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[UpdateChecker] update available: current=" + current
                            + " latest=" + latest + " (" + bestVersionLabel + ")");
                }
                return true;
            }

            updateAvailable = false;
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[UpdateChecker] up to date: current=" + current + " latest=" + latest);
            }
            return false;
        });
    }

    private volatile RemoteVersion modrinthResult;
    private volatile RemoteVersion githubResult;

    private record RemoteVersion(Version version, String versionLabel, String pageUrl) {}

    private CompletableFuture<Void> fetchModrinthVersion() {
        return CompletableFuture.runAsync(() -> {
            try {
                String slug = MODRINTH_SLUG;
                URL url = new URL("https://api.modrinth.com/v2/project/" + slug + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Modrinth update check failed. HTTP " + connection.getResponseCode());
                    return;
                }

                String response = readResponse(connection);
                JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
                if (versions.isEmpty()) {
                    return;
                }

                JsonObject latestRelease = pickNewestByDate(versions, true);
                JsonObject latestAny = pickNewestByDate(versions, false);
                JsonObject chosen = latestRelease != null ? latestRelease : latestAny;
                if (chosen == null) {
                    return;
                }

                String versionNumber = chosen.get("version_number").getAsString();
                String versionId = chosen.get("id").getAsString();
                String pageUrl = "https://modrinth.com/plugin/" + slug + "/version/" + versionId;

                modrinthResult = new RemoteVersion(new Version(versionNumber), versionNumber, pageUrl);
            } catch (Exception e) {
                plugin.getLogger().warning("Modrinth update check error: " + e.getMessage());
            }
        });
    }

    private CompletableFuture<Void> fetchGitHubVersion() {
        return CompletableFuture.runAsync(() -> {
            try {
                String repo = GITHUB_REPO;
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/vnd.github+json");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("GitHub update check failed. HTTP " + connection.getResponseCode());
                    return;
                }

                String response = readResponse(connection);
                JsonObject release = JsonParser.parseString(response).getAsJsonObject();
                if (!release.has("tag_name")) {
                    return;
                }

                String tagName = release.get("tag_name").getAsString();
                String versionLabel = tagName.replaceAll("^v", "");
                String pageUrl = release.has("html_url")
                        ? release.get("html_url").getAsString()
                        : "https://github.com/" + repo + "/releases/latest";

                githubResult = new RemoteVersion(new Version(versionLabel), versionLabel, pageUrl);
            } catch (Exception e) {
                plugin.getLogger().warning("GitHub update check error: " + e.getMessage());
            }
        });
    }

    private JsonObject pickNewestByDate(JsonArray versions, boolean releaseOnly) {
        JsonObject newest = null;
        Version newestVersion = Version.ZERO;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (releaseOnly) {
                String versionType = version.get("version_type").getAsString();
                if (!"release".equals(versionType)) {
                    continue;
                }
            }
            if (!version.has("version_number")) continue;
            Version candidate = new Version(version.get("version_number").getAsString());
            if (newest == null || candidate.compareTo(newestVersion) > 0) {
                newest = version;
                newestVersion = candidate;
            }
        }
        return newest;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !player.hasPermission("celestcombatxtra.update.notify")) {
            return;
        }

        TextColor primaryBlue = TextColor.fromHexString("#3B82F6");
        TextColor green = TextColor.fromHexString("#22C55E");
        TextColor redPink = TextColor.fromHexString("#EF4444");
        TextColor orange = TextColor.fromHexString("#F97316");
        TextColor white = TextColor.fromHexString("#F3F4F6");

        Component borderTop = Component.text("----- CelestCombat Update -----").color(primaryBlue);
        Component borderBottom = Component.text("-----------------------").color(primaryBlue);
        Component updateMsg = Component.text("New update available!").color(green);
        Component versionsComponent = Component.text("Current: ")
                .color(white)
                .append(Component.text(currentVersion).color(redPink))
                .append(Component.text("  Latest: ").color(white))
                .append(Component.text(latestVersion).color(green));

        player.sendMessage(" ");
        player.sendMessage(borderTop);
        player.sendMessage(" ");
        player.sendMessage(updateMsg);
        player.sendMessage(versionsComponent);

        if (!modrinthUrl.isEmpty()) {
            Component modrinthButton = Component.text("[Download on Modrinth]")
                    .color(orange)
                    .clickEvent(ClickEvent.openUrl(modrinthUrl))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Open Modrinth release ").color(white)
                                    .append(Component.text(latestVersion).color(green))
                    ));
            player.sendMessage(modrinthButton);
        }

        if (!githubUrl.isEmpty()) {
            Component githubButton = Component.text("[View on GitHub]")
                    .color(orange)
                    .clickEvent(ClickEvent.openUrl(githubUrl))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Open GitHub release ").color(white)
                                    .append(Component.text(latestVersion).color(green))
                    ));
            player.sendMessage(githubButton);
        }

        player.sendMessage(" ");
        player.sendMessage(borderBottom);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("celestcombatxtra.update.notify")) {
            return;
        }

        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();

        notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

        if (notifiedPlayers.containsKey(playerId) && notifiedPlayers.get(playerId).isEqual(today)) {
            return;
        }

        if (updateAvailable) {
            Scheduler.runTaskLater(() -> {
                sendUpdateNotification(player);
                notifiedPlayers.put(playerId, today);
            }, 40L);
        } else {
            checkForUpdates().thenAccept(hasUpdate -> {
                if (hasUpdate) {
                    Scheduler.runTask(() -> {
                        sendUpdateNotification(player);
                        notifiedPlayers.put(playerId, today);
                    });
                }
            });
        }
    }
}
