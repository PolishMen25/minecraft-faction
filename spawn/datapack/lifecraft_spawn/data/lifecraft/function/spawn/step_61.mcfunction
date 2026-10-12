# Étape 62/106 : eau
forceload add -96 -2080 -1 -1985
fill 176 69 -2128 223 74 -2065 minecraft:air replace minecraft:water
fill 176 69 -2128 223 74 -2065 minecraft:air replace minecraft:lava
forceload remove 160 -2144 239 -2049
schedule function lifecraft:spawn/step_62 10t
