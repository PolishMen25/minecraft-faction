# Étape 38/106 : plateau
forceload add 192 -2048 239 -1985
fill 112 50 -2032 143 59 -2001 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2032 143 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2032 143 68 -2001 minecraft:grass_block
fill 112 69 -2032 143 89 -2001 minecraft:air
fill 112 90 -2032 143 110 -2001 minecraft:air
fill 144 50 -2032 175 59 -2001 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2032 175 67 -2001 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2032 175 68 -2001 minecraft:grass_block
fill 144 69 -2032 175 89 -2001 minecraft:air
fill 144 90 -2032 175 110 -2001 minecraft:air
forceload remove 96 -2048 159 -1985
schedule function lifecraft:spawn/step_38 10t
