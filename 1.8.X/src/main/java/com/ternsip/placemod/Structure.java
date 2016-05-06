package com.ternsip.placemod;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.util.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

/* Holds schematic-extended information and can load/spawn/calibrate it */
public class Structure {

    public NBTTagCompound flags = new NBTTagCompound();
    public File schematicFile = null;
    public File flagFile = null;
    public File structureFile = null;

    public Structure(File schematicFile, File flagFile, File structureFile) throws IOException {

        /* Load structure if it exists */
        this.schematicFile = schematicFile;
        this.flagFile = flagFile;
        this.structureFile = structureFile;
        if (flagFile.exists() && structureFile.exists()) {
            FileInputStream fis = new FileInputStream(flagFile);
            flags = CompressedStreamTools.readCompressed(fis);
            fis.close();
            return;
        }

        /* Load schematic */
        FileInputStream fis = new FileInputStream(schematicFile);
        NBTTagCompound schematic = CompressedStreamTools.readCompressed(fis);
        fis.close();
        String materials =  schematic.getString("Materials");
        if (!materials.equals("Alpha")) {
            throw new IOException("Material of schematic is not an alpha");
        }
        int width = schematic.getShort("Width");
        int height = schematic.getShort("Height");
        int length = schematic.getShort("Length");
        if (width == 0 || height == 0 || length == 0) {
            throw new IOException("Zero size schematic");
        }
        byte[] addBlocks = schematic.getByteArray("AddBlocks");
        byte[] blocksID = schematic.getByteArray("Blocks");
        if (width * height * length != blocksID.length) {
            throw new IOException("Wrong schematic size: " + width * height * length + "/" + blocksID.length);
        }
        short[] blocks = compose(blocksID, addBlocks);

        /* Set flags */
        String path = schematicFile.getPath().toLowerCase().replace("\\", "/").replace("//", "/");
        flags.setString("Method", "Common");
        if (path.contains("/underground/")) flags.setString("Method", "Underground");
        if (path.contains("/village/")) flags.setString("Method", "Village");
        if (path.contains("/floating/")) flags.setString("Method", "Floating");
        if (path.contains("/water/")) flags.setString("Method", "Water");
        if (path.contains("/underwater/")) flags.setString("Method", "Underwater");
        flags.setInteger("Biome", Biome.detect(blocks).value);
        if (    flags.getString("Method").equalsIgnoreCase("Water") ||
                flags.getString("Method").equalsIgnoreCase("Underwater")) {
            flags.setInteger("Biome", Biome.Style.WATER.value);
        }
        flags.setShort("Width", (short) width);
        flags.setShort("Height", (short) height);
        flags.setShort("Length", (short) length);
        flags.setInteger("Lift", getLift(blocks));

        /* Generate structure over schematic */
        schematic.setByteArray("Skin", getSkin(blocks).toByteArray());

        /* Save flags */
        flagFile.getParentFile().mkdirs();
        FileOutputStream fosFlag = new FileOutputStream(flagFile);
        CompressedStreamTools.writeCompressed(flags, fosFlag);
        fosFlag.close();

        /* Save structure */
        structureFile.getParentFile().mkdirs();
        FileOutputStream fosStruct = new FileOutputStream(structureFile);
        CompressedStreamTools.writeCompressed(schematic, fosStruct);
        fosStruct.close();

    }

