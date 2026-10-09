# Étape 65/106 : eau
forceload add 128 -2080 191 -1985
fill 48 69 -2064 111 74 -2001 minecraft:air replace minecraft:water
fill 48 69 -2064 111 74 -2001 minecraft:air replace minecraft:lava
forceload remove 32 -2080 95 -1985
schedule function lifecraft:spawn/step_65 10t
