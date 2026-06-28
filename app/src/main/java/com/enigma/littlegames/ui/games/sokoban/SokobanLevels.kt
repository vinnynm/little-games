package com.enigma.littlegames.ui.games.sokoban

data class LevelDef(
    val xsb: String,
    val par: Int,
    val par2: Int,
    val name: String,
)

val SOKOBAN_LEVELS: List<LevelDef> = listOf(

    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 1 — Tutorial (Levels 1-10)
    // ═══════════════════════════════════════════════════════════════════════════

    // L1: 1 box, 1 target
    LevelDef("""
#####
#@$.#
#####
    """.trimIndent(), 1, 2, "First Push"),

    // L2: 2 boxes, 2 targets
    LevelDef("""
######
#@$. #
# $. #
######
    """.trimIndent(), 3, 5, "Side by Side"),

    // L3: 2 boxes, 2 targets
    LevelDef("""
#####
#.$ #
#   #
# $ #
#@$.#
#####
    """.trimIndent(), 5, 8, "Around the Bend"),

    // L4: 2 boxes, 2 targets
    LevelDef("""
######
#    #
# $$ #
# .. #
# @  #
######
    """.trimIndent(), 6, 10, "Twin Boxes"),

    // L5: 2 boxes, 2 targets
    LevelDef("""
######
#.   #
# $$ #
#  @ #
#.#  #
######
    """.trimIndent(), 7, 11, "Corner Goals"),

    // L6: FIXED - 2 boxes, 2 targets
    LevelDef("""
######
#    #
## $ #
#.  @#
#. $ #
######
    """.trimIndent(), 8, 14, "L-Shape"),

    // L7: FIXED - 4 boxes, 4 targets
    LevelDef("""
#######
#     #
# .$. #
#  $  #
# .$. #
#  @  #
#######
    """.trimIndent(), 8, 14, "Four Corners"),

    // L8: 3 boxes, 3 targets
    LevelDef("""
########
#      #
# $$.  #
# ...$ #
#   @  #
########
    """.trimIndent(), 8, 13, "Slide Right"),

    // L9: FIXED - 2 boxes, 2 targets
    LevelDef("""
######
#.   #
## $ #
#. @ #
#  $ #
######
    """.trimIndent(), 8, 14, "Staircase"),

    // L10: FIXED - 2 boxes, 2 targets
    LevelDef("""
######
#    #
# $  #
###$##
#..@ #
######
    """.trimIndent(), 7, 12, "Zigzag"),

    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 2 — Easy (Levels 11-20)
    // ═══════════════════════════════════════════════════════════════════════════

    // L11: 3 boxes, 3 targets
    LevelDef("""
########
#      #
# $$$  #
# ...  #
#   @  #
##    ##
######
    """.trimIndent(), 12, 20, "Triple Threat"),

    // L12: FIXED - 4 boxes, 4 targets
    LevelDef("""
#######
#     #
# .$. #
# $@$ #
# .$. #
#     #
#######
    """.trimIndent(), 12, 20, "Diamond"),

    // L13: 2 boxes, 2 targets
    LevelDef("""
#######
#     #
# $ $ #
# .#. #
#  @  #
#######
    """.trimIndent(), 6, 10, "Split Decision"),

    // L14: 3 boxes, 3 targets
    LevelDef("""
#######
#  @  #
# $$$ #
# ... #
#     #
#######
    """.trimIndent(), 10, 16, "Straight Shot"),

    // L15: 3 boxes, 3 targets
    LevelDef("""
#######
#     #
# $$$ #
# .@. #
#  .  #
#######
    """.trimIndent(), 12, 20, "V-Formation"),

    // L16: 4 boxes, 4 targets
    LevelDef("""
########
#      #
# $$$$ #
# .... #
#   @  #
##    ##
######
    """.trimIndent(), 16, 26, "Four in a Row"),

    // L17: 3 boxes, 3 targets
    LevelDef("""
#######
#  @  #
# $$$ #
##...#
#   ##
#####
    """.trimIndent(), 10, 16, "Tight Squeeze"),

    // L18: 2 boxes, 2 targets
    LevelDef("""
########
#      #
##$##$#
# ..  #
#  @  #
########
    """.trimIndent(), 10, 18, "Wall Split"),

    // L19: 3 boxes, 3 targets
    LevelDef("""
########
#  @   #
## $ $ #
# . . .#
#   $  #
########
    """.trimIndent(), 14, 24, "Scatter"),

    // L20: 3 boxes, 3 targets
    LevelDef("""
#######
# @   #
# $$  #
##..$ #
#  .  #
#######
    """.trimIndent(), 10, 18, "Double Back"),

    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 3 — Medium (Levels 21-30)
    // ═══════════════════════════════════════════════════════════════════════════

    // L21: 5 boxes, 5 targets
    LevelDef("""
#########
#       #
# $$$$$ #
# ..... #
#    @  #
###   ###
######
    """.trimIndent(), 20, 32, "Five Alive"),

    // L22: FIXED - 3 boxes, 3 targets
    LevelDef("""
#######
#  @  #
##$$ ##
# ... #
#  $  #
#    #
#######
    """.trimIndent(), 12, 20, "Corridor"),

    // L23: 3 boxes, 3 targets
    LevelDef("""
########
#  @   #
# $ $  #
###.#  #
#   .  #
# $ .  #
###  ###
########
    """.trimIndent(), 16, 28, "Triple Stack"),

    // L24: 4 boxes, 4 targets
    LevelDef("""
########
#   @  #
# $$ $ #
##.  .##
# .  . #
#  $   #
########
    """.trimIndent(), 18, 30, "Mirror"),

    // L25: 3 boxes, 3 targets
    LevelDef("""
########
#      #
###$$$ #
# ...  #
# @ ###
#######
    """.trimIndent(), 10, 18, "Compact"),

    // L26: 4 boxes, 4 targets
    LevelDef("""
########
#   @  #
# $$$$ #
##....#
#    ##
######
    """.trimIndent(), 18, 30, "Warehouse"),

    // L27: FIXED - 4 boxes, 4 targets
    LevelDef("""
########
#  @   #
### $$ #
# .# . #
#   $  #
## ####
#  $   #
#  .  #
# .####
########
    """.trimIndent(), 22, 38, "Spiral"),

    // L28: 6 boxes, 6 targets
    LevelDef("""
#########
#   @   #
# $$ $$ #
# ...   #
# ...   #
#  $$   #
#########
    """.trimIndent(), 24, 40, "Six Pack"),

    // L29: 4 boxes, 4 targets
    LevelDef("""
########
#  @   #
# $$   #
# .. $ #
# .. $ #
#      #
########
    """.trimIndent(), 16, 28, "Cascading"),

    // L30: 5 boxes, 5 targets
    LevelDef("""
##########
#        #
###$$$$$ #
# @..... #
#        #
##########
    """.trimIndent(), 18, 30, "Push Five"),

    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 4 — Hard (Levels 31-40)
    // ═══════════════════════════════════════════════════════════════════════════

    // L31: FIXED - 3 boxes, 3 targets
    LevelDef("""
######
#    #
# $  #
## . #
# .$.#
#  @ #
######
    """.trimIndent(), 14, 24, "Think Ahead"),

    // L32: 5 boxes, 5 targets
    LevelDef("""
########
# .....#
# $$$$ #
## # # #
#  $@  #
#      #
########
    """.trimIndent(), 26, 42, "Narrow Passage"),

    // L33: 4 boxes, 4 targets
    LevelDef("""
########
#      #
##$$ $$#
# ...  #
# ## . #
## @###
#    ##
######
    """.trimIndent(), 20, 34, "Winding Path"),

    // L34: 4 boxes, 4 targets
    LevelDef("""
#########
#       #
# $. .$ #
#   @   #
# $. .$ #
#       #
#########
    """.trimIndent(), 16, 28, "Cross Roads"),

    // L35: 3 boxes, 3 targets
    LevelDef("""
#######
#  @  #
# ### #
### . #
# $ $.#
# $ . #
###  ##
#######
    """.trimIndent(), 16, 28, "Three in Line"),

    // L36: 4 boxes, 4 targets
    LevelDef("""
#########
#       #
# .$.$  #
#  $$   #
#  ..   #
#   @   #
#########
    """.trimIndent(), 20, 34, "Dot Matrix"),

    // L37: 4 boxes, 4 targets
    LevelDef("""
#########
#       #
#  $ $  #
# .##.  #
#  @    #
# .##.  #
#  $ $  #
#       #
#########
    """.trimIndent(), 30, 50, "Octagon"),

    // L38: 4 boxes, 4 targets
    LevelDef("""
###########
#    #    #
# $$  @$$ #
# .#.#.#. #
#    #    #
###########
    """.trimIndent(), 24, 40, "Divided"),

    // L39: 4 boxes, 4 targets
    LevelDef("""
##########
#        #
### ######
#  $  $  #
# .#..#. #
#  $  $  #
### ######
#  @    #
##########
    """.trimIndent(), 28, 46, "Twin Rooms"),

    // L40: 4 boxes, 4 targets
    LevelDef("""
##########
#        #
#  $$$$  #
#  ....  #
#    @   #
##########
    """.trimIndent(), 14, 24, "Four Square"),

    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 5 — Expert (Levels 41-50)
    // ═══════════════════════════════════════════════════════════════════════════

    // L41: 6 boxes, 6 targets
    LevelDef("""
##########
# ...    #
# $$$$$$ #
# ...    #
#    @   #
##########
    """.trimIndent(), 24, 40, "Serpentine"),

    // L42: FIXED - 6 boxes, 6 targets
    LevelDef("""
###########
#    @    #
# $$$$$$  #
#  #..#.  #
#  .  .   #
###########
    """.trimIndent(), 30, 50, "Honeycomb"),

    // L43: 6 boxes, 6 targets
    LevelDef("""
##########
#        #
# .$.$.  #
#  $$    #
# .$.$.  #
#    @   #
##########
    """.trimIndent(), 36, 58, "Fortress"),

    // L44: 4 boxes, 4 targets
    LevelDef("""
############
#          #
#  $$$$    #
#  ....    #
#    @     #
############
    """.trimIndent(), 16, 28, "Long Push"),

    // L45: 4 boxes, 4 targets
    LevelDef("""
##########
#   @    #
# $$  $$ #
# ..  .. #
#        #
##########
    """.trimIndent(), 18, 30, "Inner Sanctum"),

    // L46: 6 boxes, 6 targets
    LevelDef("""
###########
#         #
#  $ $ $  #
#  . . .  #
#  . . .  #
#  $ $ $  #
#    @    #
###########
    """.trimIndent(), 40, 66, "Chess Board"),

    // L47: FIXED - Was duplicate, now unique - 6 boxes, 6 targets
    LevelDef("""
##########
#        #
# .$..$  #
#  $  $  #
# .$..$  #
#    @   #
##########
    """.trimIndent(), 32, 52, "Inception"),

    // L48: 6 boxes, 6 targets
    LevelDef("""
###########
#  .   .  #
#  $ # $  #
#  . # .  #
#  $ # $  #
#  $ # $  #
#  . @ .  #
###########
    """.trimIndent(), 44, 72, "Lattice"),

    // L49: FIXED - Was duplicate, now unique - 6 boxes, 6 targets
    LevelDef("""
##########
#   @    #
# $$$$$$ #
# ..  .. #
# ..  .. #
##########
    """.trimIndent(), 34, 56, "Crystal"),

    // L50: FIXED - Was duplicate, now unique - 6 boxes, 6 targets
    LevelDef("""
###########
#    @    #
#  $ $ $  #
#  $ $ $  #
# ..  ..  #
# ..  ..  #
###########
    """.trimIndent(), 38, 62, "The Gauntlet"),
)

fun levelCount() = SOKOBAN_LEVELS.size