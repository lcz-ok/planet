package com.planet.simulator.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.random.Random

enum class PType { SPARK, SMOKE, ROCK, FLAME, GLOW, SHOCK, DEBRIS, GLASS }

data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, var maxLife: Float,
    var size: Float,
    var color: Int,
    var gravity: Float = 0f,
    var type: PType = PType.SPARK,
    var rotation: Float = 0f,
    var rotSpeed: Float = 0f,
    var brightness: Float = 1f
) {
    val alive get() = life > 0f
    fun update(dt: Float) {
        x += vx * dt; y += vy * dt
        vy += gravity * dt
        vx *= 0.98f; vy *= 0.98f
        rotation += rotSpeed * dt
        life -= dt
    }
    fun draw(c: Canvas, p: Paint) {
        val alpha = ((life / maxLife) * 255 * brightness).toInt().coerceIn(0, 255)
        p.color = color
        p.alpha = alpha
        p.style = Paint.Style.FILL
        when (type) {
            PType.SMOKE -> {
                val r = size * (1 + (1 - life/maxLife) * 1.5f)
                c.drawCircle(x, y, r, p)
            }
            PType.ROCK -> {
                c.save()
                c.translate(x, y)
                c.rotate(rotation * 180f / PI.toFloat())
                val s = size * 0.7f
                c.drawRect(-s, -s * 0.6f, s, s * 0.6f, p)
                c.restore()
            }
            PType.FLAME -> {
                p.alpha = (alpha * 0.5f).toInt()
                c.drawCircle(x, y, size * 1.5f, p)
                p.alpha = alpha
                c.drawCircle(x, y, size, p)
            }
            PType.GLOW -> {
                p.alpha = (alpha * 0.2f).toInt()
                c.drawCircle(x, y, size * 3f, p)
                p.alpha = (alpha * 0.5f).toInt()
                c.drawCircle(x, y, size * 1.5f, p)
                p.alpha = alpha
                c.drawCircle(x, y, size, p)
            }
            PType.SHOCK -> {
                val r = size * (1 + (1 - life/maxLife) * 3f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = size * 0.3f
                c.drawCircle(x, y, r, p)
            }
            PType.DEBRIS -> {
                c.save()
                c.translate(x, y)
                c.rotate(rotation * 180f / PI.toFloat())
                p.strokeWidth = size * 0.15f
                c.drawLine(-size, -size, size, size, p)
                c.drawLine(-size, size, size, -size, p)
                c.restore()
            }
            PType.GLASS -> {
                val colors = intArrayOf(Color.WHITE, 0xFFFFEA00.toInt())
                c.drawCircle(x, y, size, p)
            }
            PType.SPARK -> c.drawCircle(x, y, size, p)
        }
        p.alpha = 255; p.style = Paint.Style.FILL
    }
}

class ParticleSystem {
    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val count get() = particles.size

    fun explode(x: Float, y: Float, power: Float = 1f) {
        val rand = Random(System.nanoTime())
        // 火花
        repeat((40 * power).toInt()) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val sp = (60f + rand.nextFloat() * 300f) * power
            particles.add(Particle(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp,
                0.6f + rand.nextFloat() * 0.6f, 1.2f, 2f + rand.nextFloat() * 5f,
                when (rand.nextInt(3)) { 0 -> 0xFFFF6B35.toInt(); 1 -> 0xFFFFEA00.toInt(); else -> 0xFFFFFFFF.toInt() },
                type = PType.FLAME))
        }
        // 烟雾
        repeat((15 * power).toInt()) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val sp = (20f + rand.nextFloat() * 80f) * power
            particles.add(Particle(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp - 40f,
                1.8f + rand.nextFloat() * 1.5f, 3.3f, 12f + rand.nextFloat() * 15f,
                0xFF424242.toInt(), type = PType.SMOKE, gravity = -20f))
        }
        // 岩石
        repeat((12 * power).toInt()) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val sp = (100f + rand.nextFloat() * 250f) * power
            particles.add(Particle(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp,
                1.2f + rand.nextFloat(), 2.2f, 4f + rand.nextFloat() * 8f,
                0xFF6D4C41.toInt(), type = PType.ROCK, gravity = 100f, rotSpeed = rand.nextFloat() * 6f))
        }
        // 冲击波
        particles.add(Particle(x, y, 0f, 0f, 0.5f, 0.5f, 30f * power, 0xFFFFEA00.toInt(), type = PType.SHOCK))
        particles.add(Particle(x, y, 0f, 0f, 0.7f, 0.7f, 60f * power, 0xFFFF6B35.toInt(), type = PType.SHOCK))
        // 核心光晕
        particles.add(Particle(x, y, 0f, 0f, 0.4f, 0.4f, 40f * power, 0xFFFFFFFF.toInt(), type = PType.GLOW, brightness = 1f))
    }

    fun trail(x: Float, y: Float, vx: Float, vy: Float, color: Int = 0xFFFF6B35.toInt()) {
        val rand = Random(System.nanoTime())
        particles.add(Particle(
            x + rand.nextFloat() * 10f - 5f,
            y + rand.nextFloat() * 10f - 5f,
            -vx * 0.2f + rand.nextFloat() * 30f - 15f,
            -vy * 0.2f + rand.nextFloat() * 30f - 15f,
            0.35f + rand.nextFloat() * 0.3f, 0.65f, 4f + rand.nextFloat() * 5f,
            color, type = PType.FLAME))
        if (rand.nextFloat() < 0.4f) {
            particles.add(Particle(
                x, y, rand.nextFloat() * 20f - 10f, rand.nextFloat() * 20f - 10f - 20f,
                0.6f + rand.nextFloat() * 0.4f, 1f, 8f + rand.nextFloat() * 6f,
                0xFF616161.toInt(), type = PType.SMOKE))
        }
    }

    fun ring(x: Float, y: Float, radius: Float, count: Int = 30, color: Int = 0xFFFFEA00.toInt()) {
        val rand = Random(System.nanoTime())
        repeat(count) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            particles.add(Particle(
                x + kotlin.math.cos(a) * radius,
                y + kotlin.math.sin(a) * radius,
                kotlin.math.cos(a) * (30f + rand.nextFloat() * 120f),
                kotlin.math.sin(a) * (30f + rand.nextFloat() * 120f),
                0.6f + rand.nextFloat() * 0.5f, 1.1f, 2f + rand.nextFloat() * 4f,
                color, type = PType.SPARK))
        }
    }

    fun debris(x: Float, y: Float, count: Int = 20) {
        val rand = Random(System.nanoTime())
        repeat(count) {
            val a = rand.nextFloat() * PI.toFloat() * 2f
            val sp = 80f + rand.nextFloat() * 200f
            particles.add(Particle(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp,
                1f + rand.nextFloat(), 2f, 4f + rand.nextFloat() * 5f,
                0xFFFFF.toInt(), type = PType.DEBRIS))
        }
    }

    fun update(dt: Float) {
        for (p in particles) p.update(dt)
        particles.removeAll { !it.alive }
    }

    fun draw(canvas: Canvas) {
        for (p in particles) p.draw(canvas, paint)
    }

    fun clear() = particles.clear()
    fun addParticle(p: Particle) = particles.add(p)
}
