# Étape 14/106 : plateau
forceload add 192 -2208 239 -2145
fill 112 50 -2192 143 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2192 143 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2192 143 68 -2161 minecraft:grass_block
fill 112 69 -2192 143 89 -2161 minecraft:air
fill 112 90 -2192 143 110 -2161 minecraft:air
fill 144 50 -2192 175 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2192 175 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2192 175 68 -2161 minecraft:grass_block
fill 144 69 -2192 175 89 -2161 minecraft:air
fill 144 90 -2192 175 110 -2161 minecraft:air
forceload remove 96 -2208 159 -2145
schedule function lifecraft:spawn/step_14 10t
