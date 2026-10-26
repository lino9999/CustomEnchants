package io.github.lino9999.customenchants.feature;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class PermissionService {
    private PermissionService() {}

    public static void toggle(Player player, String permission) {
        if (!permission.matches("[A-Za-z0-9_.*-]{1,256}"))
            throw new IllegalArgumentException("Invalid permission node.");
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))
                luckPerms(player, permission);
            else vault(player, permission);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalArgumentException(
                    "LuckPerms or a Vault permission provider is required.", error);
        }
    }

    private static void luckPerms(Player player, String permission)
            throws ReflectiveOperationException {
        Class<?> api = Class.forName("net.luckperms.api.LuckPerms");
        Object provider =
                Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
        Object users = api.getMethod("getUserManager").invoke(provider);
        Class<?> userManager = Class.forName("net.luckperms.api.model.user.UserManager");
        Object user =
                userManager.getMethod("getUser", UUID.class).invoke(users, player.getUniqueId());
        if (user == null)
            throw new IllegalArgumentException(
                    "The permission provider has not loaded this player.");
        Class<?> userType = Class.forName("net.luckperms.api.model.user.User");
        Class<?> holderType = Class.forName("net.luckperms.api.model.PermissionHolder");
        Object data = holderType.getMethod("data").invoke(user);
        Class<?> permissionType = Class.forName("net.luckperms.api.node.types.PermissionNode");
        Object builder = permissionType.getMethod("builder", String.class).invoke(null, permission);
        Object node =
                Class.forName("net.luckperms.api.node.types.PermissionNode$Builder")
                        .getMethod("build")
                        .invoke(builder);
        Class.forName("net.luckperms.api.model.data.NodeMap")
                .getMethod(
                        player.hasPermission(permission) ? "remove" : "add",
                        Class.forName("net.luckperms.api.node.Node"))
                .invoke(data, node);
        Object saved = userManager.getMethod("saveUser", userType).invoke(users, user);
        if (saved instanceof CompletionStage<?> stage)
            stage.whenComplete(
                    (ignored, error) -> {
                        if (error != null)
                            Bukkit.getLogger()
                                    .warning(
                                            "CustomEnchants permission changes could not be saved"
                                                    + " for "
                                                    + player.getUniqueId()
                                                    + ": "
                                                    + error.getMessage());
                    });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void vault(Player player, String permission)
            throws ReflectiveOperationException {
        Class<?> api = Class.forName("net.milkbowl.vault.permission.Permission");
        var registration = Bukkit.getServicesManager().getRegistration((Class) api);
        if (registration == null)
            throw new IllegalArgumentException("A Vault permission provider is required.");
        Object provider = registration.getProvider();
        boolean success =
                (boolean)
                        api.getMethod(
                                        player.hasPermission(permission)
                                                ? "playerRemove"
                                                : "playerAdd",
                                        Player.class,
                                        String.class)
                                .invoke(provider, player, permission);
        if (!success)
            throw new IllegalArgumentException("The permission provider rejected the change.");
    }
}
