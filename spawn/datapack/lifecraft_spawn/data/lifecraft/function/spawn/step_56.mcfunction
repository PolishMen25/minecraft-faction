# Étape 57/106 : eau
forceload add -96 -2144 -1 -2049
fill 176 69 -2192 223 74 -2129 minecraft:air replace minecraft:water
fill 176 69 -2192 223 74 -2129 minecraft:air replace minecraft:lava
forceload remove 160 -2208 239 -2113
schedule function lifecraft:spawn/step_57 10t
