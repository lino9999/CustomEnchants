package io.github.lino9999.customenchants.feature;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.InvocationTargetException;

public final class EconomyService {
    private final Class<?> api;
    private final Object provider;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public EconomyService() {
        try {
            api = Class.forName("net.milkbowl.vault.economy.Economy");
            var registration = Bukkit.getServicesManager().getRegistration((Class) api);
            if (registration == null)
                throw new IllegalArgumentException("A Vault economy provider is required.");
            provider = registration.getProvider();
        } catch (ClassNotFoundException | LinkageError error) {
            throw new IllegalArgumentException(
                    "Vault and an economy provider are required.", error);
        }
    }

    public double balance(OfflinePlayer player) {
        double balance =
                ((Number) invoke("getBalance", new Class<?>[] {OfflinePlayer.class}, player))
                        .doubleValue();
        if (!Double.isFinite(balance) || balance < 0)
            throw new IllegalArgumentException("The economy provider returned an invalid balance.");
        return balance;
    }

    public void withdraw(OfflinePlayer player, double amount) {
        transaction("withdrawPlayer", player, amount);
    }

    public void deposit(OfflinePlayer player, double amount) {
        transaction("depositPlayer", player, amount);
    }

    public void transfer(OfflinePlayer source, OfflinePlayer recipient, double amount) {
        valid(amount);
        if (source.getUniqueId().equals(recipient.getUniqueId()) || amount == 0) return;
        withdraw(source, amount);
        try {
            deposit(recipient, amount);
        } catch (RuntimeException rejected) {
            try {
                deposit(source, amount);
            } catch (RuntimeException refund) {
                rejected.addSuppressed(refund);
                Bukkit.getLogger()
                        .severe(
                                "CustomEnchants could not refund a rejected economy transfer of "
                                        + amount
                                        + " to "
                                        + source.getUniqueId()
                                        + ". Manual reconciliation is required.");
            }
            throw rejected;
        }
    }

    private void transaction(String method, OfflinePlayer player, double amount) {
        valid(amount);
        if (amount == 0) return;
        Object response =
                invoke(method, new Class<?>[] {OfflinePlayer.class, double.class}, player, amount);
        try {
            if (!(boolean) response.getClass().getMethod("transactionSuccess").invoke(response))
                throw new IllegalArgumentException(
                        "The economy provider rejected the transaction.");
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private Object invoke(String method, Class<?>[] types, Object... arguments) {
        try {
            return api.getMethod(method, types).invoke(provider, arguments);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static IllegalArgumentException failure(ReflectiveOperationException error) {
        Throwable cause =
                error instanceof InvocationTargetException invocation
                        ? invocation.getCause()
                        : error;
        return new IllegalArgumentException(
                "The economy provider could not complete the operation.", cause);
    }

    private static void valid(double amount) {
        if (!Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("Money amounts must be finite and nonnegative.");
    }
}
