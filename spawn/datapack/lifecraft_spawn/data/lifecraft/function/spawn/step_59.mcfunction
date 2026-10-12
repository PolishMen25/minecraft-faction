# Étape 60/106 : eau
forceload add 128 -2144 191 -2049
fill 48 69 -2080 111 74 -2065 minecraft:air replace minecraft:water
fill 48 69 -2080 111 74 -2065 minecraft:air replace minecraft:lava
fill 109 69 -2128 111 74 -2081 minecraft:air replace minecraft:water
fill 109 69 -2128 111 74 -2081 minecraft:air replace minecraft:lava
forceload remove 32 -2144 95 -2049
say Spawn : 56 %
schedule function lifecraft:spawn/step_60 10t
