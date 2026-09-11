package com.planet.simulator.engine

import android.content.Context
import android.graphics.*
import android.graphics.Typeface
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread = GameThread()
    private lateinit var planet: Planet
    private val ps = ParticleSystem()
    private val activeWeapons = mutableListOf<Weapon>()

    var paused = false
    var speed = 1f
    private var totalDamage = 0f
    private var totalCasualties = 0L
    private var planetIdx = 0
    private var population = PlanetType.byId(0).population
    private var selectedWeapon = 0
    private var damageMultiplier = 1f

    private var dragging = false
    private var lastDragX = 0f

    private var w = 0f; private var h = 0f
    private var vibrator: Vibrator? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // UI 区域缓存
    private data class Btn(val x: Float, val y: Float, val w: Float, val h: Float, val action: () -> Unit)
    private val uiButtons = mutableListOf<Btn>()
    private val weaponBtns = mutableListOf<Pair<RectF, Int>>()
    private val planetBtns = mutableListOf<RectF>()
    private var showPlanetSelect = false
    private var showSettings = false

    // 特效状态
    private var screenShake = 0f
    private var screenFlash = 0f
    private var lastHitTime = 0f

    init {
        holder.addCallback(this)
        vibrator = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }

    private fun vibrate(ms: Long) = try {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}

    override fun surfaceCreated(h: SurfaceHolder) { thread.setRunning(true); thread.start() }
    override fun surfaceChanged(holder: SurfaceHolder, f: Int, width: Int, height: Int) {
        this.w = width.toFloat(); this.h = height.toFloat(); initPlanet()
    }
    override fun surfaceDestroyed(h: SurfaceHolder) {
        thread.setRunning(false)
        var r = true; while (r) { try { thread.join(); r = false } catch (_: InterruptedException) {} }
    }
    fun resume() { thread.setRunning(true) }
    fun pause() { thread.setRunning(false) }
    fun destroy() { thread.setRunning(false) }

    private fun initPlanet() {
        val r = (w.coerceAtLeast(h) * 0.28f).coerceAtLeast(150f)
        planet = Planet(w / 2, h / 2, r, PlanetType.byId(planetIdx))
        planet.regenerate()
        population = planet.type.population
        totalDamage = 0f; totalCasualties = 0L
        activeWeapons.clear(); ps.clear()
        // 光照方向：从右上
        planet.setLightDirection(-0.3f, -0.4f)
    }

    inner class GameThread : Thread() {
        @Volatile private var running = false
        fun setRunning(r: Boolean) { running = r }
        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val canvas: Canvas? = try { holder.lockCanvas(null) } catch (_: Exception) { null }
                if (canvas != null) {
                    synchronized(holder) {
                        if (!paused) update(dt * speed)
                        drawFrame(canvas)
                    }
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun update(dt: Float) {
        planet.update(dt)
        ps.update(dt)
        screenShake *= 0.9f; screenFlash *= 0.88f

        val it = activeWeapons.iterator()
        while (it.hasNext()) {
            val w = it.next()
            val d = w.update(dt, planet, ps) * damageMultiplier
            if (d > 0) {
                totalDamage += d
                val killed = (d / 80f * population / 1000).toLong().coerceAtMost(population)
                totalCasualties += killed; population = (population - killed).coerceAtLeast(0L)
                if (w is Nuke || w is SolarFlare || w is Singularity) {
                    screenShake = 25f; screenFlash = 1f
                } else {
                    screenShake = maxOf(screenShake, 8f); screenFlash = maxOf(screenFlash, 0.3f)
                }
                lastHitTime = System.nanoTime().toFloat()
            }
            if (!w.active) it.remove()
        }
    }

    private fun drawFrame(c: Canvas) {
        c.drawColor(Color.BLACK)
        val shakeX = if (screenShake > 0.5f) (Random.nextFloat() - 0.5f) * screenShake else 0f
        val shakeY = if (screenShake > 0.5f) (Random.nextFloat() - 0.5f) * screenShake else 0f
        c.save(); c.translate(shakeX, shakeY)

        planet.draw(c)
        for (w in activeWeapons) w.draw(c)
        ps.draw(c)

        // 屏幕闪光
        if (screenFlash > 0.05f) {
            paint.color = 0x00000000 or ((screenFlash * 200).toInt() shl 24)
            c.drawRect(0f, 0f, w, h, paint)
        }

        c.restore()
        drawUI(c)
    }

    private fun drawUI(c: Canvas) {
        uiButtons.clear()
        paint.textSize = 28f; paint.typeface = Typeface.DEFAULT_BOLD; paint.alpha = 255

        drawTopBar(c)
        drawLeftPanel(c)
        drawWeaponPanel(c)
        if (showPlanetSelect) drawPlanetSelect(c)
        if (showSettings) drawSettings(c)
    }

    private fun drawTopBar(c: Canvas) {
        val y = 20f
        val sz = 56f
        var x = w / 2 - sz * 1.5f - 10f

        // 左菜单按钮
        drawIconBtn(c, 20f, y + 4f, sz, sz, "☰", Color.WHITE)
        uiButtons.add(Btn(20f, y + 4f, sz, sz) { showPlanetSelect = true })
        drawIconBtn(c, 20f + sz + 6f, y + 4f, sz, sz, "⚙", Color.parseColor("#FF00E5FF"))
        uiButtons.add(Btn(20f + sz + 6f, y + 4f, sz, sz) { showSettings = !showSettings })

        // 顶栏中心
        drawIconBtn(c, x, y, sz, sz, if (paused) "▶" else "‖", Color.WHITE)
        uiButtons.add(Btn(x, y, sz, sz) { paused = !paused }); x += sz + 10f

        val speedLabel = when (speed) { 1f -> "1x"; 2f -> "2x"; 4f -> "4x"; else -> "1x" }
        drawIconBtn(c, x, y, sz, sz, speedLabel, Color.parseColor("#FFFFEA00"))
        uiButtons.add(Btn(x, y, sz, sz) { speed = when (speed) { 1f -> 2f; 2f -> 4f; else -> 1f } })
        x += sz + 10f

        drawIconBtn(c, x, y, sz, sz, "»", Color.parseColor("#FF00E5FF"))
        uiButtons.add(Btn(x, y, sz, sz) { for (i in 0..25) update(0.05f) } )
    }

    private fun drawLeftPanel(c: Canvas) {
        val px = 20f; var py = 95f
        // 统计面板
        val panelH = 175f
        drawPanel(c, px, py, 250f, panelH, 0f)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#FF6BBE45"); paint.textSize = 20f
        c.drawText("🌍 破坏度", px + 14f, py + 30f, paint)
        paint.color = Color.parseColor("#FFFFEA00"); paint.textSize = 30f
        val pct = (planet.destruction * 100).toInt()
        c.drawText("${pct}%", px + 130f, py + 32f, paint)

        paint.color = Color.parseColor("#FFFFEA00"); paint.textSize = 20f
        c.drawText("👥 人口", px + 14f, py + 72f, paint)
        paint.color = Color.WHITE; paint.textSize = 26f
        c.drawText(fmt(population), px + 130f, py + 74f, paint)

        paint.color = Color.parseColor("#FFFF1744"); paint.textSize = 20f
        c.drawText("💀 死亡", px + 14f, py + 115f, paint)
        paint.color = Color.RED; paint.textSize = 26f
        c.drawText(fmt(totalCasualties), px + 130f, py + 117f, paint)

        paint.color = Color.parseColor("#FFB0BEC5"); paint.textSize = 16f
        c.drawText("伤害 ${"%.0f".format(totalDamage)}", px + 14f, py + 158f, paint)
        // 进度条
        paint.style = Paint.Style.FILL; paint.color = 0xFF424242.toInt()
        c.drawRect(px + 14f, py + 163f, px + 236f, py + 171f, paint)
        paint.color = Color.parseColor("#FF6BBE45")
        c.drawRect(px + 14f, py + 163f, px + 14f + 222f * planet.destruction, py + 171f, paint)

        py += panelH + 15f
        // 星球选择 + 重置
        val hb = 70f
        drawIconBtn(c, px, py, 80f, hb, "🌐", Color.parseColor("#FF00E5FF"))
        uiButtons.add(Btn(px, py, 80f, hb) { showPlanetSelect = true })
        drawIconBtn(c, px, py + hb + 8f, 80f, hb, "⟲", Color.parseColor("#FF00E5FF"))
        uiButtons.add(Btn(px, py + hb + 8f, 80f, hb) { initPlanet(); vibrate(80) })
    }

    private fun drawWeaponPanel(c: Canvas) {
        val cols = 3; val rows = 5
        val gap = 10f; val sz = 78f
        val pw = cols * sz + (cols + 1) * gap
        val ph = rows * sz + (rows + 1) * gap + 40f
        val px = w - pw - 15f; val py = 100f

        drawPanel(c, px - 5f, py - 5f, pw + 10f, ph + 10f, 0f)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE; paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT
        val wlist = Weapons.list
        weaponBtns.clear()
        for ((i, wi) in wlist.withIndex()) {
            val col = i % cols; val row = i / cols
            val bx = px + gap + col * (sz + gap); val by = py + gap + row * (sz + gap)
            val r = RectF(bx, by, bx + sz, by + sz)
            weaponBtns.add(Pair(r, i))

            paint.style = Paint.Style.FILL
            paint.color = if (i == selectedWeapon) Color.parseColor("#33FF6B35") else Color.parseColor("#55424242")
            c.drawRoundRect(r, 8f, 8f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (i == selectedWeapon) 3f else 1.5f
            paint.color = if (i == selectedWeapon) Color.parseColor("#FFFF6B35") else Color.parseColor("#FF757575")
            c.drawRoundRect(r, 8f, 8f, paint)

            // 图标圆
            paint.style = Paint.Style.FILL; paint.color = wi.accent
            c.drawCircle(bx + sz / 2, by + 18f, sz * 0.28f, paint)
            paint.color = Color.WHITE; paint.textSize = 16f; paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText(weaponChar(wi.type), bx + sz / 2, by + 23f, paint)
            // 名字
            paint.color = Color.WHITE; paint.textSize = 13f; paint.typeface = Typeface.DEFAULT
            c.drawText(wi.name, bx + sz / 2, by + sz - 12f, paint)
        }

        // 当前武器描述
        val wi = wlist[selectedWeapon]
        paint.color = Color.parseColor("#FFB0BEC5"); paint.textSize = 14f
        paint.textAlign = Paint.Align.LEFT
        c.drawText("▶ ${wi.name} (伤害${wi.damage.toInt()}) - ${wi.desc}", px, py + ph, paint)
    }

    private fun weaponChar(t: WType): String = when (t) {
        WType.ASTEROID -> "石"; WType.MISSILE -> "弹"; WType.LASER -> "激"
        WType.BLACKHOLE -> "洞"; WType.STARWORM -> "虫"; WType.NUKE -> "核"
        WType.METEOR_SHOWER -> "流"; WType.SATELLITE -> "卫"; WType.SWARM -> "群"
        WType.PLAGUE -> "疫"; WType.FREEZE -> "冰"; WType.SOLAR_FLARE -> "耀"
        WType.GRAVITY -> "引"; WType.SINGULARITY -> "奇"
    }

    private fun drawPlanetSelect(c: Canvas) {
        val pw = 420f; val ph = 420f
        val px = (w - pw) / 2; val py = (h - ph) / 2
        paint.color = 0x99000000.toInt(); c.drawRect(0f, 0f, w, h, paint)
        drawPanel(c, px, py, pw, ph, 12f)

        paint.textAlign = Paint.Align.CENTER; paint.color = Color.WHITE
        paint.textSize = 26f; paint.typeface = Typeface.DEFAULT_BOLD
        c.drawText("选择星球 / 系统", w / 2, py + 40f, paint)

        planetBtns.clear()
        val pCols = 2; val pRows = 4
        val sz = 160f; val gap = 15f
        val tx = px + (pw - (pCols * sz + (pCols - 1) * gap)) / 2
        val ty = py + 60f
        PlanetType.entries.forEachIndexed { i, pt ->
            val col = i % pCols; val row = i / pRows
            val bx = tx + col * (sz + gap)
            val by = ty + row * (sz + gap)
            if (by + sz > py + ph - 50f) return@forEachIndexed
            val selected = i == planetIdx
            val r = RectF(bx, by, bx + sz, by + sz)
            planetBtns.add(r)

            paint.style = Paint.Style.FILL
            paint.color = if (selected) Color.parseColor("#40FF6B35") else Color.parseColor("#55424242")
            c.drawRoundRect(r, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (selected) 3f else 1.5f
            paint.color = if (selected) Color.parseColor("#FFFF6B35") else Color.parseColor("#FF757575")
            c.drawRoundRect(r, 10f, 10f, paint)

            // 星球预览
            val cx = bx + sz / 2; val cy = by + sz * 0.4f
            paint.shader = RadialGradient(cx - 10f, cy - 10f, 40f,
                palettes[pt]!!.landHigh, palettes[pt]!!.oceanDeep, Shader.TileMode.CLAMP)
            paint.style = Paint.Style.FILL
            c.drawCircle(cx, cy, 38f, paint)
            paint.shader = null
            paint.color = 0x44FFFFFF.toInt()
            c.drawCircle(cx, cy, 50f, paint)

            // 名字
            paint.color = Color.WHITE; paint.textSize = 16f; paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText(pt.cnName, cx, by + sz - 35f, paint)
            paint.color = Color.parseColor("#FFB0BEC5"); paint.textSize = 12f; paint.typeface = Typeface.DEFAULT
            c.drawText("${pt.enName} | 人口 ${fmt(pt.population)}", cx, by + sz - 15f, paint)
        }

        // 关闭
        paint.color = 0xFFFF1744.toInt()
        c.drawCircle(px + pw - 30f, py + 25f, 18f, paint)
        paint.color = Color.WHITE; paint.textSize = 22f
        c.drawText("✕", px + pw - 30f, py + 32f, paint)
        uiButtons.add(Btn(px + pw - 48f, py + 7f, 36f, 36f) { showPlanetSelect = false })
    }

    private fun drawSettings(c: Canvas) {
        val pw = 320f; val ph = 280f
        val px = (w - pw) / 2; val py = (h - ph) / 2
        paint.color = 0x99000000.toInt(); c.drawRect(0f, 0f, w, h, paint)
        drawPanel(c, px, py, pw, ph, 12f)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.WHITE; paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT_BOLD; c.drawText("设置", w / 2, py + 40f, paint)
        paint.typeface = Typeface.DEFAULT; paint.textSize = 18f

        val labels = listOf(
            "游戏速度: ${when(speed){1f->"1x";2f->"2x";4f->"4x";else->"1x"}}",
            "伤害倍率: x$damageMultiplier",
            "粒子数量: 正常",
            "屏幕震动: 开启",
            "震动反馈: 开启"
        )
        labels.forEachIndexed { i, l ->
            paint.textAlign = Paint.Align.LEFT
            c.drawText(l, px + 20f, py + 80f + i * 35f, paint)
        }

        paint.textAlign = Paint.Align.CENTER; paint.color = Color.parseColor("#FF00E5FF"); paint.textSize = 14f
        c.drawText("点击空白处关闭", w / 2, py + ph - 20f, paint)
        uiButtons.add(Btn(px, py, pw, ph) { showSettings = false })
    }

    // ---- 按钮辅助 ----
    private fun drawPanel(c: Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC1A1A2E")
        c.drawRoundRect(x, y, x + w, y + h, 15f, 15f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#5500E5FF")
        paint.strokeWidth = 2f
        c.drawRoundRect(x, y, x + w, y + h, 15f, 15f, paint)
    }

    private fun drawIconBtn(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, accent: Int) {
        paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#CC1A1A2E")
        c.drawRoundRect(x, y, x + w, y + h, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = accent
        c.drawRoundRect(x, y, x + w, y + h, 10f, 10f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        paint.textSize = h * 0.45f; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
        val tr = android.graphics.Rect()
        paint.getTextBounds(label, 0, label.length, tr)
        c.drawText(label, x + w / 2, y + h / 2 + tr.height() / 2f, paint)
    }

    private fun fmt(n: Long): String = when {
        n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    // ---- 触控 ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                if (showPlanetSelect || showSettings) {
                    planetBtns.forEachIndexed { i, r ->
                        if (r.contains(x, y) && i < PlanetType.entries.size) {
                            planetIdx = i; initPlanet(); showPlanetSelect = false
                            vibrate(80); return true
                        }
                    }
                    // 检查 UI 按钮
                    for (b in uiButtons) {
                        if (x in b.x..b.x + b.w && y in b.y..b.y + b.h) { b.action(); vibrate(40); return true }
                    }
                    // 点空白处关闭弹窗
                    showPlanetSelect = false; showSettings = false; return true
                }

                for (b in uiButtons) {
                    if (x in b.x..b.x + b.w && y in b.y..b.y + b.h) { b.action(); vibrate(40); return true }
                }

                for ((r, i) in weaponBtns) {
                    if (r.contains(x, y)) { selectedWeapon = i; vibrate(30); return true }
                }

                val dc = sqrt((x - planet.centerX) * (x - planet.centerX) + (y - planet.centerY) * (y - planet.centerY))
                if (dc < planet.radius * 1.5f) {
                    dragging = true; lastDragX = x
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = x - lastDragX; lastDragX = x
                    planet.rotation += dx * 0.005f
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    val dc = sqrt((x - planet.centerX) * (x - planet.centerX) + (y - planet.centerY) * (y - planet.centerY))
                    if (dc > planet.radius * 1.5f) return true
                }
                val dc = sqrt((x - planet.centerX) * (x - planet.centerX) + (y - planet.centerY) * (y - planet.centerY))
                if (dc < planet.radius * 2f) {
                    fire(x, y); vibrate(100)
                }
            }
        }
        return true
    }

    private fun fire(tx: Float, ty: Float) {
        val wtype = Weapons.list[selectedWeapon].type
        val rx = if (planet.centerX - tx > 0) -100f + Random.nextFloat() * 50f
                 else w + 100f - Random.nextFloat() * 50f
        val ry = Random.nextFloat() * h * 0.5f
        val ox = Random.nextFloat() * w
        val oy = -50f + Random.nextFloat() * 100f

        val w: Weapon = when (wtype) {
            WType.ASTEROID -> Asteroid(ox, oy, tx, ty, 30f + Random.nextFloat() * 25f)
            WType.MISSILE -> Missile(rx, oy, tx, ty)
            WType.LASER -> Laser(w / 2, 0f, tx, ty)
            WType.BLACKHOLE -> BlackHole(tx, ty)
            WType.STARWORM -> StarWorm(planet, Random.nextFloat() * PI.toFloat() * 2f)
            WType.NUKE -> Nuke(tx, ty)
            WType.METEOR_SHOWER -> MeteorShower(planet, 12 + Random.nextInt(8))
            WType.SATELLITE -> Satellite(planet)
            WType.SWARM -> Swarm(planet)
            WType.PLAGUE -> Plague(planet)
            WType.FREEZE -> Freeze(planet)
            WType.SOLAR_FLARE -> SolarFlare(planet, 1.0f)
            WType.GRAVITY -> GravityWave(tx, ty)
            WType.SINGULARITY -> Singularity(planet)
        }
        activeWeapons.add(w)
    }
}

private fun sqrt(f: Float) = kotlin.math.sqrt(f)
