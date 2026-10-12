# Étape 64/106 : eau
forceload add 64 -2080 127 -1985
fill -16 69 -2064 47 74 -2001 minecraft:air replace minecraft:water
fill -16 69 -2064 47 74 -2001 minecraft:air replace minecraft:lava
forceload remove -32 -2080 31 -1985
schedule function lifecraft:spawn/step_64 10t
