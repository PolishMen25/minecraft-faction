# Suite de box_reset quand les chunks ont dû être forcés : remplit s'ils sont chargés, puis les libère
scoreboard players set #charge lifecraft_box 0
execute if loaded -64 0 -2032 if loaded -64 0 -2001 if loaded -33 0 -2032 if loaded -33 0 -2001 run scoreboard players set #charge lifecraft_box 1
execute if score #charge lifecraft_box matches 1 run function lifecraft:spawn/box_fill
execute if score #charge lifecraft_box matches 0 run say Box : chunks pas encore chargés, remise à neuf au prochain passage
forceload remove -64 -2032 -33 -2001
