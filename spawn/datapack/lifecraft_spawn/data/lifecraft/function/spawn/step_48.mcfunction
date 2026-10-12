# Étape 49/106 : eau
forceload add 64 -2272 127 -2177
fill -16 69 -2256 47 74 -2193 minecraft:air replace minecraft:water
fill -16 69 -2256 47 74 -2193 minecraft:air replace minecraft:lava
forceload remove -32 -2272 31 -2177
schedule function lifecraft:spawn/step_49 10t
