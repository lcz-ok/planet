package com.planet.simulator.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.*
import kotlin.random.Random

enum class WType(val iconRes: Int = 0) {
    ASTEROID, MISSILE, LASER, BLACKHOLE, STARWORM, NUKE, METEOR_SHOWER,
    SATELLITE, SWARM, PLAGUE, FREEZE, SOLAR_FLARE, GRAVITY, SINGULARITY
}

data class WeaponInfo(
    val type: WType,
    val name: String,
    val damage: Float,
    val accent: Int,
    val desc: String,
    val unlocked: Boolean = true
)

object Weapons {
    val list = listOf(
        WeaponInfo(WType.ASTEROID, "陨石", 100f, 0xFF6D4C41.toInt(), "从天而降的巨石"),
        WeaponInfo(WType.MISSILE, "导弹", 80f, 0xFFFF1744.toInt(), "精准制导导弹"),
        WeaponInfo(WType.LASER, "激光", 30f, 0xFF00E5FF.toInt(), "高能光束切割"),
        WeaponInfo(WType.BLACKHOLE, "黑洞", 500f, 0xFFD500F9.toInt(), "吞噬一切的奇点"),
        WeaponInfo(WType.STARWORM, "星虫", 250f, 0xFF2E7D32.toInt(), "撕裂星球的巨兽"),
        WeaponInfo(WType.NUKE, "核弹", 400f, 0xFFFFEA00.toInt(), "毁灭性核打击"),
        WeaponInfo(WType.METEOR_SHOWER, "流星群", 200f, 0xFFFF6B35.toInt(), "流星雨覆盖"),
        WeaponInfo(WType.SATELLITE, "卫星", 150f, 0xFFB0BEC5.toInt(), "轨道激光轰炸"),
        WeaponInfo(WType.SWARM, "虫群", 180f, 0xFF8BC34A.toInt(), "蝗虫般的吞噬"),
        WeaponInfo(WType.PLAGUE, "瘟疫", 120f, 0xFF4CAF50.toInt(), "致命传染病扩散"),
        WeaponInfo(WType.FREEZE, "冰封", 100f, 0xFF81D4FA.toInt(), "冰封星球表面"),
        WeaponInfo(WType.SOLAR_FLARE, "耀斑", 600f, 0xFFFF9800.toInt(), "恒星级毁灭"),
        WeaponInfo(WType.GRAVITY, "引力波", 350f, 0xFF7E57C2.toInt(), "时空扰动"),
        WeaponInfo(WType.SINGULARITY, "奇点", 1000f, 0xFF1A1A2E.toInt(), "终极毁灭"),
    )
}

abstract class Weapon {
    open var active: Boolean = true
    abstract fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float
    abstract fun draw(canvas: Canvas)
}

// 陨石
class Asteroid(val sx: Float, val sy: Float, val tx: Float, val ty: Float, val size: Float = 38f) : Weapon() {
    private var x = sx; private var y = sy
    private val dist = sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy))
    private val speed = 350f
    private val vx = (tx - sx) / dist * speed
    private val vy = (ty - sy) / dist * speed
    private var rot = Random.nextFloat() * PI.toFloat() * 2f
    private val rotSp = (Random.nextFloat() - 0.5f) * 4f

    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        x += vx * dt; y += vy * dt; rot += rotSp * dt
        ps.trail(x, y, vx, vy)
        if (sqrt((x - planet.centerX) * (x - planet.centerX) + (y - planet.centerY) * (y - planet.centerY)) < planet.radius + size * 0.3f) {
            active = false; ps.explode(x, y, size / 30f)
            planet.screenToPlanet(x, y)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), size / 40f) }
            return 100f * (size / 40f)
        }
        if (x < -200 || x > 3000 || y < -200 || y > 3000) active = false
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x55FF6B35.toInt()
        canvas.drawCircle(x, y, size * 1.5f, p)
        p.color = 0xFF6D4C41.toInt()
        canvas.save(); canvas.translate(x, y); canvas.rotate(rot * 180f / PI.toFloat())
        canvas.drawCircle(0f, 0f, size * 0.8f, p)
        p.color = 0xFF8D6E63.toInt()
        canvas.drawCircle(-size * 0.2f, -size * 0.2f, size * 0.35f, p)
        p.color = 0xFF3E2723.toInt()
        canvas.drawCircle(size * 0.25f, size * 0.1f, size * 0.15f, p)
        canvas.restore()
    }
}

