# Étape 13/106 : plateau
forceload add 128 -2208 191 -2145
fill 48 50 -2192 79 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 48 60 -2192 79 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 48 68 -2192 79 68 -2161 minecraft:grass_block
fill 48 69 -2192 79 89 -2161 minecraft:air
fill 48 90 -2192 79 110 -2161 minecraft:air
fill 80 50 -2192 111 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 80 60 -2192 111 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 80 68 -2192 111 68 -2161 minecraft:grass_block
fill 80 69 -2192 111 89 -2161 minecraft:air
fill 80 90 -2192 111 110 -2161 minecraft:air
forceload remove 32 -2208 95 -2145
schedule function lifecraft:spawn/step_13 10t
