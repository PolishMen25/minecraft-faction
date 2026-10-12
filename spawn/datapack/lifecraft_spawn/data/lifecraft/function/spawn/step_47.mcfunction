# Étape 48/106 : eau
forceload add 0 -2272 63 -2177
fill -80 69 -2256 -17 74 -2193 minecraft:air replace minecraft:water
fill -80 69 -2256 -17 74 -2193 minecraft:air replace minecraft:lava
forceload remove -96 -2272 -33 -2177
schedule function lifecraft:spawn/step_48 10t
