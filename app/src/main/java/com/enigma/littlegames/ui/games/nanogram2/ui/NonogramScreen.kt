package com.enigma.littlegames.ui.games.nanogram2.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.littlegames.common.GameTheme
import com.enigma.littlegames.common.LocalGameTheme
import com.enigma.littlegames.ui.games.nonogram.CellMark
import com.enigma.littlegames.ui.games.nonogram.NonogramDifficulty
import com.enigma.littlegames.ui.games.nonogram.NonogramState
import com.enigma.littlegames.ui.games.nonogram.computeColClues
import com.enigma.littlegames.ui.games.nonogram.computeRowClues
import com.enigma.littlegames.ui.games.nonogram.isLineComplete
import com.enigma.littlegames.ui.games.nonogram.randomImage
import kotlin.collections.toList

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun NonogramGameScreen (
    nonogramState: NonogramState,
    onClick: (Pair<Int, Int>) -> Unit,
    onLongClick: (Pair<Int, Int>) -> Unit,
    gameTheme: GameTheme
) {
    LazyVerticalGrid(columns = GridCells.Fixed(nonogramState.playerGrid.size+1), modifier = Modifier.fillMaxWidth().padding(2.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        gameTheme.background
                    )
            ) {

            }
        }
        nonogramState.colClues.forEachIndexed { index, it ->
            val xc =   nonogramState.playerGrid.map {
                it[index]
            }
            item {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(gameTheme.background)
                        .border(1.dp, color = gameTheme.accent.copy(alpha = .3f), shape = RectangleShape)
                        .aspectRatio(1f)
                        .padding(2.dp)
                ) {
                    Text(
                        it.sum().toString(),
                        autoSize = TextAutoSize.StepBased(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                        color = if (isLineComplete(
                               xc,  //(0 until nonogramState.size).map { r -> nonogramState.playerGrid[r][index] },
                                nonogramState.colClues[index],
                            )
                        ) gameTheme.success.copy(alpha = 0.8f) else gameTheme.textSecondary
                    )
                }
            }
        }

        nonogramState.playerGrid.forEachIndexed { index, marks ->
            item {
                val pl= nonogramState.rowClues[index]
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(gameTheme.background)
                        .border(1.dp, color = gameTheme.accent.copy(alpha = .3f), shape = RectangleShape)
                        .aspectRatio(1f)
                        .padding(2.dp)
                ) {
                    Text(
                        pl.sum().toString(),
                        autoSize = TextAutoSize.StepBased(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                        color = if(isLineComplete(
                            nonogramState.playerGrid[index].toList(),
                            nonogramState.rowClues[index]
                        )) gameTheme.success.copy(alpha = 0.8f) else gameTheme.textSecondary
                    )
                }
            }
            itemsIndexed(marks) {index2,mark->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                onClick(Pair(index,index2))
                            },
                            onLongClick = { onLongClick.invoke(Pair(index,index2)) }
                        )
                ) {
                    when(mark){
                        CellMark.EMPTY -> {
                            NonogramCell(
                                mark = CellMark.EMPTY,
                                solFill = nonogramState.solution[index][index2],
                                isError = (index to index2) in nonogramState.errorCells,
                                isHint = (index to index2) in nonogramState.hintCells,
                                t = gameTheme,
                                onTap = { onClick(Pair(index,index2)) },
                                onLongPress = { onLongClick(Pair(index,index2)) }
                            )
                        }
                        CellMark.FILLED -> {
                            NonogramCell(
                                mark = CellMark.FILLED,
                                solFill = nonogramState.solution[index][index2],
                                isError = (index to index2) in nonogramState.errorCells,
                                isHint = (index to index2) in nonogramState.hintCells,
                                t = gameTheme,
                                onTap = { onClick(Pair(index,index2)) },
                                onLongPress = { onLongClick(Pair(index,index2)) }
                            )
                        }
                        CellMark.CROSSED -> {
                            NonogramCell(
                                mark = CellMark.CROSSED,
                                solFill = nonogramState.solution[index][index2],
                                isError = (index to index2) in nonogramState.errorCells,
                                isHint = (index to index2) in nonogramState.hintCells,
                                t = gameTheme,
                                onTap = { onClick(Pair(index,index2)) },
                                onLongPress = { onLongClick(Pair(index,index2)) }
                            )
                        }
                    }
                }
            }
        }


    }
}

@Preview
@Composable
private fun Nana() {
    NonogramGameScreen(
        NonogramState(
            playerGrid = y,
            solution = x,
            rowClues = row,
            colClues = col,


        ),
        onClick = {},
        onLongClick= {},
        gameTheme = LocalGameTheme.current
    )
}


val x = randomImage(NonogramDifficulty.HARD)
val col = computeColClues(x)
val row = computeRowClues(x)
val y = x.map {
    it.map { v->
        if (v) CellMark.FILLED else CellMark.CROSSED
    }.toTypedArray()
}.toTypedArray()



@Composable
private fun NonogramClueCell(
    clues: List<Int>,
    isCol: Boolean,
    size: Dp,
    height: Dp,
    isSolved: Boolean,
    t: GameTheme,
) {
    val color = if (isSolved) t.success.copy(alpha = 0.8f) else t.textSecondary
    if (isCol) {
        Box(
            Modifier.width(size).height(height),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                clues.filter { it > 0 }.forEach { n ->
                    Text("$n", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp)
                }
            }
        }
    } else {
        Box(
            Modifier.width(size).height(height),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                clues.filter { it > 0 }.joinToString(" "),
                color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 3.dp),
            )
        }
    }
}




// ─────────────────────────────────────────────────────────────────────────────
// Grid cell — tap fills, long press crosses
// Uses drawBehind for efficient single-pass rendering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NonogramCell(
    mark: CellMark,
    solFill: Boolean,
    isError: Boolean,
    isHint: Boolean,
    t: GameTheme,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {

    val bgColor = when {
        solFill              -> t.primary
        isError              -> Color(0xFFE94560)
        isHint               -> t.warning
        mark == CellMark.FILLED -> t.primary
        else                 -> t.surface
    }

    Box(
        Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .background(
                color = bgColor
            )
            .combinedClickable(
                enabled =true,
                onClick = { onTap.invoke() },
                onLongClick = onLongPress)
            .drawBehind{
                if (isHint) {
                    drawRect(
                        color = t.warning,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }


    ){
        when (mark){
            CellMark.EMPTY -> {
                Text(
                    "?",
                    autoSize = TextAutoSize.StepBased(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize(),

                )
            }
            CellMark.FILLED -> {

            }
            CellMark.CROSSED -> {
                Text(
                    "X",
                    autoSize = TextAutoSize.StepBased(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize().padding(1.dp),

                    )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun isLineComplete(marks: List<CellMark>, clues: List<Int>): Boolean {
    val runs = mutableListOf<Int>()
    var count = 0
    for (m in marks) {
        if (m == CellMark.FILLED) count++
        else { if (count > 0) { runs.add(count); count = 0 } }
    }
    if (count > 0) runs.add(count)
    val effective = if (runs.isEmpty()) listOf(0) else runs
    return effective == clues
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val t = LocalGameTheme.current
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = t.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun LegendItem(gesture: String, action: String, color: Color, t: GameTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(gesture, color = t.textSecondary, fontSize = 9.sp, letterSpacing = 0.5.sp)
        Text(action, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