// 导弹（带追踪）
class Missile(val sx: Float, val sy: Float, val tx: Float, val ty: Float) : Weapon() {
    private var x = sx; private var y = sy
    private var angle = atan2(ty - sy, tx - sx)
    private val speed = 480f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        val dx = tx - x; val dy = ty - sy
        angle += (atan2(dy, dx) - angle).coerceIn(-2.5f * dt, 2.5f * dt)
        x += cos(angle) * speed * dt; y += sin(angle) * speed * dt
        ps.trail(x, y, cos(angle) * speed, sin(angle) * speed, 0xFFFFEA00.toInt())
        val d2 = (x - planet.centerX) * (x - planet.centerX) + (y - planet.centerY) * (y - planet.centerY)
        if (d2 < (planet.radius + 12f) * (planet.radius + 12f) || dx * dx + dy * dy < 100f) {
            active = false; ps.explode(x, y, 1.5f)
            planet.screenToPlanet(x, y)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.9f) }
            return 80f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle * 180f / PI.toFloat())
        p.color = 0xFFB0BEC5.toInt(); canvas.drawRoundRect(-24f, -4f, 16f, 4f, 2f, 2f, p)
        p.color = 0xFFFF1744.toInt()
        val path = android.graphics.Path()
        path.moveTo(16f, 0f); path.lineTo(6f, -9f); path.lineTo(6f, 9f); path.close()
        canvas.drawPath(path, p)
        p.color = 0xFFFFEA00.toInt(); canvas.drawCircle(-26f, 0f, 3f, p)
        canvas.restore()
    }
}

// 激光
class Laser(val sx: Float, val sy: Float, val tx: Float, val ty: Float) : Weapon() {
    private var life = 0.9f; private var done = false
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        life -= dt; if (life <= 0f) active = false
        if (!done) {
            done = true
            planet.screenToPlanet(tx, ty)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.5f) }
            ps.explode(tx, ty, 0.8f)
            return 30f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.strokeCap = Paint.Cap.ROUND }
        p.color = 0x3300E5FF.toInt(); p.strokeWidth = 22f; canvas.drawLine(sx, sy, tx, ty, p)
        p.color = 0xAA00E5FF.toInt(); p.strokeWidth = 12f; canvas.drawLine(sx, sy, tx, ty, p)
        p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 4f; canvas.drawLine(sx, sy, tx, ty, p)
    }
}

// 黑洞
class BlackHole(val x: Float, val y: Float, val duration: Float = 3.5f) : Weapon() {
    private var life = duration
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        life -= dt; if (life <= 0f) active = false
        val prog = 1f - life / duration
        val r = 80f * prog
        if (prog > 0.15f) {
            planet.screenToPlanet(x, y)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.04f) }
            if (Random.nextFloat() < 0.5f) {
                val a = Random.nextFloat() * PI.toFloat() * 2f
                val rand = Random(System.nanoTime())
                ps.addParticle(Particle(
                    x + cos(a) * r * 1.5f, y + sin(a) * r * 1.5f,
                    -cos(a) * 200f + rand.nextFloat() * 40f - 20f,
                    -sin(a) * 200f + rand.nextFloat() * 40f - 20f,
                    0.4f, 0.4f, 3f, 0xFFFF6B35.toInt(), type = PType.FLAME
                ))
            }
        }
        ps.ring(x, y, r * 1.2f, 5, 0xFFD500F9.toInt())
        return 2f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val prog = 1f - life / 3.5f; val r = 80f * prog
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x33D500F9.toInt(); canvas.drawCircle(x, y, r * 1.5f, p)
        canvas.save(); canvas.translate(x, y); canvas.rotate(prog * 720f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 10f; p.color = 0x66FF6B35.toInt()
        canvas.drawArc(-r * 1.3f, -r * 1.3f, r * 1.3f, r * 1.3f, 0f, 300f, false, p)
        p.color = 0xFFD500F9.toInt(); p.strokeWidth = 4f
        canvas.drawArc(-r * 0.9f, -r * 0.9f, r * 0.9f, r * 0.9f, 120f, 240f, false, p)
        canvas.restore()
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        canvas.drawCircle(x, y, r * 0.5f, p)
    }
}

