# Étape 70/106 : eau
forceload add 128 -2016 191 -1937
fill 48 69 -2000 111 74 -1953 minecraft:air replace minecraft:water
fill 48 69 -2000 111 74 -1953 minecraft:air replace minecraft:lava
forceload remove 32 -2016 95 -1937
say Spawn : 66 %
schedule function lifecraft:spawn/step_70 10t
