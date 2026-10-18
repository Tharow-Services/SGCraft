package gcewing.sg.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.UsernameCache;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

// Players looked up by name for access lists, without asking Mojang. The server's profile cache asks Mojang about any
// name it doesn't know, on the server thread: a second or more for each (much longer when Mojang is slow or limits the
// rate), and a packet could name hundreds, stalling the server until its watchdog stopped it. So only players online
// now, or who have been on this server (Forge's username cache keeps every one), are found.
public final class PlayerNames {

    private final MinecraftServer server;
    // Every player who has been on the server, by name in lower case, read from the cache the first time it's needed
    private Map<String, GameProfile> known;

    public PlayerNames(MinecraftServer server) {
        this.server = server;
    }

    // The player with this name (in any case), with their UUID and their name as they write it, or null
    @Nullable
    public GameProfile find(String name) {
        if (server == null || name == null || name.isEmpty()) {
            return null;
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null) {
            return online.getGameProfile();
        }
        if (known == null) {
            known = new HashMap<>();
            for (Map.Entry<UUID, String> entry : UsernameCache.getMap().entrySet()) {
                known.put(entry.getValue().toLowerCase(Locale.ROOT), new GameProfile(entry.getKey(), entry.getValue()));
            }
        }
        return known.get(name.toLowerCase(Locale.ROOT));
    }
}
