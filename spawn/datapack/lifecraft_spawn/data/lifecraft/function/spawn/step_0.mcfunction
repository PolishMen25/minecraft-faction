# Étape 1/106 : plateau
forceload add 0 -2272 63 -2209
fill -80 50 -2256 -49 59 -2225 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2256 -49 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2256 -49 68 -2225 minecraft:grass_block
fill -80 69 -2256 -49 89 -2225 minecraft:air
fill -80 90 -2256 -49 110 -2225 minecraft:air
fill -48 50 -2256 -17 59 -2225 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2256 -17 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2256 -17 68 -2225 minecraft:grass_block
fill -48 69 -2256 -17 89 -2225 minecraft:air
fill -48 90 -2256 -17 110 -2225 minecraft:air
forceload remove -96 -2272 -33 -2209
schedule function lifecraft:spawn/step_1 10t
