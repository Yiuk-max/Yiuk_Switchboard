package com.yiuk.switchboard.mods;

import com.yiuk.switchboard.control.RelayMessage;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ModListSnapshot {
    private static final Set<String> BUILT_INS = Set.of("minecraft", "neoforge", "java", "yiukswitchboard");
    private ModListSnapshot() {}

    public static List<RelayMessage.Mod> snapshot() {
        List<RelayMessage.Mod> result = new ArrayList<>();
        ModList.get().getMods().stream()
                .filter(mod -> !BUILT_INS.contains(mod.getModId()))
                .sorted((a, b) -> a.getModId().compareToIgnoreCase(b.getModId()))
                .forEach(mod -> {
                    result.add(new RelayMessage.Mod(mod.getModId(), mod.getVersion().toString()));
                });
        return result;
    }

    public enum Status { MATCH, MISSING, VERSION_MISMATCH, EXTRA, INVALID }
    public record Difference(String id, String roomVersion, String localVersion, Status status) {}

    public static List<Difference> differences(List<RelayMessage.Mod> hostMods) {
        List<Difference> result = new ArrayList<>();
        var local = new java.util.HashMap<String, String>();
        ModList.get().getMods().stream()
                .filter(mod -> !BUILT_INS.contains(mod.getModId()))
                .forEach(mod -> local.put(mod.getModId(), mod.getVersion().toString()));
        for (var mod : hostMods) {
            String id = mod.id();
            String version = mod.version();
            if (id.isBlank()) id = "?";
            if (version.isBlank()) version = "?";
            String localVersion = local.get(id);
            Status status = localVersion == null ? Status.MISSING
                    : localVersion.equals(version) ? Status.MATCH : Status.VERSION_MISMATCH;
            result.add(new Difference(id, version, localVersion, status));
            local.remove(id);
        }
        return result;
    }
}
