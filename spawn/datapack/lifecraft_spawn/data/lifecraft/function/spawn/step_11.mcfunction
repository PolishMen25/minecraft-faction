# Étape 12/106 : plateau
forceload add 64 -2208 127 -2145
fill -16 50 -2192 15 59 -2161 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2192 15 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2192 15 68 -2161 minecraft:grass_block
fill -16 69 -2192 15 89 -2161 minecraft:air
fill -16 90 -2192 15 110 -2161 minecraft:air
fill 16 50 -2192 47 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2192 47 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2192 47 68 -2161 minecraft:grass_block
fill 16 69 -2192 47 89 -2161 minecraft:air
fill 16 90 -2192 47 110 -2161 minecraft:air
forceload remove -32 -2208 31 -2145
schedule function lifecraft:spawn/step_12 10t
