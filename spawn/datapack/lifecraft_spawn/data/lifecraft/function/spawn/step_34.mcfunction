# Étape 35/106 : plateau
forceload add 0 -2048 63 -1985
fill -80 50 -2032 -49 59 -2001 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2032 -49 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2032 -49 68 -2001 minecraft:grass_block
fill -80 69 -2032 -49 89 -2001 minecraft:air
fill -80 90 -2032 -49 110 -2001 minecraft:air
fill -48 50 -2032 -17 59 -2001 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2032 -17 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2032 -17 68 -2001 minecraft:grass_block
fill -48 69 -2032 -17 89 -2001 minecraft:air
fill -48 90 -2032 -17 110 -2001 minecraft:air
forceload remove -96 -2048 -33 -1985
schedule function lifecraft:spawn/step_35 10t
