package com.planet.simulator.engine

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/** 星球类型定义 —— 可无限扩展 */
enum class PlanetType(val cnName: String, val enName: String, val population: Long, val hasRing: Boolean) {
    EARTH("黑德斯", "类地行星", 8_000_000_000L, false),
    DESERT("泰宁", "沙漠星球", 3_000_000_000L, false),
    ICE("冰冻星", "冰冻星球", 1_000_000_000L, false),
    CITY("阿瓦隆", "城市星球", 50_000_000_000L, false),
    LAVA("熔岩星", "熔岩星球", 500_000_000L, false),
    GAS("阿芙罗狄蒂", "气态巨星", 0L, true),
    DARK("黑星", "暗物质星", 0L, false);

    companion object {
        fun byId(id: Int) = entries[id.coerceIn(0, entries.size - 1)]
    }
}

/** 星球皮肤调色板 */
data class PlanetPalette(
    val ocean: Int, val oceanDeep: Int,
    val landLow: Int, val landHigh: Int, val landPeak: Int,
    val crater: Int,
    val cloud: Int,
    val atmosphere: Int,
    val atmosphereInner: Int,
    val shadow: Int,
    val lava: Int, val lavaCore: Int
)

val palettes = mapOf(
    PlanetType.EARTH to PlanetPalette(
        0xFF0D47A1.toInt(), 0xFF062B5C.toInt(),
        0xFF2E7D32.toInt(), 0xFF4CAF50.toInt(), 0xFF8D6E63.toInt(),
        0xFF1B5E20.toInt(),
        0xFFFFFFFF.toInt(),
        0x5581D4FA.toInt(), 0xAA81D4FA.toInt(),
        0xCC000000.toInt(),
        0xFFFF6B35.toInt(), 0xFFFFEA00.toInt()
    ),
    PlanetType.DESERT to PlanetPalette(
        0xFF5D4037.toInt(), 0xFF3E2723.toInt(),
        0xFFBF8F3F.toInt(), 0xFFFFB74D.toInt(), 0xFFA1887F.toInt(),
        0xFF4E342E.toInt(),
        0x88FFD54F.toInt(),
        0x55FFD54F.toInt(), 0xAAFFCC80.toInt(),
        0xCC000000.toInt(),
        0xFFFF5722.toInt(), 0xFFFFEA00.toInt()
    ),
    PlanetType.ICE to PlanetPalette(
        0xFF0277BD.toInt(), 0xFF01579B.toInt(),
        0xFFB3E5FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFD8DC.toInt(),
        0xFF4FC3F7.toInt(),
        0xE0FFFFFF.toInt(),
        0x66E1F5FE.toInt(), 0xAAE1F5FE.toInt(),
        0xBB000000.toInt(),
        0xFF4FC3F7.toInt(), 0xFF81D4FA.toInt()
    ),
    PlanetType.CITY to PlanetPalette(
        0xFF1A237E.toInt(), 0xFF0D1338.toInt(),
        0xFF4527A0.toInt(), 0xFF7E57C2.toInt(), 0xFFCE93D8.toInt(),
        0xFF311B92.toInt(),
        0x77B39DDB.toInt(),
        0x55CE93D8.toInt(), 0xAACE93D8.toInt(),
        0xCC000000.toInt(),
        0xFFFFD54F.toInt(), 0xFFFFEA00.toInt()
    ),
    PlanetType.LAVA to PlanetPalette(
        0xFF3E2723.toInt(), 0xFF1B0E0A.toInt(),
        0xFF5D4037.toInt(), 0xFF8D6E63.toInt(), 0xFF424242.toInt(),
        0xFF212121.toInt(),
        0x66FF8A65.toInt(),
        0x88FF5722.toInt(), 0xDDFF1744.toInt(),
        0xDD000000.toInt(),
        0xFFFF5722.toInt(), 0xFFFFEA00.toInt()
    ),
    PlanetType.GAS to PlanetPalette(
        0xFF4A148C.toInt(), 0xFF311B92.toInt(),
        0xFF6A1B9A.toInt(), 0xFFAB47BC.toInt(), 0xFFCE93D8.toInt(),
        0xFF4A148C.toInt(),
        0xAAF3E5F5.toInt(),
        0x66CE93D8.toInt(), 0xAACE93D8.toInt(),
        0xCC000000.toInt(),
        0xFFCE93D8.toInt(), 0xFFFFFFFF.toInt()
    ),
    PlanetType.DARK to PlanetPalette(
        0xFF1A1A2E.toInt(), 0xFF0A0A14.toInt(),
        0xFF2C2C54.toInt(), 0xFF3E3E7A.toInt(), 0xFF5C5CA8.toInt(),
        0xFF0A0A14.toInt(),
        0x33FFFFFF.toInt(),
        0x337E57C2.toInt(), 0x667E57C2.toInt(),
        0xFF000000.toInt(),
        0xFF7E57C2.toInt(), 0xFFFFFFFF.toInt()
    )
)

