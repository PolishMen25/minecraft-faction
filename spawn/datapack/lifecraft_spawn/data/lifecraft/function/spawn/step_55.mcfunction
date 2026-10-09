# Étape 56/106 : eau
forceload add 192 -2208 239 -2113
fill 112 69 -2192 175 74 -2129 minecraft:air replace minecraft:water
fill 112 69 -2192 175 74 -2129 minecraft:air replace minecraft:lava
forceload remove 96 -2208 159 -2113
schedule function lifecraft:spawn/step_56 10t
