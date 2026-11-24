package com.xyron.teamglow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private static final String[] WORDS = {"Wolves", "Dragons", "Hawks", "Titans", "Vipers",
            "Ghosts", "Raiders", "Storm", "Phoenix", "Shadows", "Tigers", "Knights"};
    private final TeamGlow plugin;
    private final Random rng = new Random();

    public TeamCommand(TeamGlow plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.GOLD + "[Team] " + ChatColor.RESET + m); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 0) { help(sender); return true; }
        String sub = a[0].toLowerCase();

        if (sub.equals("list")) { list(sender); return true; }
        if (!sender.hasPermission("teamglow.admin")) { msg(sender, ChatColor.RED + "No permission."); return true; }

        switch (sub) {
            case "create" -> {
                if (a.length < 4) { msg(sender, "Usage: /team create <name|random> <color|auto> <players/@a[name=!Me]>"); return true; }
                String name = a[1].equalsIgnoreCase("random") ? randomName() : a[1].replaceAll("[^A-Za-z0-9_\\-]", "");
                if (name.isEmpty() || name.length() > 12) { msg(sender, ChatColor.RED + "Name must be 1-12 letters/numbers."); return true; }
                if (plugin.teamExists(name)) { msg(sender, ChatColor.RED + "Team already exists."); return true; }
                ChatColor color = parseColor(a[2]);
                if (color == null) { msg(sender, ChatColor.RED + "Bad color. Use auto, red, blue, green, yellow, aqua, light_purple, gold..."); return true; }
                Set<Player> targets = targets(sender, a, 3);
                plugin.createTeam(name, color);
                targets.forEach(p -> plugin.addPlayer(p, name));
                msg(sender, "Created " + color + name + ChatColor.RESET + " with " + targets.size() + " player(s).");
            }
            case "add" -> {
                if (a.length < 3 || !plugin.teamExists(a[1])) { msg(sender, "Usage: /team add <team> <players>"); return true; }
                Set<Player> targets = targets(sender, a, 2);
                targets.forEach(p -> plugin.addPlayer(p, a[1]));
                msg(sender, "Added " + targets.size() + " player(s) to " + a[1].toLowerCase());
            }
            case "remove" -> {
                Set<Player> targets = targets(sender, a, 1);
                targets.forEach(plugin::removePlayer);
                msg(sender, "Removed " + targets.size() + " player(s) from their teams.");
            }
            case "delete" -> {
                if (a.length < 2 || !plugin.teamExists(a[1])) { msg(sender, "Usage: /team delete <team>"); return true; }
                plugin.deleteTeam(a[1]);
                msg(sender, "Deleted team " + a[1].toLowerCase());
            }
            case "clear" -> {
                new ArrayList<>(plugin.teamNames()).forEach(plugin::deleteTeam);
                msg(sender, "All teams deleted.");
            }
            case "fight" -> {
                if (a.length < 2) { msg(sender, "Teammate fighting is " + (plugin.isFight() ? "ON" : "OFF")); return true; }
                plugin.setFight(a[1].equalsIgnoreCase("on"));
                msg(sender, "Teammates can hit each other: " + (plugin.isFight() ? "ON" : "OFF"));
            }
            case "glow" -> {
                if (a.length < 2) { msg(sender, "Glow is " + (plugin.isGlow() ? "ON" : "OFF")); return true; }
                plugin.setGlow(a[1].equalsIgnoreCase("on"));
                msg(sender, "Team glow: " + (plugin.isGlow() ? "ON" : "OFF"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        boolean admin = sender.hasPermission("teamglow.admin");
        String own = (sender instanceof Player p) ? plugin.teamOf(p) : null;
        if (!admin && own == null) { msg(sender, "You are not in a team."); return; }
        for (String team : plugin.teamNames()) {
            if (!admin && !team.equalsIgnoreCase(own)) continue; // players only see their own team
            StringJoiner j = new StringJoiner(", ");
            for (UUID id : plugin.members(team)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                j.add(op.getName() == null ? id.toString() : op.getName());
            }
            sender.sendMessage(plugin.colorOf(team) + team + ChatColor.GRAY + ": " + ChatColor.WHITE + j);
        }
    }

    private Set<Player> targets(CommandSender s, String[] a, int from) {
        Set<Player> out = new LinkedHashSet<>();
        for (int i = from; i < a.length; i++) {
            if (a[i].startsWith("@")) {
                try {
                    for (Entity e : Bukkit.selectEntities(s, a[i])) if (e instanceof Player p) out.add(p);
                } catch (IllegalArgumentException ex) { msg(s, ChatColor.RED + "Bad selector: " + a[i]); }
            } else {
                Player p = Bukkit.getPlayerExact(a[i]);
                if (p != null) out.add(p); else msg(s, ChatColor.RED + "Player not online: " + a[i]);
            }
        }
        return out;
    }

    private ChatColor parseColor(String s) {
        if (s.equalsIgnoreCase("auto")) return plugin.nextFreeColor();
        try {
            ChatColor c = ChatColor.valueOf(s.toUpperCase());
            return c.isColor() ? c : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    private String randomName() {
        String n;
        do { n = WORDS[rng.nextInt(WORDS.length)] + rng.nextInt(100); } while (plugin.teamExists(n));
        return n;
    }

    private void help(CommandSender s) {
        msg(s, "/team create <name|random> <color|auto> <targets>   e.g. @a[name=!Xyron]");
        msg(s, "/team add <team> <targets> | /team remove <targets> | /team delete <team> | /team clear");
        msg(s, "/team fight <on|off>  (can teammates hit each other)");
        msg(s, "/team glow <on|off> | /team list");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("create", "add", "remove", "delete", "clear", "fight", "glow", "list"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("fight") || a[0].equalsIgnoreCase("glow"))) out.addAll(List.of("on", "off"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("delete"))) out.addAll(plugin.teamNames());
        else if (a.length == 2 && a[0].equalsIgnoreCase("create")) out.add("random");
        else if (a.length == 3 && a[0].equalsIgnoreCase("create")) { out.add("auto"); TeamGlow.PALETTE.forEach(x -> out.add(x.name().toLowerCase())); }
        else { out.add("@a"); Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
