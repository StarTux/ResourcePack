package com.cavetale.resourcepack;

import com.cavetale.core.resourcepack.ServerResourcePack;
import java.util.UUID;

/**
 * Core API.
 */
public final class CoreServerResourcePack implements ServerResourcePack {
    @Override
    public boolean has(UUID uuid) {
        return ResourcePackPlugin.instance.loadedCache.contains(uuid);
    }
}
