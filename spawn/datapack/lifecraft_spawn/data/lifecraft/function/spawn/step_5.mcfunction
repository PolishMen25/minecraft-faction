# Étape 6/106 : plateau
forceload add 0 -2240 63 -2177
fill -80 50 -2224 -49 59 -2193 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2224 -49 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2224 -49 68 -2193 minecraft:grass_block
fill -80 69 -2224 -49 89 -2193 minecraft:air
fill -80 90 -2224 -49 110 -2193 minecraft:air
fill -48 50 -2224 -17 59 -2193 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2224 -17 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2224 -17 68 -2193 minecraft:grass_block
fill -48 69 -2224 -17 89 -2193 minecraft:air
fill -48 90 -2224 -17 110 -2193 minecraft:air
forceload remove -96 -2240 -33 -2177
schedule function lifecraft:spawn/step_6 10t