    void paste(World world, Posture posture, long seed) throws IOException {

        /* Load ad paste structure */
        NBTTagCompound structure;
        FileInputStream fis = new FileInputStream(structureFile);
        structure = CompressedStreamTools.readCompressed(fis);
        fis.close();
        NBTTagList entities = structure.getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
        byte[] blocksMetadata = structure.getByteArray("Data");
        final byte[] addBlocks = structure.getByteArray("AddBlocks");
        byte[] blocksID = structure.getByteArray("Blocks");
        short[] blocks = compose(blocksID, addBlocks);
        BitSet skin = BitSet.valueOf(structure.getByteArray("Skin"));

        /* Prepare tiles */
        Random random = new Random(seed);
        ArrayList<ChestGenHooks> lootTables = new ArrayList<ChestGenHooks>() {{
            add(ChestGenHooks.getInfo(ChestGenHooks.MINESHAFT_CORRIDOR));
            add(ChestGenHooks.getInfo(ChestGenHooks.PYRAMID_JUNGLE_CHEST));
            add(ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST));
            add(ChestGenHooks.getInfo(ChestGenHooks.BONUS_CHEST));
            add(ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_CORRIDOR));
            add(ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_LIBRARY));
            add(ChestGenHooks.getInfo(ChestGenHooks.PYRAMID_JUNGLE_DISPENSER));
        }};
        if (flags.getString("Method").equalsIgnoreCase("Village")) {
            lootTables.add(ChestGenHooks.getInfo(ChestGenHooks.VILLAGE_BLACKSMITH));
        }
        if (flags.getString("Biome").equalsIgnoreCase("Sand")) {
            lootTables.add(ChestGenHooks.getInfo(ChestGenHooks.PYRAMID_DESERT_CHEST));
        }

        /* Paste */
        int width = posture.getWidth();
        int height = posture.getHeight();
        int length = posture.getLength();
        int startChunkX = posture.getStartChunkX();
        int startChunkZ = posture.getStartChunkZ();
        int sizeChunkX = posture.getEndChunkX() - startChunkX + 1;
        int sizeChunkZ = posture.getEndChunkZ() - startChunkZ + 1;
        ExtendedBlockStorage[][][] storage = new ExtendedBlockStorage[sizeChunkX][sizeChunkZ][16];
        for (int cx = 0; cx < sizeChunkX; ++cx) {
            for (int cz = 0; cz < sizeChunkZ; ++cz) {
                Chunk chunk = world.getChunkFromChunkCoords(cx + startChunkX, cz + startChunkZ);
                for (int sy = 0; sy < 256; sy += 16) {
                    IBlockState state = chunk.getBlockState(new BlockPos(0, sy, 0));
                    chunk.setBlockState(new BlockPos(0, sy, 0), Blocks.log.getDefaultState());
                    chunk.setBlockState(new BlockPos(0, sy, 0), state);
                }
                ExtendedBlockStorage[] stack = chunk.getBlockStorageArray();
                System.arraycopy(stack, 0, storage[cx][cz], 0, 16);
            }
        }

        int [] blockReplaces = new int[256];
        for (int blockID = 0; blockID < 256; ++blockID) {
            blockReplaces[blockID] = blockID;
        }
        if (Decorator.balanceMode) {
            blockReplaces[Block.getIdFromBlock(Blocks.bedrock)] = Block.getIdFromBlock(Blocks.cobblestone);
            blockReplaces[Block.getIdFromBlock(Blocks.iron_block)] = Block.getIdFromBlock(Blocks.mossy_cobblestone);
            blockReplaces[Block.getIdFromBlock(Blocks.gold_block)] = Block.getIdFromBlock(Blocks.stonebrick);
            blockReplaces[Block.getIdFromBlock(Blocks.diamond_block)] = Block.getIdFromBlock(Blocks.mossy_cobblestone);
            blockReplaces[Block.getIdFromBlock(Blocks.lapis_block)] = Block.getIdFromBlock(Blocks.stonebrick);
            blockReplaces[Block.getIdFromBlock(Blocks.emerald_block)] = Block.getIdFromBlock(Blocks.mossy_cobblestone);
            blockReplaces[Block.getIdFromBlock(Blocks.wool)] = Block.getIdFromBlock(Blocks.log);
            blockReplaces[Block.getIdFromBlock(Blocks.beacon)] = Block.getIdFromBlock(Blocks.quartz_block);
        }
        if (Decorator.preventCommandBlock) {
            blockReplaces[Block.getIdFromBlock(Blocks.command_block)] = Block.getIdFromBlock(Blocks.mossy_cobblestone);
        }

        double lootChance = Decorator.lootChance;

        for (int y = 0, index = 0; y < height; ++y) {
            for (int z = 0; z < length; ++z) {
                for (int x = 0; x < width ; ++x, ++index) {
                    if (skin.get(index)) {
                        continue;
                    }
                    BlockPos blockPos = posture.getWorldPos(x, y, z);
                    if (blockPos.getY() < 0 || blockPos.getY() > 255) {
                        continue;
                    }
                    int blockID = blocks[index];
                    if (blockID > 0 && blockID < 256) {
                        blockID = blockReplaces[blockID];
                    }
                    Block block = Block.getBlockById(blockID);
                    int meta = posture.getWorldMeta(block, blocksMetadata[index]);
                    IBlockState state = block.getStateFromMeta(meta);
                    int rx = blockPos.getX() - startChunkX * 16;
                    int ry = blockPos.getY();
                    int rz = blockPos.getZ() - startChunkZ * 16;
                    ExtendedBlockStorage store = storage[rx / 16][rz / 16][ry / 16];
                    if (store != null) {
                        store.set(rx % 16, ry % 16, rz % 16, state);
                    } else {
                        world.setBlockState(blockPos, state);
                    }
                    //world.markBlockRangeForRenderUpdate(blockPos, blockPos);
                    //world.setBlockState(blockPos, state);
                    //chunk.setModified(true);
                    //world.setBlockState(blockPos, state, 2);
                    TileEntity blockTile = world.getTileEntity(blockPos);
                    if (blockTile != null && blockTile instanceof TileEntityChest && lootChance >= random.nextDouble()) {
                        ChestGenHooks info = lootTables.get(Math.abs(random.nextInt() % lootTables.size()));
                        WeightedRandomChestContent.generateChestContents(random, info.getItems(random), (TileEntityChest) blockTile, info.getCount(random));
                    }
                }
            }
        }
        world.markBlockRangeForRenderUpdate(posture.getWorldPos(0, 0, 0), posture.getWorldPos(width, height, length));

        /* Populate village */
        if (flags.getString("Method").equalsIgnoreCase("Village")) {
            int count = (int) (1 + Math.cbrt(Math.abs(posture.getWidth() * posture.getLength()))) / 2;
            int maxTries = 16 + count * count;
            for (int i = 0; i < maxTries && count > 0; ++i) {
                int xPos = posture.getPosX() + Math.abs(random.nextInt()) % posture.getSizeX();
                int yPos = posture.getPosY() + Math.abs(random.nextInt()) % posture.getSizeY();
                int zPos = posture.getPosZ() + Math.abs(random.nextInt()) % posture.getSizeZ();
                if (!world.isAirBlock(new BlockPos(xPos, yPos, zPos)) || !world.isAirBlock(new BlockPos(xPos, yPos + 1, zPos))) {
                    continue;
                }
                EntityVillager villager = new EntityVillager(world, Math.abs(random.nextInt()) % 5);
                float facing = MathHelper.wrapAngleTo180_float(random.nextFloat() * 360.0F);
                villager.setLocationAndAngles(xPos + 0.5, yPos + 0.1, zPos + 0.5, facing, 0.0F);
                world.spawnEntityInWorld(villager);
                villager.playLivingSound();
                --count;
            }
        }

        /* Spawn entities */

    }

    /* Combine all 8b-blocksID and 8b-addBlocks to 16b-block */
    private short[] compose(byte[] blocksID, byte[] addBlocks) {
        short[] blocks = new short[blocksID.length];
        for (int index = 0; index < blocksID.length; index++) {
            if ((index >> 1) >= addBlocks.length) {
                blocks[index] = (short) (blocksID[index] & 0xFF);
            } else {
                if ((index & 1) == 0) {
                    blocks[index] = (short) (((addBlocks[index >> 1] & 0x0F) << 8) + (blocksID[index] & 0xFF));
                } else {
                    blocks[index] = (short) (((addBlocks[index >> 1] & 0xF0) << 4) + (blocksID[index] & 0xFF));
                }
            }
        }
        return blocks;
    }

    /* Get structure ground level (lift) to dig it down */
    private int getLift(short[] blocks) {
        int width = flags.getShort("Width");
        int height = flags.getShort("Height");
        int length = flags.getShort("Length");
        int[][] level = new int[width][length];
        int[][] levelMax = new int[width][length];
        boolean[] liquid = Decorator.liquid;
        boolean[] soil = Decorator.soil;
        boolean dry = !flags.getString("Method").equalsIgnoreCase("Underwater");
        Posture posture = new Posture(0, 0, 0, 0, 0, 0, false, false, false, width, height, length);
        for (int index = 0; index < blocks.length; ++index) {
            if (blocks[index] >= 0 && blocks[index] < 256) {
                if (soil[blocks[index]] || (dry && liquid[blocks[index]])) {
                    level[posture.getX(index)][posture.getZ(index)] += 1;
                    levelMax[posture.getX(index)][posture.getZ(index)] = posture.getY(index) + 1;
                }
            }
        }
        long borders = 0, totals = 0;
        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < length; ++z) {
                totals += level[x][z];
                if (x == 0 || z == 0 || x == width - 1 || z == length - 1) {
                    borders += levelMax[x][z];
                }
            }
        }
        int borderLevel = (int) Math.round(borders / ((width + length) * 2.0));
        int wholeLevel = Math.round(totals / (width * length));
        return  Math.max(borderLevel, wholeLevel);
    }

    /* Generate schematic skin as bitset of possible(0) and restricted(1) to spawn blocks */
    private BitSet getSkin(short[] blocks) {
        final byte Y_INC = 1;
        final byte Y_DEC = 32;
        final byte X_INC = 4;
        final byte X_DEC = 2;
        final byte Z_INC = 16;
        final byte Z_DEC = 8;
        int width = flags.getShort("Width");
        int height = flags.getShort("Height");
        int length = flags.getShort("Length");
        Posture posture = new Posture(0, 0, 0, 0, 0, 0, false, false, false, width, height, length);
        HashSet<Integer> skinBlocks = new HashSet<Integer>();
        skinBlocks.add(Block.getIdFromBlock(Blocks.air));
        if (    flags.getString("Method").equalsIgnoreCase("Water") ||
                flags.getString("Method").equalsIgnoreCase("Underwater")) {
            skinBlocks.add(Block.getIdFromBlock(Blocks.water));
            skinBlocks.add(Block.getIdFromBlock(Blocks.flowing_water));
        }
        Queue<Integer> indexQueue = new LinkedList<Integer>();
        byte[] clipped = new byte[width * height * length];
        BitSet working = new BitSet(width * height * length);
        BitSet skin = new BitSet(width * height * length);
        for (int dir = 0; dir <= 1; ++dir) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    int index = dir == 0 ? posture.getIndex(0, y, z) : posture.getIndex(width - 1, y, z);
                    int flag = dir == 0 ? X_INC : X_DEC;
                    if (skinBlocks.contains((int) blocks[index])) {
                        if (!working.get(index)) {
                            indexQueue.add(index);
                            working.set(index);
                        }
                        clipped[index] |= flag;
                        skin.set(index);
                    }
                }
                for (int x = 0; x < width; ++x) {
                    int index = dir == 0 ? posture.getIndex(x, y, 0) : posture.getIndex(x, y, length - 1);
                    int flag = dir == 0 ? Z_INC : Z_DEC;
                    if (skinBlocks.contains((int) blocks[index])) {
                        if (!working.get(index)) {
                            indexQueue.add(index);
                            working.set(index);
                        }
                        clipped[index] |= flag;
                        skin.set(index);
                    }
                }
            }
        }
        byte[] headID = {Y_INC, Y_DEC, X_INC, X_DEC, Z_INC, Z_DEC};
        byte[] backID = {Y_DEC, Y_INC, X_DEC, X_INC, Z_DEC, Z_INC};
        while (!indexQueue.isEmpty()) {
            int index = indexQueue.remove();
            working.clear(index);
            int x = posture.getX(index);
            int y = posture.getY(index);
            int z = posture.getZ(index);
            int[] idx = {
                posture.getIndex(x, y + 1, z),
                posture.getIndex(x, y - 1, z),
                posture.getIndex(x + 1, y, z),
                posture.getIndex(x - 1, y, z),
                posture.getIndex(x, y, z + 1),
                posture.getIndex(x, y, z - 1)
            };
            boolean[] cond = {
                    y < height - 1,
                    y > 0,
                    x < width - 1,
                    x > 0,
                    z < length - 1,
                    z > 0
            };
            for (int k = 0; k < 6; ++k) {
                int next = idx[k];
                if (cond[k] &&
                        (clipped[index] & headID[k]) > 0 &&
                        (clipped[next] & headID[k]) == 0 &&
                        (clipped[next] & backID[k]) == 0 &&
                        (skinBlocks.contains((int) blocks[next]))) {
                    if (!working.get(next)) {
                        working.set(next);
                        indexQueue.add(next);
                    }
                    clipped[next] |= headID[k];
                    skin.set(next);
                }
            }
        }
        for (int index = 0; index < skin.size(); ++index) {
            if (skin.get(index)) {
                int x = posture.getX(index);
                int y = posture.getY(index);
                int z = posture.getZ(index);
                while (y-- > 0) {
                    int next = posture.getIndex(x, y, z);
                    if (!skin.get(next) && skinBlocks.contains((int) blocks[next])) {
                        skin.set(next);
                    } else {
                        break;
                    }
                }
            }
        }
        return skin;
    }


}
