package com.verdeamano.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.verdeamano.R

enum class BotanicalPattern { TREE, BRANCH, SPROUT }
enum class BotanicalMotif { BUD, TREE, GEOMETRIC_TREE, BRANCH, POT, WATERING_CAN, TOOLS, LEAVES, SEED, GARDEN }

@Composable
fun BotanicalIllustration(motif: BotanicalMotif, modifier: Modifier = Modifier) {
    if (motif == BotanicalMotif.BUD) {
        BotanicalMark(modifier)
        return
    }
    val label = when (motif) {
        BotanicalMotif.BUD -> "Brote de dos hojas"
        BotanicalMotif.TREE -> "Árbol botánico"
        BotanicalMotif.GEOMETRIC_TREE -> "Árbol de copa geométrica"
        BotanicalMotif.BRANCH -> "Rama con hojas"
        BotanicalMotif.POT -> "Planta en maceta"
        BotanicalMotif.WATERING_CAN -> "Regadera"
        BotanicalMotif.TOOLS -> "Herramientas de jardín"
        BotanicalMotif.LEAVES -> "Hojas decorativas"
        BotanicalMotif.SEED -> "Semilla germinando"
        BotanicalMotif.GARDEN -> "Jardín abstracto"
    }
    val resource = when (motif) {
        BotanicalMotif.POT -> R.drawable.product_pot
        BotanicalMotif.WATERING_CAN -> R.drawable.product_watering
        BotanicalMotif.TOOLS -> R.drawable.product_tools
        else -> null
    }
    if (resource != null) {
        Image(painterResource(resource), label, modifier)
        return
    }
    val leaf = MaterialTheme.colorScheme.secondary
    val dark = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = label }) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        when (motif) {
            BotanicalMotif.TREE -> {
                drawLine(dark, Offset(w * .5f, h * .88f), Offset(w * .5f, h * .4f), stroke.width, cap = StrokeCap.Round)
                drawLine(dark, Offset(w * .5f, h * .58f), Offset(w * .29f, h * .39f), stroke.width, cap = StrokeCap.Round)
                drawLine(dark, Offset(w * .5f, h * .5f), Offset(w * .72f, h * .32f), stroke.width, cap = StrokeCap.Round)
                listOf(.3f to .31f, .5f to .23f, .73f to .28f, .38f to .51f, .65f to .49f).forEach { (x, y) -> drawLeaf(Offset(w * x, h * y), w * .25f, if(x < .5f) -30f else 30f, leaf) }
            }
            BotanicalMotif.GEOMETRIC_TREE -> {
                drawRect(dark, Offset(w * .47f, h * .6f), Size(w * .07f, h * .28f))
                drawCircle(leaf, w * .22f, Offset(w * .36f, h * .46f))
                drawCircle(BotanicalColors.Sage, w * .24f, Offset(w * .62f, h * .42f))
                drawCircle(leaf, w * .2f, Offset(w * .53f, h * .29f))
            }
            BotanicalMotif.BRANCH -> {
                drawLine(dark, Offset(w * .13f, h * .84f), Offset(w * .79f, h * .2f), stroke.width, cap = StrokeCap.Round)
                listOf(.3f to .65f, .48f to .49f, .64f to .34f).forEach { (x, y) ->
                    drawLeaf(Offset(w * (x - .07f), h * (y - .11f)), w * .24f, -32f, leaf)
                    drawLeaf(Offset(w * (x + .1f), h * (y + .08f)), w * .24f, 36f, leaf)
                }
            }
            BotanicalMotif.LEAVES -> {
                listOf(Triple(.27f, .35f, -36f), Triple(.7f, .3f, 32f), Triple(.52f, .7f, -20f)).forEach { (x, y, angle) ->
                    drawLeaf(Offset(w * x, h * y), w * .42f, angle, leaf)
                }
            }
            BotanicalMotif.SEED -> {
                drawOval(dark, Offset(w * .33f, h * .72f), Size(w * .34f, h * .16f))
                drawLine(leaf, Offset(w * .5f, h * .75f), Offset(w * .5f, h * .4f), stroke.width, cap = StrokeCap.Round)
                drawLeaf(Offset(w * .39f, h * .48f), w * .22f, -32f, leaf)
                drawLeaf(Offset(w * .61f, h * .4f), w * .24f, 30f, leaf)
            }
            BotanicalMotif.GARDEN -> {
                drawLine(dark, Offset(w * .09f, h * .85f), Offset(w * .91f, h * .85f), stroke.width, cap = StrokeCap.Round)
                listOf(.27f, .5f, .73f).forEachIndexed { index, x ->
                    val top = if(index == 1) .24f else .43f
                    drawLine(dark, Offset(w * x, h * .84f), Offset(w * x, h * top), stroke.width, cap = StrokeCap.Round)
                    drawLeaf(Offset(w * (x - .075f), h * (top + .1f)), w * .18f, -35f, leaf)
                    drawLeaf(Offset(w * (x + .075f), h * top), w * .18f, 35f, leaf)
                }
            }
            else -> Unit
        }
    }
}

