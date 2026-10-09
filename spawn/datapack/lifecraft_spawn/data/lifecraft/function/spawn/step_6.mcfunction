# Étape 7/106 : plateau
forceload add 64 -2240 127 -2177
fill -16 50 -2224 15 59 -2193 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2224 15 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2224 15 68 -2193 minecraft:grass_block
fill -16 69 -2224 15 89 -2193 minecraft:air
fill -16 90 -2224 15 110 -2193 minecraft:air
fill 16 50 -2224 47 59 -2193 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2224 47 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2224 47 68 -2193 minecraft:grass_block
fill 16 69 -2224 47 89 -2193 minecraft:air
fill 16 90 -2224 47 110 -2193 minecraft:air
forceload remove -32 -2240 31 -2177
schedule function lifecraft:spawn/step_7 10t
