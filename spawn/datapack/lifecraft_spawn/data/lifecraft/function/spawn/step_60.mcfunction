# Étape 61/106 : eau
forceload add 192 -2144 239 -2049
fill 112 69 -2128 175 74 -2065 minecraft:air replace minecraft:water
fill 112 69 -2128 175 74 -2065 minecraft:air replace minecraft:lava
forceload remove 96 -2144 159 -2049
schedule function lifecraft:spawn/step_61 10t