@Composable
fun BotanicalMark(modifier: Modifier = Modifier, tint: Color = BotanicalColors.Botanical, decorative: Boolean = false) {
    val canvasModifier = if (decorative) modifier.size(64.dp).clearAndSetSemantics { } else modifier.size(64.dp).semantics { contentDescription = "Brote de dos hojas de Verde a Mano" }
    Canvas(canvasModifier) {
        val stem = Path().apply {
            moveTo(size.width * .5f, size.height * .9f)
            cubicTo(size.width * .49f, size.height * .63f, size.width * .49f, size.height * .38f, size.width * .5f, size.height * .31f)
        }
        drawPath(stem, tint, style = Stroke(width = size.width * .055f, cap = StrokeCap.Round))
        drawLeaf(Offset(size.width * .32f, size.height * .37f), size.width * .4f, -34f, tint)
        drawLeaf(Offset(size.width * .69f, size.height * .28f), size.width * .43f, 34f, tint)
    }
}

private fun DrawScope.drawLeaf(center: Offset, length: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        drawOval(color, topLeft = Offset(center.x - length * .48f, center.y - length * .19f), size = Size(length * .96f, length * .38f))
    }
}

@Composable
fun BotanicalBackground(pattern: BotanicalPattern, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        val botanical = MaterialTheme.colorScheme.secondary
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val tint = botanical.copy(alpha = .08f)
            when (pattern) {
                BotanicalPattern.TREE -> {
                    val trunk = Path().apply {
                        moveTo(w * .94f, h)
                        cubicTo(w * .82f, h * .9f, w * .9f, h * .76f, w * .75f, h * .68f)
                        moveTo(w * .86f, h * .84f)
                        cubicTo(w * .77f, h * .8f, w * .68f, h * .77f, w * .62f, h * .74f)
                    }
                    drawPath(trunk, tint, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                    drawLeaf(Offset(w * .73f, h * .68f), w * .19f, 24f, tint)
                    drawLeaf(Offset(w * .61f, h * .74f), w * .16f, -26f, tint)
                    drawLeaf(Offset(w * .94f, h * .78f), w * .17f, 55f, tint)
                }
                BotanicalPattern.BRANCH -> {
                    val branch = Path().apply {
                        moveTo(0f, h * .36f)
                        cubicTo(w * .08f, h * .39f, w * .13f, h * .46f, w * .18f, h * .51f)
                    }
                    drawPath(branch, tint, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    drawLeaf(Offset(w * .08f, h * .4f), w * .14f, -34f, tint)
                    drawLeaf(Offset(w * .17f, h * .49f), w * .15f, 34f, tint)
                }
                BotanicalPattern.SPROUT -> {
                    val stem = Path().apply {
                        moveTo(w * .88f, h)
                        quadraticTo(w * .82f, h * .9f, w * .84f, h * .82f)
                    }
                    drawPath(stem, tint, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    drawLeaf(Offset(w * .77f, h * .84f), w * .15f, -30f, tint)
                    drawLeaf(Offset(w * .9f, h * .81f), w * .16f, 35f, tint)
                }
            }
        }
        content()
    }
}

@Composable
fun BotanicalHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = BotanicalColors.Forest, shape = MaterialTheme.shapes.large) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(BotanicalColors.Leaf.copy(alpha = .17f), radius = size.width * .24f, center = Offset(size.width * 1.02f, size.height * .03f))
                drawLeaf(Offset(size.width * .92f, size.height * .76f), size.width * .19f, -37f, BotanicalColors.Sage.copy(alpha = .18f))
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = BotanicalColors.Ivory)
            }
        }
    }
}
