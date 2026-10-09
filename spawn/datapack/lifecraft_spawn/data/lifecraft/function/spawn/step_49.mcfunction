# Étape 50/106 : eau
forceload add 128 -2272 191 -2177
fill 48 69 -2256 111 74 -2193 minecraft:air replace minecraft:water
fill 48 69 -2256 111 74 -2193 minecraft:air replace minecraft:lava
forceload remove 32 -2272 95 -2177
say Spawn : 47 %
schedule function lifecraft:spawn/step_50 10t
