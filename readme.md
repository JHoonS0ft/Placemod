Placemod (Fast Schematic Spawning System) for Minecraft

Author Ternsip

Placemod adds new extraordinary very optimized and fast structure generator in the world.
It spawns different structures based on schematics in the world on the server side. 
During the world exploration you can find various discoveries neatly generated from schematics. 
Produced structures can reach gigantic proportions, since schematics is not limited by size.
Any user can add arbitrary schematic to make it possible automatically appear in the world. 
Mod config provides the ability to adjust spawn rate. It is possible to generate villages constructed from schematic sets. 
I have collected more than 800+ great schematics and include them to standard mod releases.


http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2479524-placemod-fast-schematic-spawning-system


http://minecraft.curseforge.com/projects/placemod


Changelog:
World structure generator highly optimized since 1.4 version.
Added precalulating structure flags and skin.
Spawn system nicely simplified.
Fixed Block ID bug (gave ArrayIndexOutOfBounds Exception when you tried to run with other mods.).
Fixed mod version.
Fixed random world seed dependence.
Relocated configuration file, now config locates in config/placemod.cfg.
Fixed null pointer exception rises when world won't generate chunk.
Changed spawn rarity to 0.005.
Improved structure skinning. 

Version 2.1 Major Update:
Assigned individual cluster/structure  rarity for each biome type.
Add saturation function ratios to config.
Fixed bug that makes block not rendered when large structures spawns.
Fixed nether spawn height (Before nether was not able to spawn structures properly - only on top of hell).
Fixed underwater structures lifting.

Version 3.1 2900 balance, diversity update:
Add a few options to config:
balanceMode - Replace rich blocks to poor
preventCommandBlock - Prevent command block for spawning
roughnessFactor - Multiplier of minimal acceptable roughness
lootChance - Chest loot chance [0..1]
forceLift - Pull out structure from the ground and lift up (recommended 0)

Version 3.2:
Now mob spawners appears with random mob inside cage (not only pigs).
Add function to disable mob spawners.
Fixed mushroom rotation bug. Now mushroom cap blocks puts correctly.
Fixed rails and portals metadata rotation bugs.
Added block-id disentanglement vanilla blocks to reveal appropriate schematic block.


Version 3.3
underground becomes fully underground
add vanilla only blocks options
villa, town now spawns villagers
fix illegal argument block state exception

Version 3.4
Fixed underwater floodfill
Fixed structures dirt/stone floodfill
Add new chest loot control for 1.7.x 1.8.x


Version 3.6
Add config function LOAD_OUTPUT to disable console load output
Small fix for 1.7.10 with icons
Add support for 1.10.2

Version 3.7
Add config function to control allowed dimensions
Add support for 1.10