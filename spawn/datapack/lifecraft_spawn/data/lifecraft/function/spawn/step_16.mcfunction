# Étape 17/106 : plateau
forceload add 64 -2176 159 -2113
fill -16 50 -2160 15 59 -2129 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2160 15 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2160 15 68 -2129 minecraft:grass_block
fill -16 69 -2160 15 89 -2129 minecraft:air
fill -16 90 -2160 15 110 -2129 minecraft:air
fill 16 50 -2160 47 59 -2145 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2160 47 67 -2145 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2160 47 68 -2145 minecraft:grass_block
fill 16 69 -2160 47 110 -2145 minecraft:air
fill 16 50 -2144 44 59 -2129 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2144 44 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2144 44 68 -2129 minecraft:grass_block
fill 16 69 -2144 44 110 -2129 minecraft:air
forceload remove -32 -2176 31 -2113
schedule function lifecraft:spawn/step_17 10t
