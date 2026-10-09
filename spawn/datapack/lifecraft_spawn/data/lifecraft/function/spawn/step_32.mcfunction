# Étape 33/106 : plateau
forceload add 192 -2080 239 -2017
fill 112 50 -2064 143 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2064 143 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2064 143 68 -2033 minecraft:grass_block
fill 112 69 -2064 143 89 -2033 minecraft:air
fill 112 90 -2064 143 110 -2033 minecraft:air
fill 144 50 -2064 175 59 -2033 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2064 175 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2064 175 68 -2033 minecraft:grass_block
fill 144 69 -2064 175 89 -2033 minecraft:air
fill 144 90 -2064 175 110 -2033 minecraft:air
forceload remove 96 -2080 159 -2017
schedule function lifecraft:spawn/step_33 10t
