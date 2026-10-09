# Étape 32/106 : plateau
forceload add 128 -2080 191 -2017
fill 48 50 -2064 79 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 48 60 -2064 79 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 48 68 -2064 79 68 -2033 minecraft:grass_block
fill 48 69 -2064 79 89 -2033 minecraft:air
fill 48 90 -2064 79 110 -2033 minecraft:air
fill 80 50 -2064 111 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 80 60 -2064 111 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 80 68 -2064 111 68 -2033 minecraft:grass_block
fill 80 69 -2064 111 89 -2033 minecraft:air
fill 80 90 -2064 111 110 -2033 minecraft:air
forceload remove 32 -2080 95 -2017
schedule function lifecraft:spawn/step_32 10t
