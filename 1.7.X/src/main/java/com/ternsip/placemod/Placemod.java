package com.ternsip.placemod;


import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;

/* Main mod class. Forge will handle all registered events. */
@Mod(   modid = Placemod.MODID,
        name = Placemod.MODNAME,
        version = Placemod.VERSION,
        acceptableRemoteVersions = "*")
public class Placemod {

    static final String MODID = "placemod";
    static final String MODNAME = "Placemod";
    static final String VERSION = "3.8";
    public static final String AUTHOR = "Ternsip";
    public static final String MCVERSION = "1.7.*";


    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        GameRegistry.registerWorldGenerator(new Decorator(), 4096);
    }

}
