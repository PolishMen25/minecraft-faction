# Étape 11/106 : plateau
forceload add 0 -2208 63 -2145
fill -80 50 -2192 -49 59 -2161 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2192 -49 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2192 -49 68 -2161 minecraft:grass_block
fill -80 69 -2192 -49 89 -2161 minecraft:air
fill -80 90 -2192 -49 110 -2161 minecraft:air
fill -48 50 -2192 -17 59 -2161 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2192 -17 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2192 -17 68 -2161 minecraft:grass_block
fill -48 69 -2192 -17 89 -2161 minecraft:air
fill -48 90 -2192 -17 110 -2161 minecraft:air
forceload remove -96 -2208 -33 -2145
schedule function lifecraft:spawn/step_11 10t