// 星虫
class StarWorm(val planet: Planet, entryAngle: Float) : Weapon() {
    private var t = 0f; private val dur = 3.5f
    private val path: List<Pair<Float, Float>> = run {
        val pts = mutableListOf<Pair<Float, Float>>()
        for (i in 0..40) {
            val a = entryAngle + i * 0.35f
            val d = (1f + sin(i * 0.7f) * 0.5f + cos(i * 0.3f) * 0.3f).coerceIn(-0.8f, 0.95f)
            pts.add(Pair(a, d))
        }
        pts
    }
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        t += dt / dur; if (t >= 1f) active = false
        val idx = (t * path.size).toInt().coerceIn(0, path.size - 1)
        val (a, d) = path[idx]
        val px = planet.centerX + cos(a) * (d * planet.radius + planet.radius * 0.3f)
        val py = planet.centerY + sin(a) * (d * planet.radius + planet.radius * 0.3f) * 0.98f
        planet.screenToPlanet(px, py)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.1f) }
        ps.explode(px, py, 0.4f)
        return 2f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 15 downTo 0) {
            val pt = ((t - i * 0.025f) * path.size).toInt().coerceIn(0, path.size - 1)
            val (a, d) = path[pt]
            val x = planet.centerX + cos(a) * (d * planet.radius + planet.radius * 0.3f)
            val y = planet.centerY + sin(a) * (d * planet.radius + planet.radius * 0.3f) * 0.98f
            val size = 14f - i * 0.6f; val alpha = ((1f - i / 15f) * 255).toInt()
            p.color = 0xFF1B5E20.toInt(); p.alpha = alpha
            canvas.drawCircle(x, y, size, p)
            p.color = 0xFF4CAF50.toInt(); p.alpha = (alpha * 0.6f).toInt()
            canvas.drawCircle(x, y, size * 0.5f, p)
        }
        p.alpha = 255
    }
}

// 核弹
class Nuke(val x: Float, val y: Float) : Weapon() {
    private var t = 0f; private var done = false
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        t += dt; if (t >= 1.8f) active = false
        if (!done && t > 0.1f) {
            done = true
            for (i in 0..48 step 2) {
                val a = i * PI.toFloat() / 24f
                planet.screenToPlanet(x + cos(a) * planet.radius * 0.5f, y + sin(a) * planet.radius * 0.5f)
                    ?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.5f) }
            }
            ps.explode(x, y, 10f)
            return 400f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val r = 250f * (t / 1.8f).coerceAtMost(1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x33FF6B35.toInt(); canvas.drawCircle(x, y, r, p)
        p.color = 0x66FFFFEA00.toInt(); canvas.drawCircle(x, y, r * 0.7f, p)
        p.color = 0xAAFFFFFF.toInt(); canvas.drawCircle(x, y, r * 0.35f, p)
        // 蘑菇茎
        p.color = 0x55FF6B35.toInt()
        canvas.drawRect(x - r * 0.1f, y - r * 0.2f, x + r * 0.1f, y, p)
    }
}

// 流星群
class MeteorShower(val planet: Planet, count: Int = 15) : Weapon() {
    val meteors = mutableListOf<Asteroid>()
    init {
        val rand = Random(System.nanoTime())
        repeat(count) {
            val ta = rand.nextFloat() * PI.toFloat() * 2f
            val td = rand.nextFloat() * 0.9f
            meteors.add(Asteroid(
                rand.nextFloat() * planet.centerX, -80f + rand.nextFloat() * 100f,
                planet.centerX + cos(ta) * td * planet.radius,
                planet.centerY + sin(ta) * td * planet.radius,
                20f + rand.nextFloat() * 30f))
        }
    }
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        if (!active) return 0f
        var dmg = 0f
        for (m in meteors) dmg += m.update(dt, planet, ps)
        meteors.removeAll { !it.active }
        if (meteors.isEmpty()) active = false
        return dmg
    }
    override fun draw(canvas: Canvas) { meteors.forEach { it.draw(canvas) } }
}

