# Étape 51/106 : eau
forceload add 192 -2272 239 -2177
fill 112 69 -2256 175 74 -2193 minecraft:air replace minecraft:water
fill 112 69 -2256 175 74 -2193 minecraft:air replace minecraft:lava
forceload remove 96 -2272 159 -2177
schedule function lifecraft:spawn/step_51 10t
