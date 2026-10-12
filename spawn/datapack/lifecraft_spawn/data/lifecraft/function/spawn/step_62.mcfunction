# Étape 63/106 : eau
forceload add 0 -2080 63 -1985
fill -80 69 -2064 -17 74 -2001 minecraft:air replace minecraft:water
fill -80 69 -2064 -17 74 -2001 minecraft:air replace minecraft:lava
forceload remove -96 -2080 -33 -1985
schedule function lifecraft:spawn/step_63 10t