// 卫星
class Satellite(val planet: Planet) : Weapon() {
    private var angle = Random.nextFloat() * PI.toFloat() * 2f
    private var life = 8f; private var cooldown = 0f
    private val orbit = planet.radius * 1.4f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        angle += 0.7f * dt; life -= dt; cooldown += dt
        if (life <= 0f) active = false
        if (cooldown >= 0.35f) {
            cooldown = 0f
            val ta = Random.nextFloat() * PI.toFloat() * 2f
            val tx = planet.centerX + cos(ta) * planet.radius * Random.nextFloat() * 0.9f
            val ty = planet.centerY + sin(ta) * planet.radius * Random.nextFloat() * 0.9f
            val sx = planet.centerX + cos(angle) * orbit
            val sy = planet.centerY + sin(angle) * orbit
            ps.trail(sx, sy, (tx - sx) * 4f, (ty - sy) * 4f, 0xFF00E5FF.toInt())
            planet.screenToPlanet(tx, ty)?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.35f) }
            ps.explode(tx, ty, 0.6f)
            return 20f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val x = planet.centerX + cos(angle) * orbit
        val y = planet.centerY + sin(angle) * orbit
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFB0BEC5.toInt(); canvas.drawRoundRect(x - 14f, y - 5f, x + 14f, y + 5f, 2f, 2f, p)
        p.color = 0xFF455A64.toInt(); canvas.drawRect(x - 30f, y - 3f, x - 14f, y + 3f, p)
        canvas.drawRect(x + 14f, y - 3f, x + 30f, y + 3f, p)
    }
}

// 虫群
class Swarm(val planet: Planet) : Weapon() {
    private val bugs = mutableListOf<Triple<Float, Float, Float>>()
    init {
        val rand = Random(System.nanoTime())
        repeat(40) {
            bugs.add(Triple(rand.nextFloat() * PI.toFloat() * 2f, 1f, 3f + rand.nextFloat() * 2f))
        }
    }
    private var phase = 0f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        phase += dt; if (phase > 6f) active = false
        var dmg = 0f
        val next = mutableListOf<Triple<Float, Float, Float>>()
        for ((a, d, l) in bugs) {
            val nd = d - dt * 0.45f; val nl = l - dt
            if (nl > 0 && nd > 0f) {
                next.add(Triple(a + dt * 0.3f, nd, nl))
                if (nd < 1f) {
                    planet.addDamage(a, (1 - nd).coerceAtMost(1f), 0.03f)
                    dmg += 4f
                    if (Random.nextFloat() < 0.2f) {
                        val px = planet.centerX + cos(a) * planet.radius * nd
                        val py = planet.centerY + sin(a) * planet.radius * nd
                        ps.trail(px, py, 0f, 0f, 0xFF8BC34A.toInt())
                    }
                }
            }
        }
        bugs.clear(); bugs.addAll(next)
        return dmg
    }
    override fun draw(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((a, d, l) in bugs) {
            val x = planet.centerX + cos(a) * planet.radius * d
            val y = planet.centerY + sin(a) * planet.radius * d
            p.color = 0xFF8BC34A.toInt(); canvas.drawCircle(x, y, 4f, p)
            p.color = 0xFF1B5E20.toInt(); canvas.drawCircle(x, y, 2f, p)
        }
    }
}

