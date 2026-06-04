package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern Jetpack Compose Modifier that converts any container into a transparent
 * liquid-glass pane. Utilizes high-contrast vertical gradients and specular highlights
 * to emulate premium spherical glass refractions matching the reference design.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 16.dp,
    borderAlpha: Float = 0.25f,
    bgAlpha: Float = 0.08f
) = this
    .clip(RoundedCornerShape(cornerRadius))
    // 1. Double layer backdrop gradient: Top highlight, dark core absorption, bottom rim glare
    .background(
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = bgAlpha * 2.8f),      // Glossy top specular reflection
                Color.White.copy(alpha = bgAlpha * 0.9f),      // Smooth glass face transience
                Color(0xFF04060A).copy(alpha = 0.85f),         // Dense dark obsidian core absorption
                Color(0xFF0E1118).copy(alpha = 0.72f),         // Semi-translucent base
                Color.White.copy(alpha = bgAlpha * 1.4f)       // Ground bounce reflection rim glow
            ),
            startY = 0f
        )
    )
    // 2. Shiny high-contrast light bleed outer border simulating polished glass bevels
    .border(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha * 3.2f),  // Primary specular point-source highlight
                Color.White.copy(alpha = borderAlpha * 0.35f), // Translucent edge fade
                Color.White.copy(alpha = borderAlpha * 1.6f)   // Ambient secondary bounce highlight
            ),
            start = Offset(0f, 0f),
            end = Offset.Infinite
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Modern Jetpack Compose Modifier that converts any clickable or button container
 * into a realistic 3D glass button, matching the reference glass design.
 * Features specular glare on top-left, translucent refraction backing, and a sharp outline.
 */
fun Modifier.glassButton(
    cornerRadius: Dp = 20.dp,
    isCircle: Boolean = false
) = this
    .clip(if (isCircle) CircleShape else RoundedCornerShape(cornerRadius))
    .drawBehind {
        val width = size.width
        val height = size.height
        
        if (isCircle) {
            val center = Offset(width / 2f, height / 2f)
            val r = size.minDimension / 2f
            
            // 1. Dark core base shadow fill
            drawCircle(
                color = Color(0x350A0F1D),
                radius = r,
                center = center
            )
            
            // 2. Liquid Glass Face Translucence Gradient
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),      // Glossy top shine
                        Color.White.copy(alpha = 0.05f),      // Smooth glass face
                        Color(0xFF0D121F).copy(alpha = 0.35f), // Dense dark obsidian absorption
                        Color.White.copy(alpha = 0.12f)       // Ground bounce reflection
                    ),
                    startY = 0f,
                    endY = height
                ),
                radius = r,
                center = center
            )
            
            // 3. High-Gloss Specular Glare (curved top-left flare)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.45f, height * 0.22f),
                    radius = r * 0.72f
                ),
                radius = r * 0.72f,
                center = Offset(width * 0.45f, height * 0.22f)
            )

            // Inner crescent glass reflection
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    startY = height * 0.72f,
                    endY = height
                ),
                radius = r,
                center = center
            )
            
            // 4. Polished glass bezel crisp outline with gradient
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),  // Top-left primary highlight point
                        Color.White.copy(alpha = 0.18f),  // Soft translucent side
                        Color.White.copy(alpha = 0.60f)   // Bottom-right secondary bounce highlight
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                radius = r - 0.75.dp.toPx(),
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
        } else {
            val rect = Rect(0f, 0f, width, height)
            val rPx = cornerRadius.toPx()
            val roundRect = RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(rPx, rPx))
            val path = Path().apply { addRoundRect(roundRect) }
            
            // 1. Dark core base shadow fill
            drawPath(path = path, color = Color(0x350A0F1D))
            
            // 2. Translucent face glass gradient
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f),
                        Color(0xFF0D121F).copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.12f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
            
            // 3. Pill highlight glare at top
            val glareHeight = height * 0.35f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = glareHeight
                ),
                topLeft = Offset(4.dp.toPx(), 2.dp.toPx()),
                size = Size(width - 8.dp.toPx(), glareHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx * 0.7f, rPx * 0.7f)
            )
            
            // 4. Polished crisp outline
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.60f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                style = Stroke(width = 1.2f.dp.toPx())
            )
        }
    }

/**
 * Animated background canvas that draws shifting, organic, monochrome "liquid blobs"
 * floating leisurely. Because they are transparent gray/white, they simulate
 * clear, uncolored liquid material moving behind the glass panes.
 */
