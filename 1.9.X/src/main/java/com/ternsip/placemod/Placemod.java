package com.ternsip.placemod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;


/* Main mod class. Forge will handle all registered events. */
@Mod(   modid = Placemod.MODID,
        name = Placemod.MODNAME,
        version = Placemod.VERSION,
        acceptableRemoteVersions = "*")
public class Placemod {

    static final String MODID = "placemod";
    static final String MODNAME = "Placemod";
    static final String VERSION = "3.1";
    public static final String AUTHOR = "Ternsip";
    public static final String MCVERSION = "1.9.*";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        GameRegistry.registerWorldGenerator(new Decorator(), 4096);
    }

}
