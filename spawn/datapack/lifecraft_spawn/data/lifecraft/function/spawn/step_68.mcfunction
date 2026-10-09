# Étape 69/106 : eau
forceload add 64 -2016 127 -1937
fill -16 69 -2000 47 74 -1953 minecraft:air replace minecraft:water
fill -16 69 -2000 47 74 -1953 minecraft:air replace minecraft:lava
forceload remove -32 -2016 31 -1937
schedule function lifecraft:spawn/step_69 10t
