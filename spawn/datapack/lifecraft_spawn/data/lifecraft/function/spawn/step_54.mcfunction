# Étape 55/106 : eau
forceload add 128 -2208 191 -2113
fill 48 69 -2192 111 74 -2145 minecraft:air replace minecraft:water
fill 48 69 -2192 111 74 -2145 minecraft:air replace minecraft:lava
fill 109 69 -2144 111 74 -2129 minecraft:air replace minecraft:water
fill 109 69 -2144 111 74 -2129 minecraft:air replace minecraft:lava
forceload remove 32 -2208 95 -2113
schedule function lifecraft:spawn/step_55 10t
