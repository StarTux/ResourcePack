package com.cavetale.resourcepack;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public enum LoadStatus {
    LOADED(text("Loaded", GREEN)),
    LOADED_OUTDATED(text("Outdated", YELLOW)),
    NOT_LOADED(text("Not Loaded", RED));

    public final Component title;
}
