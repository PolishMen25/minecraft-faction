# Étape 30/106 : plateau
forceload add 0 -2080 63 -2017
fill -80 50 -2064 -49 59 -2033 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2064 -49 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2064 -49 68 -2033 minecraft:grass_block
fill -80 69 -2064 -49 89 -2033 minecraft:air
fill -80 90 -2064 -49 110 -2033 minecraft:air
fill -48 50 -2064 -17 59 -2033 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2064 -17 67 -2033 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2064 -17 68 -2033 minecraft:grass_block
fill -48 69 -2064 -17 89 -2033 minecraft:air
fill -48 90 -2064 -17 110 -2033 minecraft:air
forceload remove -96 -2080 -33 -2017
say Spawn : 28 %
schedule function lifecraft:spawn/step_30 10t
