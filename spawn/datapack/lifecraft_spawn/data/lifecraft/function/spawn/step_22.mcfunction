# Étape 23/106 : plateau
forceload add 192 -2144 239 -2081
fill 109 50 -2128 111 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 109 60 -2128 111 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 109 68 -2128 111 68 -2097 minecraft:grass_block
fill 109 69 -2128 111 110 -2097 minecraft:air
fill 112 50 -2128 143 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 112 60 -2128 143 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 112 68 -2128 143 68 -2097 minecraft:grass_block
fill 112 69 -2128 143 89 -2097 minecraft:air
fill 112 90 -2128 143 110 -2097 minecraft:air
fill 144 50 -2128 175 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2128 175 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2128 175 68 -2097 minecraft:grass_block
fill 144 69 -2128 175 89 -2097 minecraft:air
fill 144 90 -2128 175 110 -2097 minecraft:air
forceload remove 96 -2144 159 -2081
schedule function lifecraft:spawn/step_23 10t
