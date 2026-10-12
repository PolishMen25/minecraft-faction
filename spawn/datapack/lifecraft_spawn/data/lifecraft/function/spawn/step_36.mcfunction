# Étape 37/106 : plateau
forceload add 128 -2048 191 -1985
fill 48 50 -2032 79 59 -2001 minecraft:stone replace #minecraft:replaceable
fill 48 60 -2032 79 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill 48 68 -2032 79 68 -2001 minecraft:grass_block
fill 48 69 -2032 79 89 -2001 minecraft:air
fill 48 90 -2032 79 110 -2001 minecraft:air
fill 80 50 -2032 111 59 -2001 minecraft:stone replace #minecraft:replaceable
fill 80 60 -2032 111 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill 80 68 -2032 111 68 -2001 minecraft:grass_block
fill 80 69 -2032 111 89 -2001 minecraft:air
fill 80 90 -2032 111 110 -2001 minecraft:air
forceload remove 32 -2048 95 -1985
schedule function lifecraft:spawn/step_37 10t
