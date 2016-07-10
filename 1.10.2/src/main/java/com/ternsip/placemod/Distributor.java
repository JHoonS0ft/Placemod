package com.ternsip.placemod;

import java.util.*;

/* Assign array of clusters to biome map and distribute relative spawn chance */
class Distributor {

    private HashMap<Integer, ArrayList<Cluster>> biomesClusters = new HashMap<Integer, ArrayList<Cluster>>();

    /* Constructs distributor using chaotic cluster array */
    Distributor(ArrayList<Cluster> clusters) {

        /* Assign config ratios */
        double A = Decorator.ratioA;
        double B = Decorator.ratioB;
        boolean strictly = Decorator.strictMode;

        /* Put all clusters appropriate biome entry */
        int common_bid = Biome.Style.COMMON.value;
        for (Cluster cluster : clusters) {
            HashMap<Integer, Integer> biomeStyles = new HashMap<Integer, Integer>();
            for (Structure structure : cluster.getStructures()) {
                int bid = structure.flags.getInteger("Biome");
                biomeStyles.put(bid, biomeStyles.getOrDefault(bid, 0) + 1);
                if (!strictly && structure.flags.getString("Method").equalsIgnoreCase("Floating")) {
                    biomeStyles.put(common_bid, biomeStyles.getOrDefault(common_bid, 0) + 1);
                }
            }
            /* Put cluster to biomes that have maximal similarity. May be one, two or more */
            int maxValue = Collections.max(biomeStyles.values());
            for (Map.Entry<Integer, Integer> entry : biomeStyles.entrySet()) {
                if (entry.getValue() == maxValue) {
                    int bid = entry.getKey();
                    final Cluster clusterCopy = new Cluster(cluster);
                    if (biomesClusters.containsKey(bid)) {
                        biomesClusters.get(bid).add(clusterCopy);
                    } else {
                        biomesClusters.put(bid, new ArrayList<Cluster>() {{add(clusterCopy);}});
                    }
                }
            }
        }

        /* Distribute and normalize clusters weight deviations of average value */
        for (ArrayList<Cluster> biomeClusters : biomesClusters.values()) {
            long blocksTotal = 0;
            for (Cluster cluster : biomeClusters) {
                for (Structure structure : cluster.getStructures()) {
                    int width = structure.flags.getShort("Width");
                    int height = structure.flags.getShort("Height");
                    int length = structure.flags.getShort("Length");
                    blocksTotal += width * height * length;
                }
            }
            double averageBlocks = blocksTotal / biomeClusters.size();
            double chancesSum = 0;
            for (Cluster cluster : biomeClusters) {
                long weight = 0;
                for (Structure structure : cluster.getStructures()) {
                    int width = structure.flags.getShort("Width");
                    int height = structure.flags.getShort("Height");
                    int length = structure.flags.getShort("Length");
                    weight += width * height * length;
                }
                /* Logistic smooth saturation function  */
                /* f(x) = 2 / (1 + e ^ (-A * x ^ B)) - 1, default A = 1, B = 0.5 */
                double saturation = 2.0 / (1.0 + Math.exp(-A * Math.pow(weight / averageBlocks, B))) - 1.0;
                double chance = 1.0 - saturation;
                cluster.setChance(chance);
                chancesSum += chance;
            }
            /* Make sum of all chances = 1 : (a+b+c)/(a+b+c) = 1 */
            for (Cluster cluster : biomeClusters) {
                cluster.setChance(cluster.getChance() / chancesSum);
            }
        }
    }

    /* Get all clusters that can be spawned in given biome type */
    ArrayList<Cluster> getClusters(Biome.Style biome) {
        return biomesClusters.getOrDefault(biome.value, new ArrayList<Cluster>());
    }

}
