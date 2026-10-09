# Étape 66/106 : eau
forceload add 192 -2080 239 -1985
fill 112 69 -2064 175 74 -2001 minecraft:air replace minecraft:water
fill 112 69 -2064 175 74 -2001 minecraft:air replace minecraft:lava
forceload remove 96 -2080 159 -1985
schedule function lifecraft:spawn/step_66 10t
