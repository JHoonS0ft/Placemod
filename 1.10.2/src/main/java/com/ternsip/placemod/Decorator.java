package com.ternsip.placemod;


import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameData;

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
    static int minChestItems = 2; // Min number of stack per chest inclusive
    static int maxChestItems = 7; // Max number of stacks per chest exclusive
    static int maxChestStackSize = 3; // Max item stack size for chest loot
    static boolean loadOutput = true; // Prints load output to console
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
                minChestItems = (int) Double.parseDouble(config.getProperty("MIN_CHEST_ITEMS", Double.toString(minChestItems)));
                maxChestItems = (int) Double.parseDouble(config.getProperty("MAX_CHEST_ITEMS", Double.toString(maxChestItems)));
                maxChestStackSize = (int) Double.parseDouble(config.getProperty("MAX_CHEST_STACK_SIZE", Double.toString(maxChestStackSize)));
                loadOutput = Boolean.parseBoolean(config.getProperty("LOAD_OUTPUT", Boolean.toString(loadOutput)));
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
            config.setProperty("MIN_CHEST_ITEMS", Integer.toString(minChestItems));
            config.setProperty("MAX_CHEST_ITEMS", Integer.toString(maxChestItems));
            config.setProperty("MAX_CHEST_STACK_SIZE", Integer.toString(maxChestStackSize));
            config.setProperty("LOAD_OUTPUT", Boolean.toString(loadOutput));
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
                        if (loadOutput) {
                            new Report()
                                    .add("LOAD", file.getPath())
                                    .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                                    .add("LIFT", String.valueOf(structure.flags.getInteger("Lift")))
                                    .add("METHOD", structure.flags.getString("Method"))
                                    .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                                    .print();
                        }
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
    public void generate(Random randomDefault, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        Random random = getRandom(world.getSeed(), chunkX, chunkZ);
        int drops = (int) density + (random.nextDouble() <= (density - (int) density) ? 1 : 0);
        net.minecraft.world.biome.Biome biome = world.getBiomeGenForCoords(new BlockPos(chunkX * 16, 64, chunkZ * 16));
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
                .add("PLACE CLUSTER", cluster.getSign())
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
                new Report()
                        .add("CAN'T PASTE", structure.schematicFile.getPath())
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

    private static void bindHooks() {
        //ResourceLocation hooks = LootTableList.register("Placemod");
        //for (ResourceLocation itemName : GameData.getItemRegistry().getKeys()) {
        //    Item item = Item.itemRegistry.getObject(itemName);
        //    int maxDmg = item.getMaxDamage();
        //    for (int meta = 0; meta <= maxDmg; ++meta) {
        //        hooks.addItem(new WeightedRandomChestContent(new ItemStack(item, 1, meta), 1, maxChestStackSize, 256 / (1 + maxDmg)));
        //    }
        //}
        //hooks.setMin(minChestItems);
        //hooks.setMax(maxChestItems);
    }


    static {

        configure(new File("config/placemod.cfg"));

        soil[Block.getIdFromBlock(Blocks.GRASS)] = true;
        soil[Block.getIdFromBlock(Blocks.DIRT)] = true;
        soil[Block.getIdFromBlock(Blocks.STONE)] = true;
        soil[Block.getIdFromBlock(Blocks.COBBLESTONE)] = true;
        soil[Block.getIdFromBlock(Blocks.SANDSTONE)] = true;
        soil[Block.getIdFromBlock(Blocks.NETHERRACK)] = true;
        soil[Block.getIdFromBlock(Blocks.GRAVEL)] = true;
        soil[Block.getIdFromBlock(Blocks.SAND)] = true;

        overlook[Block.getIdFromBlock(Blocks.AIR)] = true;
        overlook[Block.getIdFromBlock(Blocks.LOG)] = true;
        overlook[Block.getIdFromBlock(Blocks.LOG2)] = true;
        overlook[Block.getIdFromBlock(Blocks.LEAVES)] = true;
        overlook[Block.getIdFromBlock(Blocks.LEAVES2)] = true;
        overlook[Block.getIdFromBlock(Blocks.SAPLING)] = true;
        overlook[Block.getIdFromBlock(Blocks.WEB)] = true;
        overlook[Block.getIdFromBlock(Blocks.TALLGRASS)] = true;
        overlook[Block.getIdFromBlock(Blocks.DEADBUSH)] = true;
        overlook[Block.getIdFromBlock(Blocks.YELLOW_FLOWER)] = true;
        overlook[Block.getIdFromBlock(Blocks.RED_FLOWER)] = true;
        overlook[Block.getIdFromBlock(Blocks.RED_MUSHROOM_BLOCK)] = true;
        overlook[Block.getIdFromBlock(Blocks.BROWN_MUSHROOM_BLOCK)] = true;
        overlook[Block.getIdFromBlock(Blocks.BROWN_MUSHROOM)] = true;
        overlook[Block.getIdFromBlock(Blocks.FIRE)] = true;
        overlook[Block.getIdFromBlock(Blocks.WHEAT)] = true;
        overlook[Block.getIdFromBlock(Blocks.SNOW_LAYER)] = true;
        overlook[Block.getIdFromBlock(Blocks.SNOW)] = true;
        overlook[Block.getIdFromBlock(Blocks.CACTUS)] = true;
        overlook[Block.getIdFromBlock(Blocks.PUMPKIN)] = true;
        overlook[Block.getIdFromBlock(Blocks.VINE)] = true;
        overlook[Block.getIdFromBlock(Blocks.WATERLILY)] = true;
        overlook[Block.getIdFromBlock(Blocks.DOUBLE_PLANT)] = true;

        liquid[Block.getIdFromBlock(Blocks.WATER)] = true;
        liquid[Block.getIdFromBlock(Blocks.FLOWING_WATER)] = true;
        liquid[Block.getIdFromBlock(Blocks.ICE)] = true;
        liquid[Block.getIdFromBlock(Blocks.LAVA)] = true;
        liquid[Block.getIdFromBlock(Blocks.FLOWING_LAVA)] = true;


        vanillaBlocks[0] = Blocks.AIR;
        vanillaBlocks[1] = Blocks.STONE;
        vanillaBlocks[2] = Blocks.GRASS;
        vanillaBlocks[3] = Blocks.DIRT;
        vanillaBlocks[4] = Blocks.COBBLESTONE;
        vanillaBlocks[5] = Blocks.PLANKS;
        vanillaBlocks[6] = Blocks.SAPLING;
        vanillaBlocks[7] = Blocks.BEDROCK;
        vanillaBlocks[8] = Blocks.FLOWING_WATER;
        vanillaBlocks[9] = Blocks.WATER;
        vanillaBlocks[10] = Blocks.FLOWING_LAVA;
        vanillaBlocks[11] = Blocks.LAVA;
        vanillaBlocks[12] = Blocks.SAND;
        vanillaBlocks[13] = Blocks.GRAVEL;
        vanillaBlocks[14] = Blocks.GOLD_ORE;
        vanillaBlocks[15] = Blocks.IRON_ORE;
        vanillaBlocks[16] = Blocks.COAL_ORE;
        vanillaBlocks[17] = Blocks.LOG;
        vanillaBlocks[18] = Blocks.LEAVES;
        vanillaBlocks[19] = Blocks.SPONGE;
        vanillaBlocks[20] = Blocks.GLASS;
        vanillaBlocks[21] = Blocks.LAPIS_ORE;
        vanillaBlocks[22] = Blocks.LAPIS_BLOCK;
        vanillaBlocks[23] = Blocks.DISPENSER;
        vanillaBlocks[24] = Blocks.SANDSTONE;
        vanillaBlocks[25] = Blocks.NOTEBLOCK;
        vanillaBlocks[26] = Blocks.BED;
        vanillaBlocks[27] = Blocks.GOLDEN_RAIL;
        vanillaBlocks[28] = Blocks.DETECTOR_RAIL;
        vanillaBlocks[29] = Blocks.STICKY_PISTON;
        vanillaBlocks[30] = Blocks.WEB;
        vanillaBlocks[31] = Blocks.TALLGRASS;
        vanillaBlocks[32] = Blocks.DEADBUSH;
        vanillaBlocks[33] = Blocks.PISTON;
        vanillaBlocks[34] = Blocks.PISTON_HEAD;
        vanillaBlocks[35] = Blocks.WOOL;
        vanillaBlocks[36] = Blocks.PISTON_EXTENSION;
        vanillaBlocks[37] = Blocks.YELLOW_FLOWER;
        vanillaBlocks[38] = Blocks.RED_FLOWER;
        vanillaBlocks[39] = Blocks.BROWN_MUSHROOM;
        vanillaBlocks[40] = Blocks.RED_MUSHROOM;
        vanillaBlocks[41] = Blocks.GOLD_BLOCK;
        vanillaBlocks[42] = Blocks.IRON_BLOCK;
        vanillaBlocks[43] = Blocks.DOUBLE_STONE_SLAB;
        vanillaBlocks[44] = Blocks.STONE_SLAB;
        vanillaBlocks[45] = Blocks.BRICK_BLOCK;
        vanillaBlocks[46] = Blocks.TNT;
        vanillaBlocks[47] = Blocks.BOOKSHELF;
        vanillaBlocks[48] = Blocks.MOSSY_COBBLESTONE;
        vanillaBlocks[49] = Blocks.OBSIDIAN;
        vanillaBlocks[50] = Blocks.TORCH;
        vanillaBlocks[51] = Blocks.FIRE;
        vanillaBlocks[52] = Blocks.MOB_SPAWNER;
        vanillaBlocks[53] = Blocks.OAK_STAIRS;
        vanillaBlocks[54] = Blocks.CHEST;
        vanillaBlocks[55] = Blocks.REDSTONE_WIRE;
        vanillaBlocks[56] = Blocks.DIAMOND_ORE;
        vanillaBlocks[57] = Blocks.DIAMOND_BLOCK;
        vanillaBlocks[58] = Blocks.CRAFTING_TABLE;
        vanillaBlocks[59] = Blocks.WHEAT;
        vanillaBlocks[60] = Blocks.FARMLAND;
        vanillaBlocks[61] = Blocks.FURNACE;
        vanillaBlocks[62] = Blocks.LIT_FURNACE;
        vanillaBlocks[63] = Blocks.STANDING_SIGN;
        vanillaBlocks[64] = Blocks.OAK_DOOR;
        vanillaBlocks[65] = Blocks.LADDER;
        vanillaBlocks[66] = Blocks.RAIL;
        vanillaBlocks[67] = Blocks.STONE_STAIRS;
        vanillaBlocks[68] = Blocks.WALL_SIGN;
        vanillaBlocks[69] = Blocks.LEVER;
        vanillaBlocks[70] = Blocks.STONE_PRESSURE_PLATE;
        vanillaBlocks[71] = Blocks.IRON_DOOR;
        vanillaBlocks[72] = Blocks.WOODEN_PRESSURE_PLATE;
        vanillaBlocks[73] = Blocks.REDSTONE_ORE;
        vanillaBlocks[74] = Blocks.LIT_REDSTONE_ORE;
        vanillaBlocks[75] = Blocks.UNLIT_REDSTONE_TORCH;
        vanillaBlocks[76] = Blocks.REDSTONE_TORCH;
        vanillaBlocks[77] = Blocks.STONE_BUTTON;
        vanillaBlocks[78] = Blocks.SNOW_LAYER;
        vanillaBlocks[79] = Blocks.ICE;
        vanillaBlocks[80] = Blocks.SNOW;
        vanillaBlocks[81] = Blocks.CACTUS;
        vanillaBlocks[82] = Blocks.CLAY;
        vanillaBlocks[83] = Blocks.REEDS;
        vanillaBlocks[84] = Blocks.JUKEBOX;
        vanillaBlocks[85] = Blocks.OAK_FENCE;
        vanillaBlocks[86] = Blocks.PUMPKIN;
        vanillaBlocks[87] = Blocks.NETHERRACK;
        vanillaBlocks[88] = Blocks.SOUL_SAND;
        vanillaBlocks[89] = Blocks.GLOWSTONE;
        vanillaBlocks[90] = Blocks.PORTAL;
        vanillaBlocks[91] = Blocks.LIT_PUMPKIN;
        vanillaBlocks[92] = Blocks.CAKE;
        vanillaBlocks[93] = Blocks.UNPOWERED_REPEATER;
        vanillaBlocks[94] = Blocks.POWERED_REPEATER;
        vanillaBlocks[95] = Blocks.STAINED_GLASS;
        vanillaBlocks[96] = Blocks.TRAPDOOR;
        vanillaBlocks[97] = Blocks.MONSTER_EGG;
        vanillaBlocks[98] = Blocks.STONEBRICK;
        vanillaBlocks[99] = Blocks.BROWN_MUSHROOM_BLOCK;
        vanillaBlocks[100] = Blocks.RED_MUSHROOM_BLOCK;
        vanillaBlocks[101] = Blocks.IRON_BARS;
        vanillaBlocks[102] = Blocks.GLASS_PANE;
        vanillaBlocks[103] = Blocks.MELON_BLOCK;
        vanillaBlocks[104] = Blocks.PUMPKIN_STEM;
        vanillaBlocks[105] = Blocks.MELON_STEM;
        vanillaBlocks[106] = Blocks.VINE;
        vanillaBlocks[107] = Blocks.OAK_FENCE_GATE;
        vanillaBlocks[108] = Blocks.BRICK_STAIRS;
        vanillaBlocks[109] = Blocks.STONE_BRICK_STAIRS;
        vanillaBlocks[110] = Blocks.MYCELIUM;
        vanillaBlocks[111] = Blocks.WATERLILY;
        vanillaBlocks[112] = Blocks.NETHER_BRICK;
        vanillaBlocks[113] = Blocks.NETHER_BRICK_FENCE;
        vanillaBlocks[114] = Blocks.NETHER_BRICK_STAIRS;
        vanillaBlocks[115] = Blocks.NETHER_WART;
        vanillaBlocks[116] = Blocks.ENCHANTING_TABLE;
        vanillaBlocks[117] = Blocks.BREWING_STAND;
        vanillaBlocks[118] = Blocks.CAULDRON;
        vanillaBlocks[119] = Blocks.END_PORTAL;
        vanillaBlocks[120] = Blocks.END_PORTAL_FRAME;
        vanillaBlocks[121] = Blocks.END_STONE;
        vanillaBlocks[122] = Blocks.DRAGON_EGG;
        vanillaBlocks[123] = Blocks.REDSTONE_LAMP;
        vanillaBlocks[124] = Blocks.LIT_REDSTONE_LAMP;
        vanillaBlocks[125] = Blocks.DOUBLE_WOODEN_SLAB;
        vanillaBlocks[126] = Blocks.WOODEN_SLAB;
        vanillaBlocks[127] = Blocks.COCOA;
        vanillaBlocks[128] = Blocks.SANDSTONE_STAIRS;
        vanillaBlocks[129] = Blocks.EMERALD_ORE;
        vanillaBlocks[130] = Blocks.ENDER_CHEST;
        vanillaBlocks[131] = Blocks.TRIPWIRE_HOOK;
        vanillaBlocks[132] = Blocks.TRIPWIRE;
        vanillaBlocks[133] = Blocks.EMERALD_BLOCK;
        vanillaBlocks[134] = Blocks.SPRUCE_STAIRS;
        vanillaBlocks[135] = Blocks.BIRCH_STAIRS;
        vanillaBlocks[136] = Blocks.JUNGLE_STAIRS;
        vanillaBlocks[137] = Blocks.COMMAND_BLOCK;
        vanillaBlocks[138] = Blocks.BEACON;
        vanillaBlocks[139] = Blocks.COBBLESTONE_WALL;
        vanillaBlocks[140] = Blocks.FLOWER_POT;
        vanillaBlocks[141] = Blocks.CARROTS;
        vanillaBlocks[142] = Blocks.POTATOES;
        vanillaBlocks[143] = Blocks.WOODEN_BUTTON;
        vanillaBlocks[144] = Blocks.SKULL;
        vanillaBlocks[145] = Blocks.ANVIL;
        vanillaBlocks[146] = Blocks.TRAPPED_CHEST;
        vanillaBlocks[147] = Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE;
        vanillaBlocks[148] = Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE;
        vanillaBlocks[149] = Blocks.UNPOWERED_COMPARATOR;
        vanillaBlocks[150] = Blocks.POWERED_COMPARATOR;
        vanillaBlocks[151] = Blocks.DAYLIGHT_DETECTOR;
        vanillaBlocks[152] = Blocks.REDSTONE_BLOCK;
        vanillaBlocks[153] = Blocks.QUARTZ_ORE;
        vanillaBlocks[154] = Blocks.HOPPER;
        vanillaBlocks[155] = Blocks.QUARTZ_BLOCK;
        vanillaBlocks[156] = Blocks.QUARTZ_STAIRS;
        vanillaBlocks[157] = Blocks.ACTIVATOR_RAIL;
        vanillaBlocks[158] = Blocks.DROPPER;
        vanillaBlocks[159] = Blocks.STAINED_HARDENED_CLAY;
        vanillaBlocks[160] = Blocks.STAINED_GLASS_PANE;
        vanillaBlocks[161] = Blocks.LEAVES2;
        vanillaBlocks[162] = Blocks.LOG2;
        vanillaBlocks[163] = Blocks.ACACIA_STAIRS;
        vanillaBlocks[164] = Blocks.DARK_OAK_STAIRS;
        vanillaBlocks[165] = Blocks.SLIME_BLOCK;
        vanillaBlocks[166] = Blocks.BARRIER;
        vanillaBlocks[167] = Blocks.IRON_TRAPDOOR;
        vanillaBlocks[168] = Blocks.PRISMARINE;
        vanillaBlocks[169] = Blocks.SEA_LANTERN;
        vanillaBlocks[170] = Blocks.HAY_BLOCK;
        vanillaBlocks[171] = Blocks.CARPET;
        vanillaBlocks[172] = Blocks.HARDENED_CLAY;
        vanillaBlocks[173] = Blocks.COAL_BLOCK;
        vanillaBlocks[174] = Blocks.PACKED_ICE;
        vanillaBlocks[175] = Blocks.DOUBLE_PLANT;
        vanillaBlocks[176] = Blocks.STANDING_BANNER;
        vanillaBlocks[177] = Blocks.WALL_BANNER;
        vanillaBlocks[178] = Blocks.DAYLIGHT_DETECTOR_INVERTED;
        vanillaBlocks[179] = Blocks.RED_SANDSTONE;
        vanillaBlocks[180] = Blocks.RED_SANDSTONE_STAIRS;
        vanillaBlocks[181] = Blocks.DOUBLE_STONE_SLAB2;
        vanillaBlocks[182] = Blocks.STONE_SLAB2;
        vanillaBlocks[183] = Blocks.SPRUCE_FENCE_GATE;
        vanillaBlocks[184] = Blocks.BIRCH_FENCE_GATE;
        vanillaBlocks[185] = Blocks.JUNGLE_FENCE_GATE;
        vanillaBlocks[186] = Blocks.DARK_OAK_FENCE_GATE;
        vanillaBlocks[187] = Blocks.ACACIA_FENCE_GATE;
        vanillaBlocks[188] = Blocks.SPRUCE_FENCE;
        vanillaBlocks[189] = Blocks.BIRCH_FENCE;
        vanillaBlocks[190] = Blocks.JUNGLE_FENCE;
        vanillaBlocks[191] = Blocks.DARK_OAK_FENCE;
        vanillaBlocks[192] = Blocks.ACACIA_FENCE;
        vanillaBlocks[193] = Blocks.SPRUCE_DOOR;
        vanillaBlocks[194] = Blocks.BIRCH_DOOR;
        vanillaBlocks[195] = Blocks.JUNGLE_DOOR;
        vanillaBlocks[196] = Blocks.ACACIA_DOOR;
        vanillaBlocks[197] = Blocks.DARK_OAK_DOOR;
        vanillaBlocks[198] = Blocks.END_ROD;
        vanillaBlocks[199] = Blocks.CHORUS_PLANT;
        vanillaBlocks[200] = Blocks.CHORUS_FLOWER;
        vanillaBlocks[201] = Blocks.PURPUR_BLOCK;
        vanillaBlocks[202] = Blocks.PURPUR_PILLAR;
        vanillaBlocks[203] = Blocks.PURPUR_STAIRS;
        vanillaBlocks[204] = Blocks.PURPUR_DOUBLE_SLAB;
        vanillaBlocks[205] = Blocks.PURPUR_SLAB;
        vanillaBlocks[206] = Blocks.END_BRICKS;
        vanillaBlocks[207] = Blocks.BEETROOTS;
        vanillaBlocks[208] = Blocks.GRASS_PATH;
        vanillaBlocks[209] = Blocks.END_GATEWAY;
        vanillaBlocks[210] = Blocks.REPEATING_COMMAND_BLOCK;
        vanillaBlocks[211] = Blocks.CHAIN_COMMAND_BLOCK;
        vanillaBlocks[212] = Blocks.FROSTED_ICE;
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

        bindHooks();

    }

}