// 瘟疫
class Plague(val planet: Planet) : Weapon() {
    private var phase = 0f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        phase += dt; if (phase > 12f) active = false
        val rand = Random(System.nanoTime())
        for (i in 0..24) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val d = rand.nextFloat() * 0.85f
            planet.addDamage(a, d, 0.015f)
        }
        if (rand.nextFloat() < 0.3f) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val d = rand.nextFloat()
            ps.trail(planet.centerX + cos(a) * planet.radius * d,
                     planet.centerY + sin(a) * planet.radius * d, 0f, 0f, 0xFF4CAF50.toInt())
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {}
}

// 冻结
class Freeze(val planet: Planet) : Weapon() {
    private var phase = 0f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        phase += dt; if (phase > 5f) active = false
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x4481D4FA.toInt()
        canvas.drawCircle(planet.centerX, planet.centerY, planet.radius, p)
        p.color = 0x66FFFFFF.toInt()
        canvas.drawCircle(planet.centerX, planet.centerY, planet.radius * 0.5f, p)
    }
}

// 太阳耀斑
class SolarFlare(val planet: Planet, val delay: Float = 1.0f) : Weapon() {
    private var t = 0f; private var fired = false
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        t += dt
        if (t < delay) return 0f
        if (!fired) {
            fired = true
            for (i in 0..72 step 2) {
                val a = i * PI.toFloat() / 36f
                planet.screenToPlanet(
                    planet.centerX + cos(a) * planet.radius * 0.9f,
                    planet.centerY + sin(a) * planet.radius * 0.9f
                )?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.55f) }
            }
            ps.explode(planet.centerX, planet.centerY, 8f)
            active = false; return 600f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (t < 1.0f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val alpha = (t / 1.0f * 120).toInt().coerceIn(0, 120)
            p.color = alpha shl 24 or 0xFFFF9800.toInt()
            canvas.drawCircle(planet.centerX, planet.centerY, planet.radius * (1.2f + t), p)
        }
    }
}

// 引力波
class GravityWave(val x: Float, val y: Float) : Weapon() {
    private var t = 0f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        t += dt; if (t > 2f) active = false
        val radius = 400f * (t / 2f)
        if (t < 2f && t > 0.1f) {
            val dmg = 350f / 10f
            for (i in 0..16) {
                val a = i * PI.toFloat() / 8f
                planet.screenToPlanet(x + cos(a) * radius * 0.5f, y + sin(a) * radius * 0.5f)
                    ?.let { planet.addDamage(it.first, (sin(it.first) * 0.5f + 0.5f).coerceAtLeast(0.1f), 0.4f) }
            }
        }
        generateSequence(0f) { it + 0.1f }.takeWhile { it < 2f }.forEach {
            val rad = 400f * (it / 2f)
            ps.ring(x, y, rad, 8, 0xFF7E57C2.toInt())
        }
        return if (t < 0.1f) 0f else 35f
    }
    override fun draw(canvas: Canvas) {
        if (!active) return
        val r = 400f * (t / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.STROKE }
        p.color = 0x667E57C2.toInt(); p.strokeWidth = 20f; canvas.drawCircle(x, y, r, p)
        p.color = 0xAAFFFFFF.toInt(); p.strokeWidth = 4f; canvas.drawCircle(x, y, r * 0.7f, p)
    }
}

// 奇点
class Singularity(val planet: Planet) : Weapon() {
    private var t = 0f
    override fun update(dt: Float, planet: Planet, ps: ParticleSystem): Float {
        t += dt
        if (t < 1.5f) return 0f
        if (t < 1.6f) {
            for (i in 0..100) {
                val a = Random.nextFloat() * PI.toFloat() * 2f
                val d = Random.nextFloat()
                planet.addDamage(a, d, 0.8f)
            }
            ps.explode(planet.centerX, planet.centerY, 20f)
            active = false; return 1000f
        }
        return 0f
    }
    override fun draw(canvas: Canvas) {
        if (t < 1.5f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val prog = t / 1.5f
            p.color = 0x55000000.toInt(); canvas.drawCircle(planet.centerX, planet.centerY, planet.radius * prog, p)
            p.color = 0xFF1A1A2E.toInt(); canvas.drawCircle(planet.centerX, planet.centerY, planet.radius * prog * 0.7f, p)
        }
    }
}
