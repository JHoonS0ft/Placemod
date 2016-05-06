package com.ternsip.placemod;


import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Random;

/* Calibrates posture in the world */
public class Calibrator {

    private double variance = 0; // Characterizes roughness
    private double varianceUnderLiquid = 0; // Characterizes roughness under liquids
    private double averageHeight = 0; // Average blocks height
    private double averageHeightUnderLiquid = 0; // Average blocks height under liquids
    private int maxHeight = 0; // Max block height in region
    private int maxHeightUnderLiquid = 0; // Max block height under liquids
    private int minHeight = 255; // Min block height in region
    private int minHeightUnderLiquid = 255; // Min block height under liquids

    public Calibrator(World world, Posture posture) throws IOException {
        double totalHeight = 0;
        double squareHeightSum = 0;
        double totalHeightUnderLiquid = 0;
        double squareHeightSumUnderLiquid = 0;
        boolean[] overlook = Decorator.overlook;
        boolean[] liquid = Decorator.liquid;
        int sx = posture.getPosX();
        int sz = posture.getPosZ();
        int ex = posture.getEndX() - 1;
        int ez = posture.getEndZ() - 1;
        String dimName = world.provider.getDimensionName();
        int startHeight = 255;
        startHeight = dimName.equalsIgnoreCase("Nether") ? 63 : startHeight;
        startHeight = dimName.equalsIgnoreCase("End") ? 127 : startHeight;
        double area = 0;
        for (int wx = sx; wx <= ex; ++wx) {
            for (int wz = sz; wz <= ez; ++wz) {
                if (wx != sx && wx != ex && wz != sz && wz != ez) {
                    continue;
                }
                area++;
                int hg = startHeight;
                while (hg > 0) {
                    int blockID = Block.getIdFromBlock(world.getBlock(wx, hg, wz));
                    if (blockID >= 0 && blockID < 256 && overlook[blockID]) {
                        --hg;
                    } else {
                        break;
                    }
                }
                maxHeight = Math.max(maxHeight, hg + 1);
                minHeight = Math.min(minHeight, hg + 1);
                totalHeight += hg + 1;
                squareHeightSum += (hg + 1) * (hg + 1);
                while (hg > 0) {
                    int blockID = Block.getIdFromBlock(world.getBlock(wx, hg, wz));
                    if (blockID >= 0 && blockID < 256 && (overlook[blockID] || liquid[blockID])) {
                        --hg;
                    } else {
                        break;
                    }
                }
                maxHeightUnderLiquid = Math.max(maxHeightUnderLiquid, hg + 1);
                minHeightUnderLiquid = Math.min(minHeightUnderLiquid, hg + 1);
                totalHeightUnderLiquid += hg + 1;
                squareHeightSumUnderLiquid += (hg + 1) * (hg + 1);
            }
        }
        if (area <= 0) {
            throw new IOException("Incorrect calibration area: " + area);
        }
        minHeight = Math.min(minHeight, maxHeight);
        minHeightUnderLiquid = Math.min(minHeightUnderLiquid, maxHeightUnderLiquid);
        variance = area > 1 ? Math.abs((squareHeightSum - (totalHeight * totalHeight) / area) / (area - 1)) : 0;
        varianceUnderLiquid = area > 1 ? Math.abs((squareHeightSumUnderLiquid - (totalHeightUnderLiquid * totalHeightUnderLiquid) / area) / (area - 1)) : 0;
        averageHeight = totalHeight / area;
        averageHeightUnderLiquid = totalHeightUnderLiquid / area;
    }

    /* Calibrates structure height. Throw exceptions if it impossible. */
    public int calibrate(Structure structure, long seed) throws IOException {
        Random random = new Random(seed);
        double liquidHeight = averageHeight - averageHeightUnderLiquid;
        DecimalFormat decimal = new DecimalFormat("######0.00");
        int height = structure.flags.getShort("Height");
        double lift = structure.flags.getInteger("Lift");
        boolean water = structure.flags.getString("Method").equalsIgnoreCase("Water");
        boolean underwater = structure.flags.getString("Method").equalsIgnoreCase("Underwater");
        boolean floating = structure.flags.getString("Method").equalsIgnoreCase("Floating");
        boolean underground = structure.flags.getString("Method").equalsIgnoreCase("Underground");
        int sy;
        double roughness = Math.sqrt(variance);
        double underLiquidRoughness = Math.sqrt(varianceUnderLiquid);
        if (water) {
            if (roughness > 3.0 * Decorator.roughnessFactor) {
                throw new IOException("Rough water: " + decimal.format(roughness));
            }
            if (liquidHeight < 6.0) {
                throw new IOException("Too shallow: " + decimal.format(liquidHeight));
            }
            sy = (int) (averageHeight - roughness);
        } else {
            if (underwater) {
                if (underLiquidRoughness > (height / 3.0 + 2) * Decorator.roughnessFactor) {
                    throw new IOException("Rough bottom: " + decimal.format(underLiquidRoughness));
                }
                if (liquidHeight < height * 0.35 && liquidHeight + lift < height) {
                    throw new IOException("Too shallow: " + decimal.format(liquidHeight));
                }
            } else if (!floating && !underground) {
                if (underLiquidRoughness > (height / 8.0 + 2) * Decorator.roughnessFactor) {
                    throw new IOException("Rough area: " + decimal.format(underLiquidRoughness));
                }
                if (liquidHeight > 1.5) {
                    throw new IOException("Too deep: " + decimal.format(liquidHeight));
                }
            }
            sy = (int) (averageHeightUnderLiquid - underLiquidRoughness);
        }
        if (floating) {
            sy += 8 + height + random.nextInt() % (height / 2);
        } else if (underground) {
            sy = 30 + random.nextInt() % 25;
        } else {
            sy -= lift;
            sy += Decorator.forceLift;
        }
        if (sy < 4 || sy > 250) {
            throw new IOException("Abnormal height: " + sy);
        }
        return sy;
    }

    public double getVariance() {
        return variance;
    }

    public double getVarianceUnderLiquid() {
        return varianceUnderLiquid;
    }

    public double getAverageHeight() {
        return averageHeight;
    }

    public double getAverageHeightUnderLiquid() {
        return averageHeightUnderLiquid;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getMaxHeightUnderLiquid() {
        return maxHeightUnderLiquid;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMinHeightUnderLiquid() {
        return minHeightUnderLiquid;
    }

}
