# Étape 3/106 : plateau
forceload add 128 -2272 191 -2209
fill 48 50 -2256 79 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 48 60 -2256 79 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 48 68 -2256 79 68 -2225 minecraft:grass_block
fill 48 69 -2256 79 89 -2225 minecraft:air
fill 48 90 -2256 79 110 -2225 minecraft:air
fill 80 50 -2256 111 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 80 60 -2256 111 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 80 68 -2256 111 68 -2225 minecraft:grass_block
fill 80 69 -2256 111 89 -2225 minecraft:air
fill 80 90 -2256 111 110 -2225 minecraft:air
forceload remove 32 -2272 95 -2209
schedule function lifecraft:spawn/step_3 10t
