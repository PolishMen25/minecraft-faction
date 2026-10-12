# Étape 5/106 : plateau
forceload add -96 -2240 -1 -2177
fill 176 50 -2256 207 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2256 207 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2256 207 68 -2225 minecraft:grass_block
fill 176 69 -2256 207 89 -2225 minecraft:air
fill 176 90 -2256 207 110 -2225 minecraft:air
fill 208 50 -2256 223 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2256 223 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2256 223 68 -2225 minecraft:grass_block
fill 208 69 -2256 223 110 -2225 minecraft:air
forceload remove 160 -2272 239 -2209
schedule function lifecraft:spawn/step_5 10t
