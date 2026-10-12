# Étape 21/106 : plateau
forceload add 0 -2144 47 -2081
fill -80 50 -2128 -49 59 -2097 minecraft:stone replace #minecraft:replaceable
fill -80 60 -2128 -49 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill -80 68 -2128 -49 68 -2097 minecraft:grass_block
fill -80 69 -2128 -49 89 -2097 minecraft:air
fill -80 90 -2128 -49 110 -2097 minecraft:air
fill -48 50 -2128 -17 59 -2097 minecraft:stone replace #minecraft:replaceable
fill -48 60 -2128 -17 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill -48 68 -2128 -17 68 -2097 minecraft:grass_block
fill -48 69 -2128 -17 89 -2097 minecraft:air
fill -48 90 -2128 -17 110 -2097 minecraft:air
forceload remove -96 -2144 -33 -2081
schedule function lifecraft:spawn/step_21 10t
