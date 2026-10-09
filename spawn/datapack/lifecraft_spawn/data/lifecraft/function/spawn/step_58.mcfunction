# Étape 59/106 : eau
forceload add 64 -2144 127 -2049
fill -16 69 -2080 47 74 -2065 minecraft:air replace minecraft:water
fill -16 69 -2080 47 74 -2065 minecraft:air replace minecraft:lava
fill -16 69 -2128 44 74 -2081 minecraft:air replace minecraft:water
fill -16 69 -2128 44 74 -2081 minecraft:air replace minecraft:lava
forceload remove -32 -2144 31 -2049
schedule function lifecraft:spawn/step_59 10t
