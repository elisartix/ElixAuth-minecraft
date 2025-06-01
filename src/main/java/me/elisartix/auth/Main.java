package me.elisartix.auth;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private final Set<UUID> loggedIn = new HashSet<>();
    private Connection conn;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        setupDatabase();
        getLogger().info("Auth запущен");
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdir();
            conn = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/auth.db");
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, password TEXT)");
            stmt.close();
            getLogger().info("База данных подключена.");
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Ошибка при подключении к базе данных!");
        }
    }

    private void registerToDB(UUID id, String password) {
        try {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (uuid, password) VALUES (?, ?)");
            ps.setString(1, id.toString());
            ps.setString(2, password);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isRegistered(UUID id) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE uuid = ?");
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean checkPasswordFromDB(UUID id, String password) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE uuid = ?");
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            boolean result = rs.next() && rs.getString("password").equals(password);
            rs.close();
            ps.close();
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        UUID id = p.getUniqueId();

        switch (cmd.getName().toLowerCase()) {
            case "register":
                if (isRegistered(id)) {
                    p.sendMessage("§cТы уже зарегистрирован.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§7Используй: §e/reg <пароль> <повтор>");
                    return true;
                }
                if (!args[0].equals(args[1])) {
                    p.sendMessage("§cПароли не совпадают.");
                    return true;
                }
                registerToDB(id, args[0]);
                loggedIn.add(id);
                p.sendMessage("§aРегистрация успешна.");
                break;

            case "login":
                if (!isRegistered(id)) {
                    p.sendMessage("§7Сначала зарегистрируйтесь: §e/reg <пароль> <повтор>");
                    return true;
                }
                if (args.length < 1) {
                    p.sendMessage("§7Используй: §e/l <пароль>");
                    return true;
                }
                if (!checkPasswordFromDB(id, args[0])) {
                    p.sendMessage("§cНеверный пароль.");
                    return true;
                }
                loggedIn.add(id);
                p.sendMessage("§aВход выполнен.");
                break;

            case "logout":
                if (!loggedIn.contains(id)) {
                    p.sendMessage("§eТы уже не в системе.");
                    return true;
                }
                loggedIn.remove(id);
                p.sendMessage("§cВы вышли из аккаунта. Используйте /l <пароль> для входа.");
                break;
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        loggedIn.remove(id);

        if (!isRegistered(id)) {
            e.getPlayer().sendMessage("§7Сначала зарегистрируйтесь: §e/reg <пароль> <повтор>");
        } else {
            e.getPlayer().sendMessage("§7Войдите: §e/l <пароль>");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.getPlayer().sendMessage("§cТы не вошёл.");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cНельзя выбрасывать предметы без входа.");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) {
            String msg = e.getMessage().toLowerCase();
            if (!(msg.startsWith("/login") || msg.startsWith("/l") ||
                    msg.startsWith("/register") || msg.startsWith("/reg") || msg.startsWith("/r"))) {
                e.setCancelled(true);
                p.sendMessage("§cТы не вошёл. Сначала используй /l <пароль>");
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player) {
            Player p = (Player) e.getPlayer();
            if (!loggedIn.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage("§cТы не вошёл. Инвентарь недоступен.");
            }
        }
    }
}
