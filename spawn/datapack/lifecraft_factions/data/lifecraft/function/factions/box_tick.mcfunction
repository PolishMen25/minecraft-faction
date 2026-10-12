# Remet la box à neuf, puis se replanifie dans 10 minutes (schedule remplace l'ancien : pas de doublon)
function lifecraft:factions/box_reset
schedule function lifecraft:factions/box_tick 12000t
