# Étape 9/106 : plateau
forceload add 192 -2240 239 -2177
fill 112 50 -2224 143 59 -2193 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2224 143 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2224 143 68 -2193 minecraft:grass_block
fill 112 69 -2224 143 89 -2193 minecraft:air
fill 112 90 -2224 143 110 -2193 minecraft:air
fill 144 50 -2224 175 59 -2193 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2224 175 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2224 175 68 -2193 minecraft:grass_block
fill 144 69 -2224 175 89 -2193 minecraft:air
fill 144 90 -2224 175 110 -2193 minecraft:air
forceload remove 96 -2240 159 -2177
schedule function lifecraft:spawn/step_9 10t
