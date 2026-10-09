# Étape 4/106 : plateau
forceload add 192 -2272 239 -2209
fill 112 50 -2256 143 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2256 143 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2256 143 68 -2225 minecraft:grass_block
fill 112 69 -2256 143 89 -2225 minecraft:air
fill 112 90 -2256 143 110 -2225 minecraft:air
fill 144 50 -2256 175 59 -2225 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2256 175 67 -2225 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2256 175 68 -2225 minecraft:grass_block
fill 144 69 -2256 175 89 -2225 minecraft:air
fill 144 90 -2256 175 110 -2225 minecraft:air
forceload remove 96 -2272 159 -2209
schedule function lifecraft:spawn/step_4 10t
