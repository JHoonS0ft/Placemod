package com.ternsip.placemod;


import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

/* This class statically loads blueprints and mod configuration and override generate method @author Ternsip */
public class Decorator implements IWorldGenerator {

    private static Distributor distributor = null;
    private static double density = 0.005; // Drop probability per chunk
    static double ratioA = 1, ratioB = 0.5; // Logistic f(x) = 2 / (1 + e ^ (-A * x ^ B)) - 1, default A = 1, B = 0.5
    static boolean strictMode = false; // Prevent floating islands to spawn in common biome additionally
    static boolean balanceMode = true; // Replace rich blocks to poor
    static boolean preventCommandBlock = false; // Prevent command block for spawning
    static double roughnessFactor = 1.0; // Multiplier of minimal acceptable roughness
    static double lootChance = 0.25; // Chest loot chance [0..1]
    static int forceLift = 0; // Pull out structure from the ground and lift up (recommended 0)
    static boolean preventMobSpawners = false; // Prevent mobspawners for spawning
    static boolean allowOnlyVanillaBlocks = true; // Allow only vanilla blocks to spawn
    static boolean[] soil = new boolean[256]; // Ground soil blocks
    static boolean[] overlook = new boolean[256]; // Plants, stuff, web, fire, decorative, etc.
    static boolean[] liquid = new boolean[256]; // Liquid blocks
    static Block[] vanillaBlocks = new Block[256]; // Default vanilla blocks by classical indices

