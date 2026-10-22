package com.alkeari.genesis.config.fabric;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import com.alkeari.genesis.Genesis;

@Config(name = Genesis.MOD_ID)
public class GenesisFabricConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public boolean preview_enabled = true;

    @ConfigEntry.Gui.Tooltip
    public int preview_threads = 0;

    @ConfigEntry.Gui.Tooltip
    public int preview_initial_radius = 64;

    @ConfigEntry.Gui.Tooltip
    public int preview_expand_radius = 16;

    @ConfigEntry.Gui.Tooltip
    public boolean spawn_from_map = true;

    @ConfigEntry.Gui.Tooltip
    public boolean show_structures = true;

    @ConfigEntry.Gui.Tooltip
    public boolean server_world_map = true;

    @ConfigEntry.Gui.Tooltip
    public boolean server_world_map_structures = true;

    @ConfigEntry.Gui.Tooltip
    public boolean check_for_updates = true;

    @ConfigEntry.BoundedDiscrete(min = 1, max = 2048)
    @ConfigEntry.Gui.Tooltip
    public int pregen_default_radius = 128;

    @ConfigEntry.BoundedDiscrete(min = 1, max = 16)
    @ConfigEntry.Gui.Tooltip
    public int pregen_default_threads = 4;
}
