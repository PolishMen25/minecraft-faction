# Étape 42/106 : plateau
forceload add 128 -2016 191 -1953
fill 48 50 -2000 79 59 -1969 minecraft:stone replace #minecraft:replaceable
fill 48 60 -2000 79 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill 48 68 -2000 79 68 -1969 minecraft:grass_block
fill 48 69 -2000 79 89 -1969 minecraft:air
fill 48 90 -2000 79 110 -1969 minecraft:air
fill 80 50 -2000 111 59 -1969 minecraft:stone replace #minecraft:replaceable
fill 80 60 -2000 111 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill 80 68 -2000 111 68 -1969 minecraft:grass_block
fill 80 69 -2000 111 89 -1969 minecraft:air
fill 80 90 -2000 111 110 -1969 minecraft:air
forceload remove 32 -2016 95 -1953
schedule function lifecraft:spawn/step_42 10t
