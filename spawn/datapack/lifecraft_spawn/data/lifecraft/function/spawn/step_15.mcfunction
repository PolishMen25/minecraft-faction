# Étape 16/106 : plateau
forceload add 0 -2176 63 -2113
fill -80 50 -2160 -49 59 -2129 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2160 -49 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2160 -49 68 -2129 minecraft:grass_block
fill -80 69 -2160 -49 89 -2129 minecraft:air
fill -80 90 -2160 -49 110 -2129 minecraft:air
fill -48 50 -2160 -17 59 -2129 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2160 -17 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2160 -17 68 -2129 minecraft:grass_block
fill -48 69 -2160 -17 89 -2129 minecraft:air
fill -48 90 -2160 -17 110 -2129 minecraft:air
forceload remove -96 -2176 -33 -2113
schedule function lifecraft:spawn/step_16 10t
