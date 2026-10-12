# Étape 52/106 : eau
forceload add -96 -2208 -1 -2113
fill 176 69 -2256 223 74 -2193 minecraft:air replace minecraft:water
fill 176 69 -2256 223 74 -2193 minecraft:air replace minecraft:lava
forceload remove 160 -2272 239 -2177
schedule function lifecraft:spawn/step_52 10t
