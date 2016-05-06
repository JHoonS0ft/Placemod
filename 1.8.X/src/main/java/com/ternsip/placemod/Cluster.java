package com.ternsip.placemod;

import java.util.ArrayList;

/* Cluster is spawn unit. All structures must be spawned simultaneously. */
class Cluster {

    private double chance = 0;
    private String name = "";
    private ArrayList<Structure> structures = new ArrayList<Structure>();

    /* Construct empty cluster with given name */
    public Cluster(String name) {
        this.name = name;
    }

    /* Construct new cluster as copy of another */
    public Cluster(Cluster cluster) {
        this.chance = cluster.getChance();
        this.name = cluster.getName();
        this.structures.addAll(cluster.getStructures());
    }

    /* Add given structure to cluster */
    public Cluster add(Structure structure) {
        structures.add(structure);
        return this;
    }

    /* Add all structures to cluster */
    public Cluster add(ArrayList<Structure> structures) {
        this.structures.addAll(structures);
        return this;
    }

    void setChance(double chance) {
        this.chance = chance;
    }

    double getChance() {
        return chance;
    }

    public String getName() {
        return name;
    }

    ArrayList<Structure> getStructures() {
        return structures;
    }

}
