package com.ternsip.placemod;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

/* Biome-style determination class */
class Biome {

    /* Possible biome styles. Must completely cover all biomes */
    enum Style {

        COMMON (0x00, "COMMON"),
        SNOW (0x01, "SNOW"),
        NETHER (0x02, "NETHER"),
        SAND (0x03, "SAND"),
        MUSHROOM (0x04, "MUSHROOM"),
        MESA (0x05, "MESA"),
        END (0x07, "END"),
        WATER (0x08, "WATER");

        public final int value;
        public final String name;

        Style(int value, String name) {
            this.value = value;
            this.name = name;
        }

        /* Biome ID to Biome.Style */
        public static Style valueOf(int value) {
            for (Style sample : Style.values()) {
                if (sample.value == value) {
                    return sample;
                }
            }
            return COMMON;
        }

    }

    /* Detect Biome.Style by given set of blocks */
    static Style detect(short[] blocks) {

        /* Counts [0..SIZE] of each vanilla blocks */
        double[] counts = new double[256];
        for (short blockID : blocks) {
            if (blockID >= 0 && blockID < 256) {
                counts[blockID] += 1.0;
            }
        }

        /* Frequency [0..1] of each vanilla block, exclusive air */
        double[] frequency = new double[256];
        double notAir = 1 + blocks.length - counts[0];
        frequency[0] /= counts[0] / blocks.length;
        for (int i = 1; i < 256; ++i) {
            frequency[i] = counts[i] / notAir;
        }

        /* Affective blocks for each Biome.Style, except COMMON */
        Block[] snow = new Block[]{Blocks.snow_layer, Blocks.snow, Blocks.ice};
        Block[] nether = new Block[]{Blocks.netherrack, Blocks.soul_sand, Blocks.nether_brick, Blocks.nether_brick_fence, Blocks.nether_brick_stairs, Blocks.obsidian};
        Block[] sand = new Block[]{Blocks.sand, Blocks.sandstone, Blocks.sandstone_stairs};
        Block[] mushroom = new Block[]{Blocks.red_mushroom_block, Blocks.brown_mushroom_block};
        Block[] mesa = new Block[]{Blocks.stained_hardened_clay, Blocks.hardened_clay, Blocks.clay};
        Block[] end = new Block[]{Blocks.end_stone};
        if (accumulate(snow, counts) > 8.5) return Style.SNOW;
        if (accumulate(end, frequency) > 0.25) return  Style.END;
        if (accumulate(nether, frequency) > 0.25) return  Style.NETHER;
        if (accumulate(sand, frequency) > 0.25) return  Style.SAND;
        if (accumulate(mesa, frequency) > 0.25) return  Style.MESA;
        if (accumulate(mushroom, frequency) > 0.1) return  Style.MUSHROOM;
        return  Style.COMMON;

    }

    /* Sum of blocks array values */
    private static double accumulate(Block[] blocks, double[] array) {
        double sum = 0;
        for (Block block : blocks) {
            sum += array[Block.getIdFromBlock(block)];
        }
        return sum;
    }

    private static boolean isBiomeSnow(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biome.getEnableSnow() ||
                biomeName.contains("Frozen".toLowerCase()) ||
                biomeName.contains("Ice".toLowerCase()) ||
                biomeName.contains("Cold".toLowerCase()) ||
                biomeName.contains("Alps".toLowerCase()) ||
                biomeName.contains("Arctic".toLowerCase()) ||
                biomeName.contains("Frost".toLowerCase()) ||
                biomeName.contains("Icy".toLowerCase()) ||
                biomeName.contains("Snow".toLowerCase()) ||
                biomeName.contains("Coniferous".toLowerCase()) ||
                biomeName.contains("Tundra".toLowerCase()) ||
                biomeName.contains("Glacier".toLowerCase());
    }

    private static boolean isBiomeSand(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biomeName.contains("Desert".toLowerCase()) ||
                biomeName.contains("Canyon".toLowerCase()) ||
                biomeName.contains("Dune".toLowerCase()) ||
                biomeName.contains("Beach".toLowerCase()) ||
                biomeName.contains("Mangrove".toLowerCase()) ||
                biomeName.contains("Oasis".toLowerCase()) ||
                biomeName.contains("Xeric".toLowerCase());
    }

    private static boolean isBiomeMesa(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biomeName.contains("Mesa".toLowerCase()) ||
                biomeName.contains("Badlands".toLowerCase()) ||
                biomeName.contains("LushDesert".toLowerCase());
    }

    private static boolean isBiomeMushroom(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biomeName.contains("Roofed".toLowerCase()) ||
                biomeName.contains("Mushroom".toLowerCase()) ||
                biomeName.contains("Fungi".toLowerCase());
    }

    private static boolean isBiomeWater(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biomeName.contains("Ocean".toLowerCase()) ||
                biomeName.contains("Coral".toLowerCase()) ||
                biomeName.contains("Pond".toLowerCase()) ||
                biomeName.contains("Kelp".toLowerCase()) ||
                biomeName.contains("River".toLowerCase());
    }

    private static boolean isBiomeNether(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return  biomeName.contains("Hell".toLowerCase()) ||
                biomeName.contains("Bloody".toLowerCase()) ||
                biomeName.contains("Boneyard".toLowerCase()) ||
                biomeName.contains("Corrupted".toLowerCase()) ||
                biomeName.contains("Inferno".toLowerCase()) ||
                biomeName.contains("Chasm".toLowerCase()) ||
                biomeName.contains("Undergarden".toLowerCase()) ||
                biomeName.contains("Nether".toLowerCase());
    }

    private static boolean isBiomeEnd(BiomeGenBase biome) {
        String biomeName = biome.getBiomeName().toLowerCase().replace(" ", "");
        return biomeName.contains("TheEnd".toLowerCase());
    }

    /* Determine Biome.Style by given BiomeGenBase */
    static Style determine(BiomeGenBase biome) {
        if (isBiomeEnd(biome)) return Style.END;
        if (isBiomeNether(biome)) return Style.NETHER;
        if (isBiomeMesa(biome)) return Style.MESA;
        if (isBiomeMushroom(biome)) return Style.MUSHROOM;
        if (isBiomeSand(biome)) return Style.SAND;
        if (isBiomeSnow(biome)) return Style.SNOW;
        if (isBiomeWater(biome)) return Style.WATER;
        return Style.COMMON;
    }


}
