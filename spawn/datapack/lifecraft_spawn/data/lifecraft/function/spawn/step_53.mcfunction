# Étape 54/106 : eau
forceload add 64 -2208 127 -2113
fill -16 69 -2192 47 74 -2145 minecraft:air replace minecraft:water
fill -16 69 -2192 47 74 -2145 minecraft:air replace minecraft:lava
fill -16 69 -2144 44 74 -2129 minecraft:air replace minecraft:water
fill -16 69 -2144 44 74 -2129 minecraft:air replace minecraft:lava
forceload remove -32 -2208 31 -2113
schedule function lifecraft:spawn/step_54 10t
