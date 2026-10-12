# Étape 10/106 : plateau
forceload add -96 -2208 -1 -2145
fill 176 50 -2224 207 59 -2193 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2224 207 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2224 207 68 -2193 minecraft:grass_block
fill 176 69 -2224 207 89 -2193 minecraft:air
fill 176 90 -2224 207 110 -2193 minecraft:air
fill 208 50 -2224 223 59 -2193 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2224 223 67 -2193 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2224 223 68 -2193 minecraft:grass_block
fill 208 69 -2224 223 110 -2193 minecraft:air
forceload remove 160 -2240 239 -2177
say Spawn : 9 %
schedule function lifecraft:spawn/step_10 10t
