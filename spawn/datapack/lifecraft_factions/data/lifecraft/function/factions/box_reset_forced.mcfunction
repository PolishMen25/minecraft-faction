# Suite de box_reset quand les chunks ont dû être forcés : remplit s'ils sont chargés, puis les libère
scoreboard players set #charge lifecraft_box 0
execute if loaded 93 0 -2255 if loaded 93 0 -2224 if loaded 124 0 -2255 if loaded 124 0 -2224 run scoreboard players set #charge lifecraft_box 1
execute if score #charge lifecraft_box matches 1 run function lifecraft:factions/box_fill
execute if score #charge lifecraft_box matches 0 run say Box : chunks pas encore chargés, remise à neuf au prochain passage
forceload remove 93 -2255 124 -2224
