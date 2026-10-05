package com.poiki.toyotawear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import kotlin.math.cos
import kotlin.math.sin

internal val NeonRed = Color(0xFFFF1838)
private val Metal = Color(0xFF656970)

/** Static light and floor: cached drawing, no bitmap or animation while the watch is idle. */
internal fun Modifier.cockpit(cabin: Boolean = false): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val horizon = h * .49f
    val glow = Brush.radialGradient(listOf(NeonRed.copy(alpha = .12f), Color.Transparent), Offset(w * .5f, horizon), w * .62f)
    val shade = Brush.verticalGradient(listOf(Color(0xFF101216), Color.Black, Color.Black))
    val beam = Brush.horizontalGradient(listOf(Color.Transparent, NeonRed.copy(alpha = .8f), Color.Transparent))
    onDrawBehind {
        drawRect(shade)
        drawRect(glow)
        val panel = Path().apply {
            moveTo(0f, h * .23f); lineTo(w * .36f, horizon)
            lineTo(w * .17f, horizon); lineTo(0f, h * .36f); close()
            moveTo(w, h * .22f); lineTo(w * .66f, horizon)
            lineTo(w * .85f, horizon); lineTo(w, h * .35f); close()
        }
        drawPath(panel, Brush.verticalGradient(listOf(Color(0xFF23070C), Color(0xFF0A0B0D))))
        drawLine(beam, Offset(0f, horizon), Offset(w, horizon), 8.dp.toPx(), alpha = .08f)
        drawLine(beam, Offset(0f, horizon), Offset(w, horizon), 2.dp.toPx(), alpha = .3f)
        drawLine(beam, Offset(0f, horizon), Offset(w, horizon), .6.dp.toPx())
        for (i in -3..3) {
            drawLine(Color(0xFF22252B), Offset(w * .5f + i * w * .075f, horizon), Offset(w * .5f + i * w * .4f, h), .5.dp.toPx(), alpha = .32f)
        }
        for (fraction in listOf(.54f, .61f, .73f, .92f)) {
            drawLine(Color(0xFF282A30), Offset(0f, h * fraction), Offset(w, h * fraction), .5.dp.toPx(), alpha = .23f)
        }
        if (cabin) {
            drawRoundRect(Color(0xFF0E1013), Offset(w * .16f, h * .32f), Size(w * .68f, h * .05f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            drawRoundRect(Color.Black, Offset(w * .42f, h * .31f), Size(w * .2f, h * .05f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            for (side in listOf(.2f, .8f)) {
                val air = Path().apply {
                    moveTo(w * side, h * .38f)
                    cubicTo(w * side, h * .48f, w * .5f, h * .48f, w * side, h * .7f)
                }
                drawPath(air, NeonRed.copy(alpha = .12f), style = Stroke(6.dp.toPx()))
                drawPath(air, NeonRed.copy(alpha = .6f), style = Stroke(.8.dp.toPx()))
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = h * .56f, endY = h * .94f))
    }
}

@Composable
internal fun BrandHeader(page: Int? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_toyota_mark), contentDescription = null, tint = Color(0xFFC9CDD2), modifier = Modifier.size(18.dp, 12.dp))
        Text(stringResource(R.string.app_name), fontSize = 8.sp, lineHeight = 10.sp, color = Color(0xFFB9BDC4), modifier = Modifier.padding(top = 2.dp))
        if (page != null) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { index -> Box(Modifier.size(3.dp).background(if (index == page) NeonRed else Color(0xFF35383D), CircleShape)) }
        }
    }
}

/** Small and large versions of the same illuminated dial. */
@Composable
internal fun NeonGauge(progress: () -> Float, modifier: Modifier, ticks: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = (if (ticks) 8 else 6).dp.toPx()
            val diameter = size.minDimension - inset * 2
            val start = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val width = (if (ticks) 6 else 4).dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Color(0xFF17191D), Color.Black)), radius = diameter / 2)
            drawArc(Color(0xFF24272D), 135f, 270f, false, start, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            val sweep = progress().coerceIn(0f, 1f) * 270f
            drawArc(NeonRed.copy(alpha = .1f), 135f, sweep, false, start, arcSize, style = Stroke(width * 2.6f, cap = StrokeCap.Round))
            drawArc(Brush.linearGradient(listOf(Color(0xFFFF0730), Color(0xFFFF6772))), 135f, sweep, false, start, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            if (ticks) {
                val center = Offset(size.width / 2, size.height / 2)
                for (i in 0..22) {
                    val a = Math.toRadians((135 + i * 270.0 / 22)).toFloat()
                    val outer = diameter / 2 - width - 3.dp.toPx()
                    val inner = outer - (if (i % 4 == 0) 4 else 2).dp.toPx()
                    drawLine(if (i % 4 == 0) Metal else Color(0xFF34373D), center + Offset(cos(a) * inner, sin(a) * inner), center + Offset(cos(a) * outer, sin(a) * outer), .6.dp.toPx())
                }
                val a = Math.toRadians((135 + sweep).toDouble()).toFloat()
                val point = center + Offset(cos(a) * diameter / 2, sin(a) * diameter / 2)
                drawCircle(NeonRed, 4.dp.toPx(), point)
                drawCircle(Color.White, 4.dp.toPx(), point, style = Stroke(1.dp.toPx()))
            }
        }
        content()
    }
}

internal fun Modifier.neonSurface(active: Boolean = false): Modifier = drawWithCache {
    val inset = 2.5.dp.toPx()
    val origin = Offset(inset, inset)
    val bounds = Size(size.width - inset * 2, size.height - inset * 2)
    val radius = androidx.compose.ui.geometry.CornerRadius(bounds.minDimension / 2)
    val fill = Brush.verticalGradient(if (active) listOf(Color(0xFF790E22), Color(0xFF28040B)) else listOf(Color(0xFF22252A), Color(0xFF08090B)))
    val edge = Brush.verticalGradient(listOf(if (active) NeonRed else Metal, Color(0xFF24262C), NeonRed))
    onDrawBehind {
        drawRoundRect(NeonRed.copy(alpha = if (active) .18f else .07f), topLeft = origin, size = bounds, cornerRadius = radius, style = Stroke(5.dp.toPx()))
        drawRoundRect(fill, topLeft = origin, size = bounds, cornerRadius = radius)
        drawRoundRect(edge, topLeft = origin, size = bounds, cornerRadius = radius, style = Stroke(.8.dp.toPx()))
    }
}

@Composable
internal fun NeonAction(icon: Int, label: Int, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).neonSurface(active),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, contentColor = Color.White),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(16.dp))
            BasicText(stringResource(label), modifier = Modifier.width(38.dp),
                style = TextStyle(color = Color.White, fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 8.sp))
        }
    }
}
