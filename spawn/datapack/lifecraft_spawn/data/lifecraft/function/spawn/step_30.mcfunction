# Étape 31/106 : plateau
forceload add 64 -2080 127 -2017
fill -16 50 -2064 15 59 -2033 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2064 15 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2064 15 68 -2033 minecraft:grass_block
fill -16 69 -2064 15 89 -2033 minecraft:air
fill -16 90 -2064 15 110 -2033 minecraft:air
fill 16 50 -2064 47 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2064 47 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2064 47 68 -2033 minecraft:grass_block
fill 16 69 -2064 47 89 -2033 minecraft:air
fill 16 90 -2064 47 110 -2033 minecraft:air
forceload remove -32 -2080 31 -2017
schedule function lifecraft:spawn/step_31 10t