    /* Load/Generate mod settings */
    private static void configure(File file) {
        if (new File(file.getParent()).mkdirs()) {
            new Report().add("CREATE CONFIG", file.getParent());
        }
        Properties config = new Properties();
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                config.load(fis);
                density = Double.parseDouble(config.getProperty("DENSITY", Double.toString(density)));
                ratioA = Double.parseDouble(config.getProperty("RATIO_A", Double.toString(ratioA)));
                ratioB = Double.parseDouble(config.getProperty("RATIO_B", Double.toString(ratioB)));
                strictMode = Boolean.parseBoolean(config.getProperty("STRICT_MODE", Boolean.toString(strictMode)));
                balanceMode = Boolean.parseBoolean(config.getProperty("BALANCE_MODE", Boolean.toString(balanceMode)));
                preventCommandBlock = Boolean.parseBoolean(config.getProperty("PREVENT_COMMAND_BLOCK", Boolean.toString(preventCommandBlock)));
                roughnessFactor = Double.parseDouble(config.getProperty("ROUGHNESS_FACTOR", Double.toString(roughnessFactor)));
                lootChance = Double.parseDouble(config.getProperty("CHEST_LOOT_CHANCE", Double.toString(lootChance)));
                forceLift = (int) Double.parseDouble(config.getProperty("FORCE_LIFT", Double.toString(forceLift)));
                preventMobSpawners = Boolean.parseBoolean(config.getProperty("PREVENT_MOB_SPAWNERS", Boolean.toString(preventMobSpawners)));
                allowOnlyVanillaBlocks = Boolean.parseBoolean(config.getProperty("ALLOW_ONLY_VANILLA_BLOCKS", Boolean.toString(allowOnlyVanillaBlocks)));
                fis.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            config.setProperty("DENSITY", Double.toString(density));
            config.setProperty("RATIO_A", Double.toString(ratioA));
            config.setProperty("RATIO_B", Double.toString(ratioB));
            config.setProperty("STRICT_MODE", Boolean.toString(strictMode));
            config.setProperty("BALANCE_MODE", Boolean.toString(balanceMode));
            config.setProperty("PREVENT_COMMAND_BLOCK", Boolean.toString(preventCommandBlock));
            config.setProperty("ROUGHNESS_FACTOR", Double.toString(roughnessFactor));
            config.setProperty("CHEST_LOOT_CHANCE", Double.toString(lootChance));
            config.setProperty("FORCE_LIFT", Integer.toString(forceLift));
            config.setProperty("PREVENT_MOB_SPAWNERS", Boolean.toString(preventMobSpawners));
            config.setProperty("ALLOW_ONLY_VANILLA_BLOCKS", Boolean.toString(allowOnlyVanillaBlocks));
            config.store(fos, null);
            fos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void loadStructures(File folder) {
        long startTime = System.currentTimeMillis();
        new Report().add("LOADING SCHEMATICS FROM", folder.getPath()).print();
        Stack<File> folders = new Stack<File>();
        folders.add(folder);
        ArrayList<Cluster> clusters = new ArrayList<Cluster>();
        HashMap<String, Cluster> villages = new HashMap<String, Cluster>();
        int loaded = 0;
        while (!folders.empty()) {
            File dir = folders.pop();
            File[] listOfFiles = dir.listFiles();
            for (File file : listOfFiles != null ? listOfFiles : new File[0]) {
                if (file.isFile()) {
                    try {
                        String pathParallel = file.getPath().replace("\\", "/").replace("//", "/").replace("/Schematics/", "/Structures/");
                        String pathFlags = pathParallel.replace(".schematic", ".flags");
                        String pathStructure = pathParallel.replace(".schematic", ".structure");
                        final Structure structure = new Structure(file, new File(pathFlags), new File(pathStructure));
                        loaded++;
                        String parent = file.getParent();
                        if (structure.flags.getString("Method").equalsIgnoreCase("Village")) {
                            if (villages.containsKey(parent)) {
                                villages.get(parent).add(structure);
                            } else {
                                villages.put(parent, new Cluster(parent).add(structure));
                            }
                        } else {
                            clusters.add(new Cluster(parent).add(structure));
                        }
                        int width = structure.flags.getShort("Width");
                        int height = structure.flags.getShort("Height");
                        int length = structure.flags.getShort("Length");
                        new Report()
                                .add("LOAD", file.getPath())
                                .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                                .add("LIFT", String.valueOf(structure.flags.getInteger("Lift")))
                                .add("METHOD", structure.flags.getString("Method"))
                                .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                                .print();
                    } catch (IOException ioe) {
                        new Report()
                                .add("CAN'T LOAD SCHEMATIC", file.getPath())
                                .add("ERROR", ioe.getMessage())
                                .print();
                    }
                } else if (file.isDirectory()) {
                    folders.add(file);
                }
            }
        }
        clusters.addAll(villages.values());
        distributor = new Distributor(clusters);
        long loadTime = (System.currentTimeMillis() - startTime);
        new Report()
                .add("LOADED CLUSTERS", String.valueOf(clusters.size()))
                .add("LOADED SCHEMATICS", String.valueOf(loaded))
                .add("LOAD TIME", new DecimalFormat("###0.00").format(loadTime / 1000.0) + "s")
                .print();
    }


    @Override
    public void generate(Random randomDefault, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
        Random random = getRandom(world.getSeed(), chunkX, chunkZ);
        int drops = (int) density + (random.nextDouble() <= (density - (int) density) ? 1 : 0);
        BiomeGenBase biome = world.getBiomeGenForCoords(new BlockPos(chunkX * 16, 64, chunkZ * 16));
        Biome.Style biomeStyle = Biome.determine(biome);
        ArrayList<Cluster> biomeClusters = distributor.getClusters(biomeStyle);
        for (int i = 0; i < drops; ++i) {
            double pointer = random.nextDouble();
            for (Cluster cluster : biomeClusters) {
                if (pointer <= cluster.getChance()) {
                    place(world, cluster, chunkX, chunkZ, random.nextLong());
                    break;
                }
                pointer -= cluster.getChance();
            }
        }
    }

    private static Random getRandom(long seed, int chunkX, int chunkZ) {
        long chunkIndex = (long)chunkX << 32 | chunkZ & 0xFFFFFFFFL;
        Random random = new Random(chunkIndex ^ seed);
        for (int i = 0; i < 16; ++i) {
            random.nextDouble();
        }
        return random;
    }

    private void place(World world, Cluster cluster, int chunkX, int chunkZ, long seed) {
        Random random = new Random(seed);
        int cx = chunkX * 16 + Math.abs(random.nextInt()) % 16;
        int cz = chunkZ * 16 + Math.abs(random.nextInt()) % 16;
        new Report()
                .add("PLACE CLUSTER", cluster.getName())
                .add("POS", "[CHUNK_X=" + chunkX + ";CHUNK_Z=" + chunkZ + "]")
                .add("SIZE", String.valueOf(cluster.getStructures().size()))
                .print();
        int curX = cx, curZ = cz, maxZ = 0;
        int timer = 0, delay = (int) Math.ceil(Math.sqrt(cluster.getStructures().size()));
        ArrayList<Structure> structures = new ArrayList<Structure>(cluster.getStructures());
        Collections.shuffle(structures, random);
        for (Structure structure : structures) {
            int rotX = 0, rotY = random.nextInt() % 4, rotZ = 0;
            boolean flipX = random.nextBoolean(), flipY = false, flipZ = random.nextBoolean();
            int width = structure.flags.getShort("Width");
            int height = structure.flags.getShort("Height");
            int length = structure.flags.getShort("Length");
            Posture posture = new Posture(0, 0, 0, rotX, rotY, rotZ, flipX, flipY, flipZ, width, height, length);
            if (--timer <= 0) {
                timer = delay;
                curX = cx;
                curZ += maxZ;
                maxZ = 0;
            }
            int sx = curX;
            int sz = curZ;
            curX += posture.getSizeX() + 1;
            maxZ = Math.max(maxZ, posture.getSizeZ());
            posture.shift(sx, 0, sz);
            long startTime = System.currentTimeMillis();
            try {
                Calibrator calibrator = new Calibrator(world, posture);
                posture.shift(0, calibrator.calibrate(structure, seed), 0);
                structure.paste(world, posture, random.nextLong());
                long spawnTime = System.currentTimeMillis() - startTime;
                new Report()
                        .add("PASTED", structure.schematicFile.getPath())
                        .add("SPAWN TIME", new DecimalFormat("###0.00").format(spawnTime / 1000.0) + "s")
                        .add("POS", "[X=" + posture.getPosX() + ";Y=" + posture.getPosY() + ";Z=" + posture.getPosZ() + "]")
                        .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                        .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                        .add("ROTATE", "[X=" + posture.getRotateX() + ";Y=" + posture.getRotateY() + ";Z=" + posture.getRotateZ() + "]")
                        .add("FLIP", "[X=" + posture.isFlipX() + ";Y=" + posture.isFlipY() + ";Z=" + posture.isFlipZ() + "]")
                        .print();
            } catch (IOException ioe) {
                long spentTime = System.currentTimeMillis() - startTime;
                new Report().add("CAN'T PASTE", structure.schematicFile.getPath())
                        .add("ERROR",  ioe.getMessage())
                        .add("POS", "[X=" + posture.getPosX() + ";Y=" + posture.getPosY() + ";Z=" + posture.getPosZ() + "]")
                        .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                        .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                        .add("ROTATE", "[X=" + posture.getRotateX() + ";Y=" + posture.getRotateY() + ";Z=" + posture.getRotateZ() + "]")
                        .add("FLIP", "[X=" + posture.isFlipX() + ";Y=" + posture.isFlipY() + ";Z=" + posture.isFlipZ() + "]")
                        .add("SPENT TIME", new DecimalFormat("###0.00").format(spentTime / 1000.0) + "s")
                        .print();
            }
        }
    }

    static {

        configure(new File("config/placemod.cfg"));

        soil[Block.getIdFromBlock(Blocks.grass)] = true;
        soil[Block.getIdFromBlock(Blocks.dirt)] = true;
        soil[Block.getIdFromBlock(Blocks.stone)] = true;
        soil[Block.getIdFromBlock(Blocks.cobblestone)] = true;
        soil[Block.getIdFromBlock(Blocks.sandstone)] = true;
        soil[Block.getIdFromBlock(Blocks.netherrack)] = true;
        soil[Block.getIdFromBlock(Blocks.gravel)] = true;
        soil[Block.getIdFromBlock(Blocks.sand)] = true;

        overlook[Block.getIdFromBlock(Blocks.air)] = true;
        overlook[Block.getIdFromBlock(Blocks.log)] = true;
        overlook[Block.getIdFromBlock(Blocks.log2)] = true;
        overlook[Block.getIdFromBlock(Blocks.leaves)] = true;
        overlook[Block.getIdFromBlock(Blocks.leaves2)] = true;
        overlook[Block.getIdFromBlock(Blocks.sapling)] = true;
        overlook[Block.getIdFromBlock(Blocks.web)] = true;
        overlook[Block.getIdFromBlock(Blocks.tallgrass)] = true;
        overlook[Block.getIdFromBlock(Blocks.deadbush)] = true;
        overlook[Block.getIdFromBlock(Blocks.yellow_flower)] = true;
        overlook[Block.getIdFromBlock(Blocks.red_flower)] = true;
        overlook[Block.getIdFromBlock(Blocks.red_mushroom_block)] = true;
        overlook[Block.getIdFromBlock(Blocks.brown_mushroom_block)] = true;
        overlook[Block.getIdFromBlock(Blocks.brown_mushroom)] = true;
        overlook[Block.getIdFromBlock(Blocks.fire)] = true;
        overlook[Block.getIdFromBlock(Blocks.wheat)] = true;
        overlook[Block.getIdFromBlock(Blocks.snow_layer)] = true;
        overlook[Block.getIdFromBlock(Blocks.snow)] = true;
        overlook[Block.getIdFromBlock(Blocks.cactus)] = true;
        overlook[Block.getIdFromBlock(Blocks.pumpkin)] = true;
        overlook[Block.getIdFromBlock(Blocks.vine)] = true;
        overlook[Block.getIdFromBlock(Blocks.waterlily)] = true;
        overlook[Block.getIdFromBlock(Blocks.double_plant)] = true;

        liquid[Block.getIdFromBlock(Blocks.water)] = true;
        liquid[Block.getIdFromBlock(Blocks.flowing_water)] = true;
        liquid[Block.getIdFromBlock(Blocks.ice)] = true;
        liquid[Block.getIdFromBlock(Blocks.lava)] = true;
        liquid[Block.getIdFromBlock(Blocks.flowing_lava)] = true;

        vanillaBlocks[0] = Blocks.air;
        vanillaBlocks[1] = Blocks.stone;
        vanillaBlocks[2] = Blocks.grass;
        vanillaBlocks[3] = Blocks.dirt;
        vanillaBlocks[4] = Blocks.cobblestone;
        vanillaBlocks[5] = Blocks.planks;
        vanillaBlocks[6] = Blocks.sapling;
        vanillaBlocks[7] = Blocks.bedrock;
        vanillaBlocks[8] = Blocks.flowing_water;
        vanillaBlocks[9] = Blocks.water;
        vanillaBlocks[10] = Blocks.flowing_lava;
        vanillaBlocks[11] = Blocks.lava;
        vanillaBlocks[12] = Blocks.sand;
        vanillaBlocks[13] = Blocks.gravel;
        vanillaBlocks[14] = Blocks.gold_ore;
        vanillaBlocks[15] = Blocks.iron_ore;
        vanillaBlocks[16] = Blocks.coal_ore;
        vanillaBlocks[17] = Blocks.log;
        vanillaBlocks[18] = Blocks.leaves;
        vanillaBlocks[19] = Blocks.sponge;
        vanillaBlocks[20] = Blocks.glass;
        vanillaBlocks[21] = Blocks.lapis_ore;
        vanillaBlocks[22] = Blocks.lapis_block;
        vanillaBlocks[23] = Blocks.dispenser;
        vanillaBlocks[24] = Blocks.sandstone;
        vanillaBlocks[25] = Blocks.noteblock;
        vanillaBlocks[26] = Blocks.bed;
        vanillaBlocks[27] = Blocks.golden_rail;
        vanillaBlocks[28] = Blocks.detector_rail;
        vanillaBlocks[29] = Blocks.sticky_piston;
        vanillaBlocks[30] = Blocks.web;
        vanillaBlocks[31] = Blocks.tallgrass;
        vanillaBlocks[32] = Blocks.deadbush;
        vanillaBlocks[33] = Blocks.piston;
        vanillaBlocks[34] = Blocks.piston_head;
        vanillaBlocks[35] = Blocks.wool;
        vanillaBlocks[36] = Blocks.piston_extension;
        vanillaBlocks[37] = Blocks.yellow_flower;
        vanillaBlocks[38] = Blocks.red_flower;
        vanillaBlocks[39] = Blocks.brown_mushroom;
        vanillaBlocks[40] = Blocks.red_mushroom;
        vanillaBlocks[41] = Blocks.gold_block;
        vanillaBlocks[42] = Blocks.iron_block;
        vanillaBlocks[43] = Blocks.double_stone_slab;
        vanillaBlocks[44] = Blocks.stone_slab;
        vanillaBlocks[45] = Blocks.brick_block;
        vanillaBlocks[46] = Blocks.tnt;
        vanillaBlocks[47] = Blocks.bookshelf;
        vanillaBlocks[48] = Blocks.mossy_cobblestone;
        vanillaBlocks[49] = Blocks.obsidian;
        vanillaBlocks[50] = Blocks.torch;
        vanillaBlocks[51] = Blocks.fire;
        vanillaBlocks[52] = Blocks.mob_spawner;
        vanillaBlocks[53] = Blocks.oak_stairs;
        vanillaBlocks[54] = Blocks.chest;
        vanillaBlocks[55] = Blocks.redstone_wire;
        vanillaBlocks[56] = Blocks.diamond_ore;
        vanillaBlocks[57] = Blocks.diamond_block;
        vanillaBlocks[58] = Blocks.crafting_table;
        vanillaBlocks[59] = Blocks.wheat;
        vanillaBlocks[60] = Blocks.farmland;
        vanillaBlocks[61] = Blocks.furnace;
        vanillaBlocks[62] = Blocks.lit_furnace;
        vanillaBlocks[63] = Blocks.standing_sign;
        vanillaBlocks[64] = Blocks.oak_door;
        vanillaBlocks[65] = Blocks.ladder;
        vanillaBlocks[66] = Blocks.rail;
        vanillaBlocks[67] = Blocks.stone_stairs;
        vanillaBlocks[68] = Blocks.wall_sign;
        vanillaBlocks[69] = Blocks.lever;
        vanillaBlocks[70] = Blocks.stone_pressure_plate;
        vanillaBlocks[71] = Blocks.iron_door;
        vanillaBlocks[72] = Blocks.wooden_pressure_plate;
        vanillaBlocks[73] = Blocks.redstone_ore;
        vanillaBlocks[74] = Blocks.lit_redstone_ore;
        vanillaBlocks[75] = Blocks.unlit_redstone_torch;
        vanillaBlocks[76] = Blocks.redstone_torch;
        vanillaBlocks[77] = Blocks.stone_button;
        vanillaBlocks[78] = Blocks.snow_layer;
        vanillaBlocks[79] = Blocks.ice;
        vanillaBlocks[80] = Blocks.snow;
        vanillaBlocks[81] = Blocks.cactus;
        vanillaBlocks[82] = Blocks.clay;
        vanillaBlocks[83] = Blocks.reeds;
        vanillaBlocks[84] = Blocks.jukebox;
        vanillaBlocks[85] = Blocks.oak_fence;
        vanillaBlocks[86] = Blocks.pumpkin;
        vanillaBlocks[87] = Blocks.netherrack;
        vanillaBlocks[88] = Blocks.soul_sand;
        vanillaBlocks[89] = Blocks.glowstone;
        vanillaBlocks[90] = Blocks.portal;
        vanillaBlocks[91] = Blocks.lit_pumpkin;
        vanillaBlocks[92] = Blocks.cake;
        vanillaBlocks[93] = Blocks.unpowered_repeater;
        vanillaBlocks[94] = Blocks.powered_repeater;
        vanillaBlocks[95] = Blocks.stained_glass;
        vanillaBlocks[96] = Blocks.trapdoor;
        vanillaBlocks[97] = Blocks.monster_egg;
        vanillaBlocks[98] = Blocks.stonebrick;
        vanillaBlocks[99] = Blocks.brown_mushroom_block;
        vanillaBlocks[100] = Blocks.red_mushroom_block;
        vanillaBlocks[101] = Blocks.iron_bars;
        vanillaBlocks[102] = Blocks.glass_pane;
        vanillaBlocks[103] = Blocks.melon_block;
        vanillaBlocks[104] = Blocks.pumpkin_stem;
        vanillaBlocks[105] = Blocks.melon_stem;
        vanillaBlocks[106] = Blocks.vine;
        vanillaBlocks[107] = Blocks.oak_fence_gate;
        vanillaBlocks[108] = Blocks.brick_stairs;
        vanillaBlocks[109] = Blocks.stone_brick_stairs;
        vanillaBlocks[110] = Blocks.mycelium;
        vanillaBlocks[111] = Blocks.waterlily;
        vanillaBlocks[112] = Blocks.nether_brick;
        vanillaBlocks[113] = Blocks.nether_brick_fence;
        vanillaBlocks[114] = Blocks.nether_brick_stairs;
        vanillaBlocks[115] = Blocks.nether_wart;
        vanillaBlocks[116] = Blocks.enchanting_table;
        vanillaBlocks[117] = Blocks.brewing_stand;
        vanillaBlocks[118] = Blocks.cauldron;
        vanillaBlocks[119] = Blocks.end_portal;
        vanillaBlocks[120] = Blocks.end_portal_frame;
        vanillaBlocks[121] = Blocks.end_stone;
        vanillaBlocks[122] = Blocks.dragon_egg;
        vanillaBlocks[123] = Blocks.redstone_lamp;
        vanillaBlocks[124] = Blocks.lit_redstone_lamp;
        vanillaBlocks[125] = Blocks.double_wooden_slab;
        vanillaBlocks[126] = Blocks.wooden_slab;
        vanillaBlocks[127] = Blocks.cocoa;
        vanillaBlocks[128] = Blocks.sandstone_stairs;
        vanillaBlocks[129] = Blocks.emerald_ore;
        vanillaBlocks[130] = Blocks.ender_chest;
        vanillaBlocks[131] = Blocks.tripwire_hook;
        vanillaBlocks[132] = Blocks.tripwire;
        vanillaBlocks[133] = Blocks.emerald_block;
        vanillaBlocks[134] = Blocks.spruce_stairs;
        vanillaBlocks[135] = Blocks.birch_stairs;
        vanillaBlocks[136] = Blocks.jungle_stairs;
        vanillaBlocks[137] = Blocks.command_block;
        vanillaBlocks[138] = Blocks.beacon;
        vanillaBlocks[139] = Blocks.cobblestone_wall;
        vanillaBlocks[140] = Blocks.flower_pot;
        vanillaBlocks[141] = Blocks.carrots;
        vanillaBlocks[142] = Blocks.potatoes;
        vanillaBlocks[143] = Blocks.wooden_button;
        vanillaBlocks[144] = Blocks.skull;
        vanillaBlocks[145] = Blocks.anvil;
        vanillaBlocks[146] = Blocks.trapped_chest;
        vanillaBlocks[147] = Blocks.light_weighted_pressure_plate;
        vanillaBlocks[148] = Blocks.heavy_weighted_pressure_plate;
        vanillaBlocks[149] = Blocks.unpowered_comparator;
        vanillaBlocks[150] = Blocks.powered_comparator;
        vanillaBlocks[151] = Blocks.daylight_detector;
        vanillaBlocks[152] = Blocks.redstone_block;
        vanillaBlocks[153] = Blocks.quartz_ore;
        vanillaBlocks[154] = Blocks.hopper;
        vanillaBlocks[155] = Blocks.quartz_block;
        vanillaBlocks[156] = Blocks.quartz_stairs;
        vanillaBlocks[157] = Blocks.activator_rail;
        vanillaBlocks[158] = Blocks.dropper;
        vanillaBlocks[159] = Blocks.stained_hardened_clay;
        vanillaBlocks[160] = Blocks.stained_glass_pane;
        vanillaBlocks[161] = Blocks.leaves2;
        vanillaBlocks[162] = Blocks.log2;
        vanillaBlocks[163] = Blocks.acacia_stairs;
        vanillaBlocks[164] = Blocks.dark_oak_stairs;
        vanillaBlocks[165] = Blocks.slime_block;
        vanillaBlocks[166] = Blocks.barrier;
        vanillaBlocks[167] = Blocks.iron_trapdoor;
        vanillaBlocks[168] = Blocks.prismarine;
        vanillaBlocks[169] = Blocks.sea_lantern;
        vanillaBlocks[170] = Blocks.hay_block;
        vanillaBlocks[171] = Blocks.carpet;
        vanillaBlocks[172] = Blocks.hardened_clay;
        vanillaBlocks[173] = Blocks.coal_block;
        vanillaBlocks[174] = Blocks.packed_ice;
        vanillaBlocks[175] = Blocks.double_plant;
        vanillaBlocks[176] = Blocks.standing_banner;
        vanillaBlocks[177] = Blocks.wall_banner;
        vanillaBlocks[178] = Blocks.daylight_detector_inverted;
        vanillaBlocks[179] = Blocks.red_sandstone;
        vanillaBlocks[180] = Blocks.red_sandstone_stairs;
        vanillaBlocks[181] = Blocks.double_stone_slab2;
        vanillaBlocks[182] = Blocks.stone_slab2;
        vanillaBlocks[183] = Blocks.spruce_fence_gate;
        vanillaBlocks[184] = Blocks.birch_fence_gate;
        vanillaBlocks[185] = Blocks.jungle_fence_gate;
        vanillaBlocks[186] = Blocks.dark_oak_fence_gate;
        vanillaBlocks[187] = Blocks.acacia_fence_gate;
        vanillaBlocks[188] = Blocks.spruce_fence;
        vanillaBlocks[189] = Blocks.birch_fence;
        vanillaBlocks[190] = Blocks.jungle_fence;
        vanillaBlocks[191] = Blocks.dark_oak_fence;
        vanillaBlocks[192] = Blocks.acacia_fence;
        vanillaBlocks[193] = Blocks.spruce_door;
        vanillaBlocks[194] = Blocks.birch_door;
        vanillaBlocks[195] = Blocks.jungle_door;
        vanillaBlocks[196] = Blocks.acacia_door;
        vanillaBlocks[197] = null;
        vanillaBlocks[198] = null;
        vanillaBlocks[199] = null;
        vanillaBlocks[200] = null;
        vanillaBlocks[201] = null;
        vanillaBlocks[202] = null;
        vanillaBlocks[203] = null;
        vanillaBlocks[204] = null;
        vanillaBlocks[205] = null;
        vanillaBlocks[206] = null;
        vanillaBlocks[207] = null;
        vanillaBlocks[208] = null;
        vanillaBlocks[209] = null;
        vanillaBlocks[210] = null;
        vanillaBlocks[211] = null;
        vanillaBlocks[212] = null;
        vanillaBlocks[213] = null;
        vanillaBlocks[214] = null;
        vanillaBlocks[215] = null;
        vanillaBlocks[216] = null;
        vanillaBlocks[217] = null;
        vanillaBlocks[218] = null;
        vanillaBlocks[219] = null;
        vanillaBlocks[220] = null;
        vanillaBlocks[221] = null;
        vanillaBlocks[222] = null;
        vanillaBlocks[223] = null;
        vanillaBlocks[224] = null;
        vanillaBlocks[225] = null;
        vanillaBlocks[226] = null;
        vanillaBlocks[227] = null;
        vanillaBlocks[228] = null;
        vanillaBlocks[229] = null;
        vanillaBlocks[230] = null;
        vanillaBlocks[231] = null;
        vanillaBlocks[232] = null;
        vanillaBlocks[233] = null;
        vanillaBlocks[234] = null;
        vanillaBlocks[235] = null;
        vanillaBlocks[236] = null;
        vanillaBlocks[237] = null;
        vanillaBlocks[238] = null;
        vanillaBlocks[239] = null;
        vanillaBlocks[240] = null;
        vanillaBlocks[241] = null;
        vanillaBlocks[242] = null;
        vanillaBlocks[243] = null;
        vanillaBlocks[244] = null;
        vanillaBlocks[245] = null;
        vanillaBlocks[246] = null;
        vanillaBlocks[247] = null;
        vanillaBlocks[248] = null;
        vanillaBlocks[249] = null;
        vanillaBlocks[250] = null;
        vanillaBlocks[251] = null;
        vanillaBlocks[252] = null;
        vanillaBlocks[253] = null;
        vanillaBlocks[254] = null;
        vanillaBlocks[255] = null;

        loadStructures(new File("Placemod/Schematics/"));

    }

}
