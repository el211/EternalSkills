package com.afelia.eskills;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Eskills extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<String, SkillData> skills = new HashMap<>();
    private Map<UUID, Long> cooldowns = new HashMap<>();
    private File tagDataFile;
    private FileConfiguration tagDataConfig;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        config = getConfig();
        tagDataFile = new File(getDataFolder(), "tagdata.yml");
        if (!tagDataFile.exists()) {
            saveResource("tagdata.yml", false);
        }
        tagDataConfig = YamlConfiguration.loadConfiguration(tagDataFile);
        loadSkills();
    }

    private void loadSkills() {
        ConfigurationSection skillsSection = config.getConfigurationSection("skills");
        if (skillsSection != null) {
            for (String skillKey : skillsSection.getKeys(false)) {
                ConfigurationSection skillConfig = skillsSection.getConfigurationSection(skillKey);
                if (skillConfig != null) {
                    String triggerActionString = skillConfig.getString("trigger-action");
                    SkillAction triggerAction = SkillAction.valueOf(triggerActionString.toUpperCase());
                    String skillToExecute = skillConfig.getString("skill-to-execute");
                    long cooldownDuration = skillConfig.getLong("cooldown-duration");
                    String condition = skillConfig.getString("condition");
                    String tag = skillConfig.getString("tag");
                    skills.put(skillKey, new SkillData(triggerAction, skillToExecute, cooldownDuration, condition, tag));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        for (SkillData skill : skills.values()) {
            SkillAction eventAction = convertToSkillAction(event.getAction(), player); // Pass the Player object
            if (eventAction == skill.getTriggerAction()) {
                getLogger().info("Skill triggered: " + skill); // Add debug message
                if (player.hasPermission("eskills.trigger")) {
                    if (checkCooldown(player, skill)) {
                        getLogger().info("Cooldown check passed."); // Add debug message
                        if (checkTag(player, skill)) {
                            getLogger().info("Tag check passed."); // Add debug message
                            executeSkill(player, skill);
                            startCooldown(player, skill);
                        } else {
                            player.sendMessage("You don't have the required tag!");
                        }
                    } else {
                        player.sendMessage("Skill is on cooldown!");
                    }
                } else {
                    player.sendMessage("You don't have permission to use this skill!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().split(" ");
        Player player = event.getPlayer();
        if (args.length >= 4 && args[0].equalsIgnoreCase("/eskills") && args[1].equalsIgnoreCase("tag")) {
            if (!player.hasPermission("eskills.tag.manage")) {
                player.sendMessage("You don't have permission to manage tags!");
                return;
            }

            String subCommand = args[2].toLowerCase();
            String tagName = args[3].toLowerCase();
            switch (subCommand) {
                case "add":
                    if (player.hasPermission("eskills.tag.add")) {
                        addTag(player, tagName);
                    } else {
                        player.sendMessage("You don't have permission to add tags!");
                    }
                    break;
                case "remove":
                    if (player.hasPermission("eskills.tag.remove")) {
                        removeTag(player, tagName);
                    } else {
                        player.sendMessage("You don't have permission to remove tags!");
                    }
                    break;
                case "clear":
                    if (player.hasPermission("eskills.tag.clear")) {
                        clearTags(player);
                    } else {
                        player.sendMessage("You don't have permission to clear tags!");
                    }
                    break;
                default:
                    // Handle unknown sub-command
                    break;
            }
        }
    }

    private void addTag(Player player, String tagName) {
        tagDataConfig.set(player.getUniqueId().toString() + "." + tagName, true);
        saveTagData();
        player.sendMessage("Tag '" + tagName + "' added successfully!");
    }

    private void removeTag(Player player, String tagName) {
        tagDataConfig.set(player.getUniqueId().toString() + "." + tagName, null);
        saveTagData();
        player.sendMessage("Tag '" + tagName + "' removed successfully!");
    }

    private void clearTags(Player player) {
        tagDataConfig.set(player.getUniqueId().toString(), null);
        saveTagData();
        player.sendMessage("All tags cleared successfully!");
    }

    private boolean checkTag(Player player, SkillData skill) {
        String tag = skill.getTag();
        if (tag == null || tag.isEmpty()) {
            return true; // No tag required
        }

        String playerUUID = player.getUniqueId().toString();
        if (!tagDataConfig.isSet(playerUUID)) {
            getLogger().warning("Tag data for player " + playerUUID + " not found in tagdata.yml");
            return false; // Player tag data not found
        }

        boolean hasTag = tagDataConfig.getBoolean(playerUUID + "." + tag, false);
        getLogger().info("Player " + player.getName() + " has tag '" + tag + "': " + hasTag);

        return hasTag;
    }


    private void saveTagData() {
        try {
            tagDataConfig.save(tagDataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save tag data file: " + e.getMessage());
        }
    }



    private SkillAction convertToSkillAction(Action action, Player player) {
        if (player.isSneaking()) {
            if (action == Action.LEFT_CLICK_AIR) {
                return SkillAction.SHIFT_LEFT_CLICK_AIR;
            } else if (action == Action.RIGHT_CLICK_AIR) {
                return SkillAction.SHIFT_RIGHT_CLICK_AIR;
            } else if (action == Action.LEFT_CLICK_BLOCK) {
                return SkillAction.SHIFT_LEFT_CLICK_BLOCK;
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                return SkillAction.SHIFT_RIGHT_CLICK_BLOCK;
            }
        } else {
            if (!player.getLocation().getBlock().getType().isSolid()) {
                if (action == Action.LEFT_CLICK_AIR) { //Player in air
                    return SkillAction.JUMP_LEFT_CLICK;
                }

            }
            switch (action) {
                case RIGHT_CLICK_AIR:
                    return SkillAction.RIGHT_CLICK_AIR;
                case LEFT_CLICK_AIR:
                    return SkillAction.LEFT_CLICK_AIR;
                case RIGHT_CLICK_BLOCK:
                    return SkillAction.RIGHT_CLICK_BLOCK;
                case LEFT_CLICK_BLOCK:
                    return SkillAction.LEFT_CLICK_BLOCK;
                case PHYSICAL: // Handles sneaking and jumping actions

                    return null; // Unknown action
                default:
                    return null; // Unknown action
            }

        }
        return null; // Unknown action
    }


    // Method to convert org.bukkit.event.block.Action to SkillAction

//        switch (action) {
//            case RIGHT_CLICK_AIR:
//                return SkillAction.RIGHT_CLICK_AIR;
//            case LEFT_CLICK_AIR:
//                return SkillAction.LEFT_CLICK_AIR;
//            case RIGHT_CLICK_BLOCK:
//                return SkillAction.RIGHT_CLICK_BLOCK;
//            case LEFT_CLICK_BLOCK:
//                return SkillAction.LEFT_CLICK_BLOCK;
//            case PHYSICAL: // Handles sneaking and jumping actions
//                // Check if the player is sneaking or jumping
////                if (player.isSneaking()) {
////                    if (action == Action.LEFT_CLICK_AIR) {
////                        return SkillAction.SHIFT_LEFT_CLICK_AIR;
////                    } else if (action == Action.RIGHT_CLICK_AIR) {
////                        return SkillAction.SHIFT_RIGHT_CLICK_AIR;
////                    } else if (action == Action.LEFT_CLICK_BLOCK) {
////                        return SkillAction.SHIFT_LEFT_CLICK_BLOCK;
////                    } else if (action == Action.RIGHT_CLICK_BLOCK) {
////                        return SkillAction.SHIFT_RIGHT_CLICK_BLOCK;
////                    }
////                } else {
////                    if (action == Action.LEFT_CLICK_AIR) {
////                        return SkillAction.JUMP_LEFT_CLICK;
////                    }
////                }
//                return null; // Unknown action
//            default:
//                return null; // Unknown action
//        }


    private boolean checkCooldown(Player player, SkillData skill) {
        long lastExecuted = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldown = skill.getCooldownDuration() * 1000; // Cooldown duration in milliseconds
        long tot = currentTime - lastExecuted;
        return tot >= cooldown;
    }

    private void executeSkill(Player player, SkillData skill) {
        // Use MythicMobs command to execute the specified skill
        String skillToExecute = skill.getSkillToExecute();

        String addPermission = "";
        String removePermission = "";

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), new String("lp user %player% permission set %permission% true")
                        .replace("%permission%", addPermission)
                .replace("%player%", player.getName()));

        // Execute the skill using the correct MythicMobs command
        String command = "mm test cast -s " + skillToExecute;
        player.performCommand(command);


        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), new String("lp user %player% permission set %permission% true")
                .replace("%permission%", removePermission)
                .replace("%player%", player.getName()));
        // Suppress the MythicMobs message in chat
        player.sendMessage(""); // Send an empty message

        // Update cooldown
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void startCooldown(Player player, SkillData skill) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + skill.getCooldownDuration() * 1000); // Add cooldown duration
    }

    private static class SkillData {
        private SkillAction triggerAction;
        private String skillToExecute;
        private long cooldownDuration;
        private String condition;
        private String tag;

        public SkillData(SkillAction triggerAction, String skillToExecute, long cooldownDuration, String condition, String tag) {
            this.triggerAction = triggerAction;
            this.skillToExecute = skillToExecute;
            this.cooldownDuration = cooldownDuration;
            this.condition = condition;
            this.tag = tag;
        }

        // Getters for accessing properties
        public SkillAction getTriggerAction() {
            return triggerAction;
        }

        public String getSkillToExecute() {
            return skillToExecute;
        }

        public long getCooldownDuration() {
            return cooldownDuration;
        }

        public String getCondition() {
            return condition;
        }

        public String getTag() {
            return tag;
        }

        // Override toString() to provide custom string representation
        @Override
        public String toString() {
            return "SkillData{" +
                    "triggerAction=" + triggerAction +
                    ", skillToExecute='" + skillToExecute + '\'' +
                    ", cooldownDuration=" + cooldownDuration +
                    ", condition='" + condition + '\'' +
                    ", tag='" + tag + '\'' +
                    '}';
        }
    }
}
