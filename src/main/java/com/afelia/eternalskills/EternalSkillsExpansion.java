package com.afelia.eternalskills;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;

public class EternalSkillsExpansion extends PlaceholderExpansion {
    private EternalSkills main;

    public EternalSkillsExpansion(EternalSkills main) {
        this.main = main;
    }

    @Override
    public String getIdentifier() {
        return "eternalskills";
    }

    @Override
    public String getAuthor() {
        return ".matthewe";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }


    @Override
    public String onPlaceholderRequest(Player p, String params) {

        if (params.startsWith("tag_")){

            List<String> tags = main.getTags(p);
            for (int i = 0; i < tags.size(); i++) {

                int index = i +1;
                if (params.equalsIgnoreCase("tag_"+index)){

                    return tags.get(i);
                }
            }
            return "No Tag";
        }


        return params;
    }
}
