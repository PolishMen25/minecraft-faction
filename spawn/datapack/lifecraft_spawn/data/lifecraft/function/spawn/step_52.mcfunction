# Étape 53/106 : eau
forceload add 0 -2208 63 -2113
fill -80 69 -2192 -17 74 -2129 minecraft:air replace minecraft:water
fill -80 69 -2192 -17 74 -2129 minecraft:air replace minecraft:lava
forceload remove -96 -2208 -33 -2113
schedule function lifecraft:spawn/step_53 10t
