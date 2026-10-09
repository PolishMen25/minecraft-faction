# Étape 22/106 : plateau
forceload add 96 -2144 191 -2081
fill -16 50 -2128 15 59 -2097 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2128 15 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2128 15 68 -2097 minecraft:grass_block
fill -16 69 -2128 15 89 -2097 minecraft:air
fill -16 90 -2128 15 110 -2097 minecraft:air
fill 16 50 -2128 44 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2128 44 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2128 44 68 -2097 minecraft:grass_block
fill 16 69 -2128 44 89 -2097 minecraft:air
fill 16 90 -2128 44 110 -2097 minecraft:air
forceload remove -32 -2144 47 -2081
schedule function lifecraft:spawn/step_22 10t
