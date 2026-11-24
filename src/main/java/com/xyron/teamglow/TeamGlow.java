package com.xyron.teamglow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamGlow extends JavaPlugin implements Listener {

    public static final String PREFIX = "tg_";
    public static final List<ChatColor> PALETTE = List.of(
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_PURPLE,
            ChatColor.WHITE, ChatColor.DARK_GREEN);

    private final Map<String, ChatColor> teamColors = new LinkedHashMap<>();
    private final Map<UUID, String> memberTeam = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityTeam = new ConcurrentHashMap<>();
    private boolean fight = false; // false = teammates can NOT hit each other
    private boolean glow = true;

    @Override
    public void onEnable() {
        var cmd = new TeamCommand(this);
        getCommand("team").setExecutor(cmd);
        getCommand("team").setTabCompleter(cmd);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Glow flag is only kept for teammates; everyone else gets it stripped.
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
                    @Override
                    public void onPacketSending(PacketEvent e) {
                        if (e.isPlayerTemporary()) return;
                        int id = e.getPacket().getIntegers().read(0);
                        String team = entityTeam.get(id);
                        if (team == null) return;
                        boolean visible = glow && team.equals(memberTeam.get(e.getPlayer().getUniqueId()));
                        PacketContainer packet = e.getPacket().deepClone();
                        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
                        for (WrappedDataValue v : values) {
                            if (v.getIndex() == 0 && v.getValue() instanceof Byte b) {
                                v.setValue(visible ? (byte) (b | 0x40) : (byte) (b & ~0x40));
                            }
                        }
                        packet.getDataValueCollectionModifier().write(0, values);
                        e.setPacket(packet);
                    }
                });
    }

    @Override
    public void onDisable() {
        for (String name : new ArrayList<>(teamColors.keySet())) deleteTeam(name);
    }

    // ---------- API used by the command ----------

    public boolean teamExists(String name) { return teamColors.containsKey(name.toLowerCase()); }
    public Set<String> teamNames() { return teamColors.keySet(); }
    public ChatColor colorOf(String name) { return teamColors.get(name.toLowerCase()); }
    public String teamOf(Player p) { return memberTeam.get(p.getUniqueId()); }
    public boolean isFight() { return fight; }
    public boolean isGlow() { return glow; }

    public ChatColor nextFreeColor() {
        for (ChatColor c : PALETTE) if (!teamColors.containsValue(c)) return c;
        return PALETTE.get(teamColors.size() % PALETTE.size());
    }

    public void createTeam(String name, ChatColor color) {
        name = name.toLowerCase();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team old = sb.getTeam(PREFIX + name);
        if (old != null) old.unregister();
        Team t = sb.registerNewTeam(PREFIX + name);
        t.setColor(color);
        t.setAllowFriendlyFire(fight);
        t.setCanSeeFriendlyInvisibles(true);
        teamColors.put(name, color);
    }

    public void addPlayer(Player p, String team) {
        team = team.toLowerCase();
        removePlayer(p);
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
        if (t == null) return;
        t.addEntry(p.getName());
        memberTeam.put(p.getUniqueId(), team);
        entityTeam.put(p.getEntityId(), team);
        p.setGlowing(true);
        refresh(p);
    }

    public void removePlayer(Player p) {
        String team = memberTeam.remove(p.getUniqueId());
        entityTeam.remove(p.getEntityId());
        if (team == null) return;
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
        if (t != null) t.removeEntry(p.getName());
        p.setGlowing(false);
        refresh(p);
    }

    public void deleteTeam(String name) {
        name = name.toLowerCase();
        for (UUID id : members(name)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removePlayer(p); else memberTeam.remove(id);
        }
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + name);
        if (t != null) t.unregister();
        teamColors.remove(name);
    }

    public List<UUID> members(String team) {
        List<UUID> list = new ArrayList<>();
        memberTeam.forEach((u, t) -> { if (t.equalsIgnoreCase(team)) list.add(u); });
        return list;
    }

    public void setFight(boolean on) {
        fight = on;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String n : teamColors.keySet()) {
            Team t = sb.getTeam(PREFIX + n);
            if (t != null) t.setAllowFriendlyFire(on);
        }
    }

    public void setGlow(boolean on) {
        glow = on;
        for (UUID id : memberTeam.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) { p.setGlowing(on); refresh(p); }
        }
    }

    private void refresh(Player p) {
        ProtocolLibrary.getProtocolManager().updateEntity(p, new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    // ---------- events ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String team = memberTeam.get(e.getPlayer().getUniqueId());
        if (team == null) return;
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
        if (t != null) t.addEntry(e.getPlayer().getName());
        entityTeam.put(e.getPlayer().getEntityId(), team);
        e.getPlayer().setGlowing(glow);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        entityTeam.remove(e.getPlayer().getEntityId());
    }
}