@Composable
fun LiquidGlassBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAnimation")

    // Dynamic wave / blobbing parameters
    val animationTime1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time1"
    )

    val animationTime2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Ambient space background: Ultra-dark deep carbon canvas to simulate pure 3D glass highlighting
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000000), // Pure ink black
                        Color(0xFF05070D), // Deep carbon dark glow
                        Color(0xFF000000)  // Pure ink black
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize().blur(40.dp)) {
            val width = size.width
            val height = size.height

            if (width > 0 && height > 0) {
                // Drawing liquid blob 1
                val bx1 = width * 0.35f + sin(animationTime1) * (width * 0.12f)
                val by1 = height * 0.4f + cos(animationTime1) * (height * 0.1f)
                val r1 = width * 0.3f + sin(animationTime2) * (width * 0.05f)
                drawLiquidBlob(bx1, by1, r1)

                // Drawing liquid blob 2
                val bx2 = width * 0.65f + cos(animationTime2) * (width * 0.15f)
                val by2 = height * 0.65f + sin(animationTime1) * (height * 0.12f)
                val r2 = width * 0.35f + cos(animationTime1) * (width * 0.04f)
                drawLiquidBlob(bx2, by2, r2)

                // Drawing secondary floating liquid rings / droplets
                val bx3 = width * 0.5f + sin(animationTime2 * 1.5f) * (width * 0.25f)
                val by3 = height * 0.2f + cos(animationTime1 * 1.2f) * (height * 0.08f)
                drawLiquidBlob(bx3, by3, width * 0.15f)
            }
        }
    }
}

private fun DrawScope.drawLiquidBlob(cx: Float, cy: Float, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.04f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

/**
 * Custom 3D Glass Switch modeling the glass capsule toggles with sliding spherical knobs.
 * Employs animated offsets and soft, realistic blue refractive glow when activated.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    
    // Physics-driven smooth sliding knob transit
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 28.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "switchKnob"
    )

    // Track frosted background opacity animation when toggled
    val trackOpacity by animateFloatAsState(
        targetValue = if (checked) 0.32f else 0.16f,
        animationSpec = tween(250),
        label = "trackOpacity"
    )

    Box(
        modifier = modifier
            .size(56.dp, 32.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .drawBehind {
                val width = size.width
                val height = size.height
                val r = height / 2f
                
                // 1. Frosted translucent white capsule track fill (glass theme matching the user's reference image)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = trackOpacity + 0.05f),
                            Color.White.copy(alpha = trackOpacity - 0.05f)
                        ),
                        startY = 0f,
                        endY = height
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
                )
                
                // 2. Bezel border outline around switch capsule to mimic glass refractive thickness
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.40f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(width, height)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    style = Stroke(width = 1.25f.dp.toPx())
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Glass sphere slider representing the 3D frosted glass droplet bulb
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(24.dp)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val r = w / 2f
                    
                    // 1. Knob drop shadow projection below the knob for 3D depth mimicking the reference image
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.20f),
                        radius = r + 1.dp.toPx(),
                        center = Offset(w * 0.5f, h * 0.5f + 1.5f.dp.toPx())
                    )
                    
                    // 2. Translucent frosted white glass knob fill
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.58f),
                                Color.White.copy(alpha = 0.22f)
                            ),
                            startY = 0f,
                            endY = h
                        ),
                        radius = r,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // 3. Highlight/Shine border around the knob - mimicking the bright top-left specular curve
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f), // Very bright specular glint
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.65f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        radius = r - 0.5f.dp.toPx(),
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // 4. Subtle inner diagonal glare light (resembling water bead refraction)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.35f, h * 0.35f),
                            radius = r * 0.5f
                        ),
                        radius = r * 0.65f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                }
        )
    }
}

private fun colorMapChecked(c: Color): Color = c

/**
 * Custom 3D Glass Text mimicking the glowing, hollow refractiveness of the 9:41 clock image.
 * Uses overlapping composable texts with diagonal silver gradients and hair-thin stroke masks.
 */
@Composable
fun GlassText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        // 1. Semi-translucent core fill (enhanced white gradient to stand out beautifully and be highly visible!)
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),  // Gorgeous glint at top
                        Color.White.copy(alpha = 0.65f),  // Highly visible frosted core
                        Color.White.copy(alpha = 0.85f)   // Soft reflective bottom bounce
                    )
                )
            ),
            modifier = Modifier.drawBehind {
                // Subtle 3D background shadow projection to elevate the letters from wallpaper noise
                drawCircle(
                    color = Color.Black.copy(alpha = 0.18f),
                    radius = (size.minDimension * 0.9f).coerceIn(0f, 120f),
                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                )
            }
        )
        
        // 2. Multi-point refractive gradient border stroke to simulate glass edge reflections
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f), // Specular light point highlight
                        Color.White.copy(alpha = 0.25f), // Soft transparency blend
                        Color.White.copy(alpha = 0.75f)  // Ground bounce shadow rim
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(250f, 150f)
                ),
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.35f,
                    miter = 4f,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        )
        
        // 3. Top-down glossy shine overlay
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
        )
    }
}


