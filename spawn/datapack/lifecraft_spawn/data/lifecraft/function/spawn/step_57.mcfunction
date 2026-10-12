# Étape 58/106 : eau
forceload add 0 -2144 63 -2049
fill -80 69 -2128 -17 74 -2065 minecraft:air replace minecraft:water
fill -80 69 -2128 -17 74 -2065 minecraft:air replace minecraft:lava
forceload remove -96 -2144 -33 -2049
schedule function lifecraft:spawn/step_58 10t
