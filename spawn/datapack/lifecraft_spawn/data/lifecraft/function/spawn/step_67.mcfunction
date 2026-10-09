# Étape 68/106 : eau
forceload add 0 -2016 63 -1937
fill -80 69 -2000 -17 74 -1953 minecraft:air replace minecraft:water
fill -80 69 -2000 -17 74 -1953 minecraft:air replace minecraft:lava
forceload remove -96 -2016 -33 -1937
schedule function lifecraft:spawn/step_68 10t
