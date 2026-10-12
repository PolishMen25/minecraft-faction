# Étape 25/106 : plateau
forceload add 0 -2112 63 -2049
fill -80 50 -2096 -49 59 -2065 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2096 -49 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2096 -49 68 -2065 minecraft:grass_block
fill -80 69 -2096 -49 89 -2065 minecraft:air
fill -80 90 -2096 -49 110 -2065 minecraft:air
fill -48 50 -2096 -17 59 -2065 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2096 -17 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2096 -17 68 -2065 minecraft:grass_block
fill -48 69 -2096 -17 89 -2065 minecraft:air
fill -48 90 -2096 -17 110 -2065 minecraft:air
forceload remove -96 -2112 -33 -2049
schedule function lifecraft:spawn/step_25 10t
