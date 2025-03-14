package com.afelia.eternalskills;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.utils.lib.jooq.impl.QOM;
import io.lumine.mythic.core.skills.conditions.all.FactionSameCondition;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EternalSkills extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<String, SkillData> skills = new HashMap<>();
    private Map<UUID, Long> cooldowns = new HashMap<>();
    private File tagDataFile;
    private FileConfiguration tagDataConfig;

    private List<String> tags=new ArrayList<>();
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);



        reload(true);
        new EternalSkillsExpansion(this).register();
        PluginCommand eskills = getCommand("eskills");
        eskills.setTabCompleter(this);
        eskills

                .setExecutor(this);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player p : Bukkit.getServer().getOnlinePlayers()) {
                if (cooldowns.containsKey(p.getUniqueId())) {
                    long lastExecuted = cooldowns.get(p.getUniqueId())-System.currentTimeMillis();
                    if (lastExecuted> 0) {

                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN+"Ability Cooldown: " + ChatColor.WHITE+( lastExecuted/1000)+"s"));
                    } else {
                        cooldowns.remove(p.getUniqueId());
                    }

                }
            }
        },5,5);
    }

    private void reload(boolean start) {
        skills = new HashMap<>();
        cooldowns = new HashMap<>();

        if (!start){

            reloadConfig();
        }

        saveDefaultConfig();
        loadTags();


        config = getConfig();
        tagDataFile = new File(getDataFolder(), "tagdata.yml");
        if (!tagDataFile.exists()) {
            saveResource("tagdata.yml", false);
        }
        tagDataConfig = YamlConfiguration.loadConfiguration(tagDataFile);
        loadSkills();
    }

    private void loadTags() {
        tags=new ArrayList<>();


        File f  = new File(getDataFolder(), "tags.yml");
        if (!f.exists()) {
            saveResource("tags.yml", true);
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
        tags.addAll(yamlConfiguration.getStringList("tags"));


    }

    private void loadSkills() {
        skills=new HashMap<>();

        ConfigurationSection skillsSection = config.getConfigurationSection("skills");
        if (skillsSection != null) {
            for (String skillKey : skillsSection.getKeys(false)) {
                ConfigurationSection skillConfig = skillsSection.getConfigurationSection(skillKey);
                if (skillConfig != null) {
                    List<SkillAction> actions =new ArrayList<>();

                    if (skillConfig.isList("trigger-action")) {
                        for (String s : skillConfig.getStringList("trigger-action")) {
                            SkillAction triggerAction = SkillAction.valueOf(s.toUpperCase());
                            actions.add(triggerAction);

                        }
                    } else {

                        String triggerActionString = skillConfig.getString("trigger-action");
                        SkillAction triggerAction = SkillAction.valueOf(triggerActionString.toUpperCase());
                        actions.add(triggerAction);

                    }
                    String skillToExecute = skillConfig.getString("skill-to-execute");
                    long cooldownDuration = skillConfig.getLong("cooldown-duration");
                    String condition = skillConfig.getString("condition");
                    String tag = skillConfig.getString("tag");
                    skills.put(skillKey, new SkillData(actions, skillToExecute, cooldownDuration, condition, tag));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        for (SkillData skill : skills.values()) {
            SkillAction eventAction = convertToSkillAction(event.getAction(), player); // Pass the Player object
            if (skill.getTriggerAction().contains(eventAction)) {
//                getLogger().info("Skill triggered: " + skill); // Add debug message
                if (player.hasPermission("eskills.trigger")) {
                    if (checkCooldown(player, skill)) {
//                        getLogger().info("Cooldown check passed."); // Add debug message
                        if (checkTag(player, skill)) {
//                            getLogger().info("Tag check passed."); // Add debug message
                            executeSkill(player, skill);
                            startCooldown(player, skill);
                        } else {
//                            player.sendMessage("You don't have the required tag!");
                        }
                    } else {
//                        long lastExecuted = cooldowns.get(player.getUniqueId())-System.currentTimeMillis();
//
//                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN+"Ability Cooldown: " + ChatColor.WHITE+( lastExecuted/1000)+"s"));

                    }
                } else {
                    player.sendMessage("You don't have permission to use this skill!");
                }
            }
        }
    }

    public static List<String> filterStartsWith(List<String> completions, String partialInput) {
        if (partialInput==null||partialInput.isEmpty()){
            return completions;
        }
        List<String> filteredList = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(partialInput.toLowerCase())) {
                filteredList.add(completion);
            }
        }
        return filteredList;
    }
    /*
         s.sendMessage(ChatColor.GREEN+"/eskill tag add "+ChatColor.GRAY+"<tag> <player>");
        s.sendMessage(ChatColor.GREEN+"/eskill tag remove "+ChatColor.GRAY+"<tag> <player>");
        s.sendMessage(ChatColor.GREEN+"/eskill tag clear "+ChatColor.GRAY+"<player>");
        s.sendMessage(ChatColor.GREEN+"/eskill help");
        s.sendMessage(ChatColor.GREEN+"/eskill reload");
     */
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("eskills")) {
            if (args.length==0)return Arrays.asList("reload", "tag", "help");
            if (args.length==1)return filterStartsWith( Arrays.asList("reload", "tag", "help"),args[0].toLowerCase());
            if (args.length==2){
                if (args[0].equalsIgnoreCase("tag")) {
                    return filterStartsWith(  Arrays.asList("clear", "remove", "add"), args[1].toLowerCase());

                }
            }
            if (args.length==3){
                if (args[0].equalsIgnoreCase("tag")) {
                    if (args[1].equalsIgnoreCase("clear")) {
                        return getOnlinePlayersCompletion(args, 2);
                    } else {
                        return filterStartsWith(  tags, args[2].toLowerCase());
                    }
                }
            }
            if (args.length==4){
                if (!args[1].equalsIgnoreCase("clear")) {
                    return getOnlinePlayersCompletion(args, 3);
                }
                return new ArrayList<>();
            }
        }
        return super.onTabComplete(sender, command, alias, args);
    }
    public static List<String> getOnlinePlayersCompletion(String[] args ) {
        return getOnlinePlayersCompletion(args,0);

    }
    public static List<String> getOnlinePlayersCompletion(String[] args, int start  ) {
        if (args.length==start){
            return Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).collect(Collectors.toList());
        }
        return filterStartsWith(Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).collect(Collectors.toList()), args[start]);
    }
    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("eskills"))return false;

        if (!(s instanceof Player)){
            s.sendMessage(ChatColor.RED+"PLAYER ONLY COMMAND.");
            return  false;
        }

        Player player = (Player) s;
        if (args.length==1 && args[0].equalsIgnoreCase("reload")){
            if (s.hasPermission("eskills.tag.reload")) {
                reload(false);
                s.sendMessage(ChatColor.GREEN+"Reloaded");
            } else {
                player.sendMessage(ChatColor.RED+"You don't have permission to reload!");
            }
            return true;
        }
        if (args.length==0){
            if (s.hasPermission("eskills.tag.help")) {
                sendHelp(s);
            } else {
                player.sendMessage(ChatColor.RED+"You don't have permission to reload!");
            }
            return true;
        }
        if (args.length==1 && args[0].equalsIgnoreCase("help")){
            if (s.hasPermission("eskills.tag.help")) {
                sendHelp(s);
            } else {
                player.sendMessage(ChatColor.RED+"You don't have permission to reload!");
            }
            return true;
        }
        if (args.length >= 3&& args[0].equalsIgnoreCase("tag")) {
            if (!player.hasPermission("eskills.tag.manage")) {
                player.sendMessage(ChatColor.RED+"You don't have permission to manage tags!");
                return false;
            }

            String subCommand = args[1].toLowerCase();
            String tagName = args[2].toLowerCase();
            switch (subCommand) {

                case "add":
                    if (player.hasPermission("eskills.tag.add")) {
                        if (!tags.contains(tagName)) {
                            player.sendMessage(ChatColor.RED+"Tag doesnt exist");
                            return false;
                        }
                        addTag(player, tagName);
                    } else {
                        player.sendMessage("You don't have permission to add tags!");
                    }
                    break;
                case "remove":
                    if (player.hasPermission("eskills.tag.remove")) {
                        if (!tags.contains(tagName)) {
                            player.sendMessage(ChatColor.RED+"Tag doesnt exist");
                            return false;
                        }
                        removeTag(player, tagName);
                    } else {
                        player.sendMessage(ChatColor.RED+"You don't have permission to remove tags!");
                    }
                    break;
                case "clear":
                    if (player.hasPermission("eskills.tag.clear")) {
                        clearTags(player);
                    } else {
                        player.sendMessage(ChatColor.RED+"You don't have permission to clear tags!");
                    }
                    break;
                default:
                    // Handle unknown sub-command
                    break;
            }
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ChatColor.GREEN+"/eskill tag add "+ChatColor.GRAY+"<tag> <player>");
        s.sendMessage(ChatColor.GREEN+"/eskill tag remove "+ChatColor.GRAY+"<tag> <player>");
        s.sendMessage(ChatColor.GREEN+"/eskill tag clear "+ChatColor.GRAY+"<player>");
        s.sendMessage(ChatColor.GREEN+"/eskill help");
        s.sendMessage(ChatColor.GREEN+"/eskill reload");
        /*
        r /eskills:
/eskill tag add <tag> <player>
/eskill tag remove <tag> <player>
/eskill tag clear  <player>

         */
    }


    private void addTag(Player player, String tagName) {
        tagDataConfig.set(player.getUniqueId().toString() + "." + tagName, true);
        saveTagData();
        player.sendMessage(ChatColor.GREEN+"Tag '" + tagName + "' added successfully!");
    }

    private void removeTag(Player player, String tagName) {
        tagDataConfig.set(player.getUniqueId().toString() + "." + tagName, null);
        saveTagData();
        player.sendMessage(ChatColor.GREEN+"Tag '" + tagName + "' removed successfully!");
    }

    public List<String> getTags(Player p) {
        List<String> tags=new ArrayList<>();

        for (String tag : this.tags) {
            boolean equipped = tagDataConfig.getBoolean(p.getUniqueId().toString() + "." + tag);
            if (equipped){
                tags.add(tag);

            }

        }

        return tags;
    }
    private void clearTags(Player player) {
        tagDataConfig.set(player.getUniqueId().toString(), null);
        saveTagData();
        tags.clear();
        player.sendMessage(ChatColor.GREEN+"All tags cleared successfully!");
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
//        getLogger().info("Player " + player.getName() + " has tag '" + tag + "': " + hasTag);

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
//            if (!player.getLocation().getBlock().getType().isSolid()) {
//                if (action == Action.LEFT_CLICK_AIR) { //Player in air
//                    return SkillAction.JUMP_LEFT_CLICK;
//                }
//
//            }
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

                    return SkillAction.LEFT_CLICK_AIR;
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
        if (!cooldowns.containsKey(player.getUniqueId())){
            return true;
        }
        if (System.currentTimeMillis()> cooldowns.get(player.getUniqueId())){
            return true;
        }

        return false;
    }
    private void executeSkill(Player player, SkillData skill) {
        // Use MythicMobs command to execute the specified skill
        String skillToExecute = skill.getSkillToExecute();
        BukkitAPIHelper apiHelper = MythicBukkit.inst().getAPIHelper();

        apiHelper.castSkill(player, skillToExecute);
//        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

//        String addPermission = "";
//        String removePermission = "";
//        Optional<Skill> mythicSkill = MythicBukkit.inst().getSkillManager().getSkill(skillToExecute);
//        if (mythicSkill.isPresent()){
//            mythicSkill.get().execute(player);
//        } else {
//            player.sendMessage(ChatColor.RED+"Skill " + skillToExecute+ " doesn't exist!");
//        }
//        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), new String("lp user %player% permission set %permission% true")
//                        .replace("%permission%", addPermission)
//                .replace("%player%", player.getName()));
//
//        // Execute the skill using the correct MythicMobs command
//        String command = "mm test cast -s " + skillToExecute;
//
//        Skills
//        player.performCommand(command);
//
//
//        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), new String("lp user %player% permission set %permission% false")
//                .replace("%permission%", removePermission)
//                .replace("%player%", player.getName()));
//        // Suppress the MythicMobs message in chat
//        player.sendMessage(""); // Send an empty message

        // Update cooldown
    }

    private void startCooldown(Player player, SkillData skill) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (skill.getCooldownDuration() * 1000)); // Add cooldown duration
    }

    private static class SkillData {
        private List<SkillAction> triggerAction;
        private String skillToExecute;
        private long cooldownDuration;
        private String condition;
        private String tag;

        public SkillData(List<SkillAction>  triggerAction, String skillToExecute, long cooldownDuration, String condition, String tag) {
            this.triggerAction = triggerAction;
            this.skillToExecute = skillToExecute;
            this.cooldownDuration = cooldownDuration;
            this.condition = condition;
            this.tag = tag;
        }

        // Getters for accessing properties
        public List<SkillAction>  getTriggerAction() {
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
