# Étape 302/803
forceload add 391 -2425 432 -2408
fill -288 10 -1800 -249 11 -1785 minecraft:stone
fill -288 12 -1800 -249 12 -1785 minecraft:tuff
fill -288 13 -1800 -249 47 -1785 minecraft:air
fill -288 48 -1800 -249 81 -1785 minecraft:air
fill -288 82 -1800 -249 116 -1785 minecraft:air
fill -288 117 -1800 -249 150 -1785 minecraft:air
say Warzone : côté ouest de la faille creusé
forceload remove -289 -1801 -248 -1784
schedule function lifecraft:warzone/step_302 10t
