package com.alkeari.genesis.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldCreationUiState.class)
public interface WorldCreationUiStateAccessor {

    /**
     * Exposes the WorldCreationContext held inside WorldCreationUiState.
     * Field name in Mojang mappings: "settings".
     */
    @Accessor("settings")
    WorldCreationContext genesis_getContext();

    @SuppressWarnings("unused")
    @Accessor("settings")
    void genesis_setContext(WorldCreationContext context);
}
