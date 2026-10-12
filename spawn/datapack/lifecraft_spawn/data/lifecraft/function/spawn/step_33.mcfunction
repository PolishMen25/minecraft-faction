# Étape 34/106 : plateau
forceload add -96 -2048 -1 -1985
fill 176 50 -2064 207 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2064 207 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2064 207 68 -2033 minecraft:grass_block
fill 176 69 -2064 207 89 -2033 minecraft:air
fill 176 90 -2064 207 110 -2033 minecraft:air
fill 208 50 -2064 223 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2064 223 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2064 223 68 -2033 minecraft:grass_block
fill 208 69 -2064 223 110 -2033 minecraft:air
forceload remove 160 -2080 239 -2017
schedule function lifecraft:spawn/step_34 10t
