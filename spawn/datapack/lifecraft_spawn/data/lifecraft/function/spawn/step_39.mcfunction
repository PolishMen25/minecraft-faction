# Étape 40/106 : plateau
forceload add 0 -2016 63 -1953
fill -80 50 -2000 -49 59 -1969 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2000 -49 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2000 -49 68 -1969 minecraft:grass_block
fill -80 69 -2000 -49 89 -1969 minecraft:air
fill -80 90 -2000 -49 110 -1969 minecraft:air
fill -48 50 -2000 -17 59 -1969 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2000 -17 67 -1969 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2000 -17 68 -1969 minecraft:grass_block
fill -48 69 -2000 -17 89 -1969 minecraft:air
fill -48 90 -2000 -17 110 -1969 minecraft:air
forceload remove -96 -2016 -33 -1953
say Spawn : 37 %
schedule function lifecraft:spawn/step_40 10t
