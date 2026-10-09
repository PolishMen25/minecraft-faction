# Étape 36/106 : plateau
forceload add 64 -2048 127 -1985
fill -16 50 -2032 15 59 -2001 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2032 15 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2032 15 68 -2001 minecraft:grass_block
fill -16 69 -2032 15 89 -2001 minecraft:air
fill -16 90 -2032 15 110 -2001 minecraft:air
fill 16 50 -2032 47 59 -2001 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2032 47 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2032 47 68 -2001 minecraft:grass_block
fill 16 69 -2032 47 89 -2001 minecraft:air
fill 16 90 -2032 47 110 -2001 minecraft:air
forceload remove -32 -2048 31 -1985
schedule function lifecraft:spawn/step_36 10t