/** 星球 —— 真正的 3D 体渲染，带程序化纹理 */
class Planet(
    var centerX: Float,
    var centerY: Float,
    var radius: Float,
    var type: PlanetType = PlanetType.EARTH
) {
    val palette = palettes[type]!!

    // 动态状态
    var destruction: Float = 0f
    var rotation: Float = 0f
    var rotationSpeed: Float = 0.002f

    // 损伤点数据 — 记录极坐标损伤
    data class DamageSpot(val angle: Float, val latDist: Float, val power: Float, val age: Float = 0f)
    val damages = mutableListOf<DamageSpot>()

    // 预制像素纹理
    private var surfacePixels: IntArray? = null
    private var texW = 0
    private var texH = 0

    // 光照方向（归一化）
    var lightDirX = -0.5f
    var lightDirY = -0.5f
    var lightDirZ = 0.7f
    fun setLightDirection(x: Float, y: Float) {
        val len = sqrt(x * x + y * y + 1f)
        lightDirX = x / len
        lightDirY = y / len
        lightDirZ = 1f / len
    }

    // 星空背景
    private data class Star(var x: Float, var y: Float, var r: Float, var twinkle: Float)
    private val stars = mutableListOf<Star>()
    private var canvasW = 0f
    private var canvasH = 0f

    // 程序化噪声种子
    private val noiseSeed = Random(type.ordinal * 8887L + 12345L).nextLong()

    init { regenerate() }

    fun regenerate() {
        destruction = 0f
        damages.clear()
        rotation = Random.nextFloat() * PI.toFloat() * 2f
        buildTexture()
    }

    private fun buildTexture() {
        // 生成等距圆柱投影纹理
        val w = 512; val h = 256
        texW = w; texH = h
        val pixels = IntArray(w * h)
        val rand = Random(noiseSeed)

        for (y in 0 until h) {
            val lat = (y.toFloat() / h) * PI.toFloat() - PI.toFloat() / 2f
            for (x in 0 until w) {
                val lon = (x.toFloat() / w) * PI.toFloat() * 2f - PI.toFloat()
                val noise = fbmNoise(lon * 3f, lat * 3f, noiseSeed)
                val detailNoise = fbmNoise(lon * 8f, lat * 8f, noiseSeed + 1)
                val polar = abs(sin(lat))

                val color = when (type) {
                    PlanetType.EARTH -> {
                        val h = noise * 0.6f + detailNoise * 0.4f
                        if (h > 0.05f) {
                            val t = (h - 0.05f).coerceIn(0f, 1f) / 0.45f
                            blend(palette.landLow, palette.landHigh, t)
                        } else {
                            val t = (h + 0.15f).coerceIn(0f, 1f) / 0.2f
                            blend(palette.oceanDeep, palette.ocean, t)
                        }
                    }
                    PlanetType.DESERT -> {
                        val h = noise
                        val latVar = cos(lat * 2f) * 0.2f
                        val t = (h + latVar).coerceIn(0f, 1f)
                        blend(palette.landLow, palette.landHigh, t)
                    }
                    PlanetType.ICE -> {
                        val h = noise * 0.3f + 0.5f
                        val polarBoost = polar * 0.4f
                        val t = (h + polarBoost).coerceIn(0f, 1f)
                        blend(palette.ocean, palette.landHigh, t)
                    }
                    PlanetType.CITY -> {
                        val h = noise
                        val cityDensity = fbmNoise(lon * 12f, lat * 12f, noiseSeed + 99L)
                        val base = blend(palette.ocean, palette.landLow, h.coerceIn(0f, 1f))
                        if (h > 0.1f && cityDensity > 0.2f) {
                            blend(base, palette.lavaCore, (cityDensity - 0.2f).coerceIn(0f, 1f) / 0.5f)
                        } else base
                    }
                    PlanetType.LAVA -> {
                        val h = noise
                        if (h < 0.1f) {
                            blend(palette.ocean, palette.oceanDeep, (h + 0.1f).coerceIn(0f, 1f) / 0.2f)
                        } else {
                            val t = (h - 0.1f).coerceIn(0f, 1f) / 0.5f
                            val lavaHere = fbmNoise(lon * 10f, lat * 10f, noiseSeed + 7L)
                            if (lavaHere > 0.3f) blend(palette.landLow, palette.lava, (lavaHere - 0.3f) * 2f)
                            else blend(palette.landLow, palette.landHigh, t)
                        }
                    }
                    PlanetType.GAS -> {
                        // 气态巨星：水平条纹
                        val stripe = sin(lat * 10f + noise * 2f) * 0.5f + 0.5f
                        val turbulence = fbmNoise(lon * 5f, lat * 15f, noiseSeed) * 0.3f
                        val t = (stripe + turbulence).coerceIn(0f, 1f)
                        blend(palette.ocean, palette.landHigh, t)
                    }
                    PlanetType.DARK -> {
                        val h = noise * 0.3f + 0.2f
                        val t = h.coerceIn(0f, 1f)
                        blend(palette.oceanDeep, palette.landLow, t)
                    }
                }

                pixels[y * w + x] = color
            }
        }
        surfacePixels = pixels
    }

    private fun fbmNoise(x: Float, y: Float, seed: Long): Float {
        var total = 0f
        var amplitude = 1f
        var frequency = 1f
        var maxValue = 0f
        for (i in 0 until 5) {
            total += smoothNoise(x * frequency, y * frequency, seed + i * 7) * amplitude
            maxValue += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        return total / maxValue
    }

    private fun smoothNoise(x: Float, y: Float, seed: Long): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val a = pseudoRandom(ix, iy, seed)
        val b = pseudoRandom(ix + 1, iy, seed)
        val c = pseudoRandom(ix, iy + 1, seed)
        val d = pseudoRandom(ix + 1, iy + 1, seed)
        val u = fx * fx * (3f - 2f * fx)
        val v = fy * fy * (3f - 2f * fy)
        return a * (1 - u) * (1 - v) + b * u * (1 - v) + c * (1 - u) * v + d * u * v
    }

    private fun pseudoRandom(x: Int, y: Int, seed: Long): Float {
        val h = (x * 374761393L + y * 668265263L + seed * 1442695040888963407L) xor (x.toLong() xor y.toLong())
        return (((h xor (h shr 13)) * 1274126177L) and 0x00000000FFFFFFFFL).toFloat() / 4294967295f
    }

    fun addDamage(angle: Float, latDist: Float, power: Float) {
        damages.add(DamageSpot(angle, latDist.coerceIn(0f, 1f), power))
        destruction = (destruction + power * 0.04f).coerceAtMost(1f)
    }

    fun update(dt: Float) {
        rotation += rotationSpeed
        if (rotation > PI.toFloat() * 2f) rotation -= PI.toFloat() * 2f
    }

    fun ensureStars(w: Float, h: Float) {
        if (w == canvasW && h == canvasH && stars.isNotEmpty()) return
        canvasW = w; canvasH = h
        stars.clear()
        val rand = Random(42L)
        val count = (w * h / 8000).toInt().coerceAtLeast(300)
        repeat(count) {
            stars.add(Star(
                rand.nextFloat() * w,
                rand.nextFloat() * h,
                0.5f + rand.nextFloat() * 1.5f,
                rand.nextFloat() * PI.toFloat() * 2f
            ))
        }
    }

    fun drawBackground(canvas: Canvas) {
        ensureStars(canvas.width.toFloat(), canvas.height.toFloat())

        // 深空渐变
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = RadialGradient(
            centerX, centerY, radius * 6f,
            Color.parseColor("#FF0A0A1E"),
            Color.parseColor("#FF000000"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, canvasW, canvasH, bgPaint)

        // 星星
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (s in stars) {
            val tw = (sin(System.nanoTime() * 0.000001f + s.twinkle) + 1f) * 0.5f
            paint.alpha = (100 + tw * 155).toInt()
            paint.color = if (s.twinkle > 4.0f) 0xFFE3F2FD.toInt() else Color.WHITE
            canvas.drawCircle(s.x, s.y, s.r * (0.5f + tw * 0.5f), paint)
        }
        paint.alpha = 255
    }

    fun draw(canvas: Canvas) {
        drawBackground(canvas)
        if (type.hasRing) drawRing(canvas)
        drawAtmosphere(canvas)
        drawSphere(canvas)
        drawDamageOnSphere(canvas)
        drawCasts(canvas)
    }

    private fun drawRing(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        val outerR = radius * 1.8f
        val innerR = radius * 1.15f
        paint.color = palette.atmosphere
        paint.strokeWidth = outerR - innerR
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(1f, 0.3f)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ringPaint.shader = RadialGradient(0f, 0f, outerR,
            palette.atmosphere, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, outerR, ringPaint)
        ringPaint.color = palette.cloud
        ringPaint.strokeWidth = 4f
        for (i in 0..5) {
            ringPaint.alpha = (50 + i * 20).toInt()
            canvas.drawCircle(0f, 0f, innerR + i * (outerR - innerR) / 5f, ringPaint)
        }
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawAtmosphere(canvas: Canvas) {
        val atmPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        atmPaint.shader = RadialGradient(
            centerX, centerY, radius * 1.6f,
            palette.atmosphere,
            0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 1.6f, atmPaint)
        // 内层强光
        atmPaint.shader = RadialGradient(
            centerX, centerY, radius * 1.15f,
            palette.atmosphereInner,
            0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 1.15f, atmPaint)
    }

    /** 真正的 3D 球体投影渲染 */
    private fun drawSphere(canvas: Canvas) {
        val tex = surfacePixels ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val step = 2 // 像素步进，加快渲染
        val r2 = radius * radius

        for (dy in -radius.toInt()..radius.toInt() step step) {
            for (dx in -radius.toInt()..radius.toInt() step step) {
                val d2 = dx.toFloat() * dx + dy.toFloat() * dy
                if (d2 > r2) continue

                // 计算 3D 点法向量
                val nx = dx / radius
                val ny = dy / radius
                val nz = sqrt((1f - d2 / r2).coerceAtLeast(0f))

                // 光照
                val dot = nx * lightDirX + ny * lightDirY + nz * lightDirZ
                val lighting = (0.15f + dot * 0.85f).coerceIn(0f, 1f)

                // 计算纹理坐标
                val u = (atan2(nx, nz) + rotation)
                val v = asin(ny).coerceIn(-PI.toFloat() / 2f, PI.toFloat() / 2f)
                val uNorm = ((u + PI.toFloat()) / (PI.toFloat() * 2f)).coerceIn(0f, 1f)
                val vNorm = ((v + PI.toFloat() / 2f) / PI.toFloat()).coerceIn(0f, 1f)

                val tx = (uNorm * texW).toInt().coerceIn(0, texW - 1)
                val ty = (vNorm * texH).toInt().coerceIn(0, texH - 1)

                var color = tex[ty * texW + tx]

                // 光照应用
                val a = color and 0xFF000000.toInt()
                val r = ((color shr 16) and 0xFF) * lighting
                val g = ((color shr 8) and 0xFF) * lighting
                val b = (color and 0xFF) * lighting
                color = a or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

                canvas.drawRect(
                    centerX + dx, centerY + dy,
                    centerX + dx + step, centerY + dy + step,
                    ColorPaintWrapper(paint, color)
                )
            }
        }
    }

    private fun drawDamageOnSphere(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (d in damages) {
            val x = centerX + cos(d.angle) * d.latDist * radius
            val y = centerY + sin(d.angle) * d.latDist * radius
            val depth = sin(d.angle).coerceAtLeast(0f)
            if (depth < 0.05f) continue

            // 爆炸坑（橙色核心 + 红色岩浆）
            val size = (d.power * radius).coerceAtLeast(6f)
            paint.style = Paint.Style.FILL
            paint.color = 0xDD000000.toInt()
            canvas.drawCircle(x, y, size, paint)
            paint.color = blend(palette.lava, palette.lavaCore, 0.3f)
            paint.alpha = (depth * 220).toInt()
            canvas.drawCircle(x, y, size * 0.8f, paint)
            paint.color = palette.lavaCore
            paint.alpha = (depth * 180).toInt()
            canvas.drawCircle(x, y, size * 0.45f, paint)
            paint.color = Color.WHITE
            paint.alpha = (depth * 80).toInt()
            canvas.drawCircle(x, y, size * 0.18f, paint)
            paint.alpha = 255
        }

        // 整体损坏变暗效果
        if (destruction > 0.05f) {
            paint.color = 0x00000000 or ((destruction * 100).toInt() shl 24)
            canvas.drawCircle(centerX, centerY, radius, paint)
            paint.color = Color.WHITE
        }
    }

    private fun drawCasts(canvas: Canvas) {
        // 顶部高光（模拟强太阳光）
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            centerX + lightDirX * radius * 0.3f,
            centerY + lightDirY * radius * 0.3f,
            radius * 0.6f,
            0x22FFFFFF.toInt(),
            0x00FFFFFF.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    fun screenToPlanet(sx: Float, sy: Float): Pair<Float, Float>? {
        val dx = sx - centerX; val dy = sy - centerY
        val d2 = dx * dx + dy * dy; val r2 = radius * radius
        if (d2 > r2) return null
        val dist = sqrt(d2)
        val nz = sqrt((1f - d2 / r2).coerceAtLeast(0f))
        val nx = dx / radius; val ny = dy / radius
        val angle = atan2(dy, dx)
        val lat = asin(ny).coerceIn(-PI.toFloat() / 2f, PI.toFloat() / 2f)
        return Pair(angle, lat)
    }
}

private fun blend(c1: Int, c2: Int, t: Float): Int {
    val a1 = c1 ushr 24; val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
    val a2 = c2 ushr 24; val r2 = (c2 shr 16) and 0xFF; val g2 = (c2 shr 8) and 0xFF; val b2 = c2 and 0xFF
    val t2 = t.coerceIn(0f, 1f)
    val a = ((a1 + (a2 - a1) * t2).toInt().coerceIn(0, 255))
    val r = ((r1 + (r2 - r1) * t2).toInt().coerceIn(0, 255))
    val g = ((g1 + (g2 - g1) * t2).toInt().coerceIn(0, 255))
    val b = ((b1 + (b2 - b1) * t2).toInt().coerceIn(0, 255))
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Paint 包装器，因为 Canvas.drawRect 需要 Paint 参数 */
private fun Paint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }

// 辅助扩展：用颜色创建 Paint
private fun ColorPaintWrapper(base: Paint, color: Int): Paint {
    base.color = color
    base.style = Paint.Style.FILL
    base.strokeWidth = 0f
    return base
}
