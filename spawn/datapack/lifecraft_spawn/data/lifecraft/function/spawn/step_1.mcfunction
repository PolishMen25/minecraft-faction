# Étape 2/106 : plateau
forceload add 64 -2272 127 -2209
fill -16 50 -2256 15 59 -2225 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2256 15 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2256 15 68 -2225 minecraft:grass_block
fill -16 69 -2256 15 89 -2225 minecraft:air
fill -16 90 -2256 15 110 -2225 minecraft:air
fill 16 50 -2256 47 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2256 47 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2256 47 68 -2225 minecraft:grass_block
fill 16 69 -2256 47 89 -2225 minecraft:air
fill 16 90 -2256 47 110 -2225 minecraft:air
forceload remove -32 -2272 31 -2209
schedule function lifecraft:spawn/step_2 10t
