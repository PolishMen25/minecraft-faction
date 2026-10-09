# Étape 41/106 : plateau
forceload add 64 -2016 127 -1953
fill -16 50 -2000 15 59 -1969 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2000 15 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2000 15 68 -1969 minecraft:grass_block
fill -16 69 -2000 15 89 -1969 minecraft:air
fill -16 90 -2000 15 110 -1969 minecraft:air
fill 16 50 -2000 47 59 -1969 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2000 47 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2000 47 68 -1969 minecraft:grass_block
fill 16 69 -2000 47 89 -1969 minecraft:air
fill 16 90 -2000 47 110 -1969 minecraft:air
forceload remove -32 -2016 31 -1953
schedule function lifecraft:spawn/step_41 10t
