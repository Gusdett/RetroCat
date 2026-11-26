package com.retrocat.tamagotchi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

/**
 * The whole game screen. Everything is drawn on a logical grid that is
 * [W] "pixels" wide and scaled up by a whole number, so pixels stay crisp.
 */
class GameView(context: Context, private val pet: Pet) : View(context) {

    private enum class Mode { INTRO, ROOM, CATCH }
    private class Heart(var x: Float, var y: Float, var life: Int)
    private class Fish(var x: Float, var y: Float, val vy: Float)

    var onSave: (() -> Unit)? = null
    /** Asks the user for a name (shows an Android dialog) and calls back with the text. */
    var onAskName: ((String, (String) -> Unit) -> Unit)? = null

    // ------------------------------------------------------------- colours
    private fun argb(v: Long) = v.toInt()
    private val BG = argb(0xFFF7F5F0)
    private val INK = argb(0xFF2B1B17)
    private val WHITE = argb(0xFFFFFFFF)
    private val RED = argb(0xFFD64545)
    private val ORANGE = argb(0xFFF2A65A)
    private val MUTED = argb(0xFF9A9286)

    private fun mono(fill: Int) = intArrayOf(fill, fill, fill, INK)

    // fur colours: [belly/cheeks, body, stripes, outline]
    private val coats = arrayOf(
        intArrayOf(WHITE, ORANGE, argb(0xFFC96F2B), INK),
        intArrayOf(WHITE, argb(0xFFB4BBC6), argb(0xFF7A8494), INK),
        intArrayOf(WHITE, argb(0xFF5E5E6B), argb(0xFF3A3A46), INK),
        intArrayOf(WHITE, argb(0xFFFAF0DC), argb(0xFFDCC9A6), INK),
        intArrayOf(WHITE, argb(0xFFB07A50), argb(0xFF7A4E2D), INK),
    )
    private val coatNames = arrayOf("ORANGE", "GRAY", "BLACK", "CREAM", "BROWN")
    private fun palCat() = coats[pet.coat.coerceIn(0, coats.size - 1)]
    private val palEgg = intArrayOf(WHITE, argb(0xFFF4E9D0), argb(0xFFD9C48F), INK)
    private val palGhost = intArrayOf(argb(0xFFE6ECF8), argb(0xFFE6ECF8), argb(0xFFE6ECF8), INK)
    private val palFish = intArrayOf(WHITE, argb(0xFF6FA8DC), argb(0xFF3D6FA3), INK)
    private val palPoop = intArrayOf(WHITE, argb(0xFFA0662E), argb(0xFF7A4A1E), argb(0xFF3B2412))
    private val palRug = intArrayOf(WHITE, argb(0xFFF4B6B6), argb(0xFFE58585), argb(0xFF8A3A3A))
    private val palInk = intArrayOf(INK, INK, INK, INK)
    private val palHeart = intArrayOf(RED, RED, RED, RED)
    private val palMoon = mono(argb(0xFFF6C945))

    // ------------------------------------------------------------- geometry
    private val W = 120
    private var lh = 200
    private var scale = 8
    private var ox = 0f
    private var oy = 0f

    private val hudBottom = 22
    private val roomTop = 34
    private val btnH = 26
    private val btnTop get() = lh - btnH - 4
    private val msgY get() = btnTop - 9
    private val roomBottom get() = msgY - 3
    private val floorH get() = ((roomBottom - roomTop) * 0.34f).toInt().coerceAtLeast(22)
    private val floorY get() = roomBottom - floorH
    private val feetY get() = floorY + floorH / 2 + 4   // where the pet's feet touch the floor

    // ------------------------------------------------------------- state
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private var mode = Mode.INTRO   // every launch starts at the title screen
    private var running = false
    private var lastMs = System.currentTimeMillis()
    private var tickCount = 0

    private var catX = 40f
    private var dir = 1
    private var speed = 0f
    private var walkTimer = 20
    private var blinkTimer = 30
    private var blinkLen = 0
    private var happyTimer = 0
    private var eatTimer = 0
    private var wiggle = 0
    private val hearts = ArrayList<Heart>()

    // title screen decoration
    private val notes = ArrayList<Heart>()
    private val starX = IntArray(14)
    private val starY = IntArray(14)
    private val starPh = IntArray(14)
    private val noteSprite = Sprite.of(
        "..#..",
        "..##.",
        "..#.#",
        "..#..",
        "..#..",
        ".##..",
        "###..",
        ".##..",
    )

    init {
        val r = Random(7)
        for (i in starX.indices) {
            starX[i] = r.nextInt(7, W - 7)
            starY[i] = r.nextInt(7, 100)
            starPh[i] = r.nextInt(3)
        }
    }
    private var msg = ""
    private var msgTimer = 0
    private var pressIdx = -1
    private var pressTimer = 0
    private var needsTimer = 0
    private var saveTimer = 0

    // mini-game
    private val gameTicksMax = 250
    private val fishes = ArrayList<Fish>()
    private var score = 0
    private var misses = 0
    private var gameTicks = 0
    private var spawnT = 0
    private var targetX = 40f

    private val btnLabels = arrayOf("FEED", "PLAY", "SLEEP", "CLEAN", "HEAL")

    init {
        pet.onEvent = { handleEvent(it) }
    }

    // ------------------------------------------------------------- loop
    fun resume() {
        if (running) return
        running = true
        lastMs = System.currentTimeMillis()
        if (mode == Mode.INTRO) Chip.startMusic()
        post(loop)
    }

    fun pause() {
        running = false
        removeCallbacks(loop)
        Chip.stopMusic()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            val dt = ((now - lastMs) / 1000f).coerceIn(0f, 5f)
            lastMs = now
            pet.tick(dt)
            step()
            invalidate()
            postDelayed(this, 100)
        }
    }

    private fun handleEvent(e: PetEvent) {
        when (e) {
            PetEvent.HATCHED -> { Chip.play(Snd.HATCH); say("IT HATCHED!"); burst(5) }
            PetEvent.GREW -> { Chip.play(Snd.GROW); say("ALL GROWN UP!"); burst(6) }
            PetEvent.POOPED -> { Chip.play(Snd.POOP); say("OOPS! CLEAN UP") }
            PetEvent.SICK -> { Chip.play(Snd.SICK); say("GOT SICK! HEAL IT") }
            PetEvent.DIED -> { Chip.play(Snd.DEATH); mode = Mode.ROOM; say("GOODBYE...", 60) }
            PetEvent.WOKE -> { Chip.play(Snd.MEOW); say("GOOD MORNING!") }
        }
    }

    private fun say(s: String, ticks: Int = 25) {
        msg = s
        msgTimer = ticks
    }

    private fun statusText(): String = when {
        pet.stage == Stage.EGG -> "HATCHING..."
        pet.stage == Stage.DEAD -> "GONE TO CAT HEAVEN"
        pet.sleeping -> "ZZZ..."
        pet.sick -> "I FEEL SICK..."
        pet.hunger < 25f -> "SO HUNGRY!"
        pet.poops >= 2 -> "IT SMELLS!"
        pet.energy < 20f -> "SO SLEEPY..."
        pet.happy < 25f -> "SO LONELY..."
        pet.happy > 75f -> "PURRR"
        else -> "MEOW"
    }

    // ------------------------------------------------------------- per-tick update
    private fun petSprites() = if (pet.stage == Stage.KITTEN) Art.kitten else Art.cat
    private fun petW() = petSprites().base.w * 2
    private fun petH() = petSprites().base.h * 2
    private fun petY() = feetY - petH() - (if (speed > 0f && tickCount % 2 == 0) 1 else 0)

    private fun burst(n: Int) {
        val w = petW()
        repeat(n) {
            hearts.add(Heart(catX + w / 2f + Random.nextInt(-14, 14), petY().toFloat() - 2f - Random.nextInt(0, 8), 14))
        }
    }

    private fun step() {
        tickCount++
        if (msgTimer > 0) msgTimer--
        if (pressTimer > 0) pressTimer--
        if (eatTimer > 0) eatTimer--
        if (happyTimer > 0) happyTimer--
        if (wiggle > 0) wiggle--

        if (blinkLen > 0) {
            blinkLen--
        } else if (--blinkTimer <= 0) {
            blinkLen = 2
            blinkTimer = Random.nextInt(25, 60)
        }

        val it = hearts.iterator()
        while (it.hasNext()) {
            val h = it.next()
            h.y -= 1.5f
            if (--h.life <= 0) it.remove()
        }

        if (mode == Mode.INTRO) {
            if (tickCount % 5 == 0) {
                notes.add(Heart(W / 2f + Random.nextInt(-26, 22), introFeet() - 64f, 30))
            }
            val ni = notes.iterator()
            while (ni.hasNext()) {
                val n = ni.next()
                n.y -= 1.2f
                n.x += if ((n.life / 4) % 2 == 0) 0.6f else -0.6f
                if (--n.life <= 0) ni.remove()
            }
        }

        if (pet.stage == Stage.EGG && pet.stageSec > Pet.EGG_SEC * 0.4f && tickCount % 40 == 0) wiggle = 8

        val w = petW()
        if (mode == Mode.CATCH) {
            updateGame()
        } else if (pet.alive && !pet.sleeping && eatTimer == 0) {
            if (--walkTimer <= 0) {
                if (Random.nextInt(100) < 45) {
                    speed = 0f
                    walkTimer = Random.nextInt(15, 40)
                } else {
                    speed = 1f
                    dir = if (Random.nextBoolean()) 1 else -1
                    walkTimer = Random.nextInt(10, 30)
                }
            }
            catX += dir * speed
        } else {
            speed = 0f
        }
        val maxX = (W - w - 4).toFloat()
        if (catX < 4f) { catX = 4f; dir = 1 }
        if (catX > maxX) { catX = maxX; dir = -1 }

        if (pet.alive && !pet.sleeping && mode == Mode.ROOM && ++needsTimer >= 600) {
            needsTimer = 0
            val needy = pet.hunger < 30f || pet.happy < 30f || pet.poops > 1 || pet.sick || pet.energy < 20f
            if (needy) {
                Chip.play(Snd.MEOW)
                say(statusText(), 30)
            }
        }
        if (++saveTimer >= 300) {
            saveTimer = 0
            onSave?.invoke()
        }
    }

    // ------------------------------------------------------------- mini-game: catch the fish
    private fun startGame() {
        mode = Mode.CATCH
        fishes.clear()
        score = 0
        misses = 0
        gameTicks = 0
        spawnT = 5
        catX = (W - petW()) / 2f
        targetX = catX
        dir = 1
        say("CATCH THE FISH!", 20)
    }

    private fun updateGame() {
        gameTicks++
        val w = petW()
        if (--spawnT <= 0) {
            val vy = 2.5f + minOf(3f, score * 0.15f)
            fishes.add(Fish(Random.nextInt(4, W - 4 - 26).toFloat(), roomTop + 2f, vy))
            spawnT = Random.nextInt(8, 14)
        }
        val dx = targetX - catX
        catX += dx.coerceIn(-6f, 6f)
        speed = if (kotlin.math.abs(dx) > 1f) 1f else 0f
        if (dx < -0.5f) dir = -1 else if (dx > 0.5f) dir = 1
        catX = catX.coerceIn(2f, (W - w - 2).toFloat())

        val top = petY()
        val it = fishes.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.y += f.vy
            val bottom = f.y + 14f
            val overlapX = f.x + 26f > catX + 4f && f.x < catX + w - 4f
            if (bottom >= top + 6f && f.y <= top + 22f && overlapX) {
                score++
                Chip.play(Snd.CATCH)
                it.remove()
            } else if (f.y > feetY.toFloat()) {
                misses++
                Chip.play(Snd.MISS)
                it.remove()
            }
        }
        if (gameTicks >= gameTicksMax || misses >= 5) endGame()
    }

    private fun endGame() {
        pet.rewardPlay(score)
        mode = Mode.ROOM
        fishes.clear()
        if (score > 0) {
            Chip.play(if (score >= 3) Snd.WIN else Snd.HAPPY)
            happyTimer = 15
            burst(minOf(6, score + 1))
            say("SCORE $score  YAY!", 35)
        } else {
            Chip.play(Snd.SAD)
            say("SCORE 0... AWW", 35)
        }
    }

    // ------------------------------------------------------------- touch
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val lx = (e.x - ox) / scale
        val ly = (e.y - oy) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down(lx, ly)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.CATCH) targetX = lx - petW() / 2f
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun hitButton(lx: Float, ly: Float): Int {
        if (ly < btnTop || ly > btnTop + btnH) return -1
        val i = ((lx - 1f) / 24f).toInt()
        if (i < 0 || i > 4) return -1
        val x0 = 1f + i * 24f
        return if (lx >= x0 && lx <= x0 + 22f) i else -1
    }

    private fun down(lx: Float, ly: Float) {
        if (mode == Mode.INTRO) {
            introDown(lx, ly)
            return
        }
        if (!pet.adopted) {
            adoptDown(lx, ly)
            return
        }
        val inButtonRow = ly >= btnTop && ly <= btnTop + btnH

        if (mode == Mode.CATCH) {
            if (inButtonRow) {
                mode = Mode.ROOM
                fishes.clear()
                say("MAYBE LATER", 20)
                Chip.play(Snd.BLIP)
            } else {
                targetX = lx - petW() / 2f
            }
            return
        }

        if (pet.stage == Stage.DEAD) {
            if (inButtonRow) {
                pet.restart()
                hearts.clear()
                Chip.play(Snd.BLIP)
                say("A NEW EGG!", 30)
            }
            return
        }

        // sound switch (top right)
        if (lx >= W - 18 && ly in 24f..40f) {
            pet.soundOn = !pet.soundOn
            Chip.enabled = pet.soundOn
            Chip.play(Snd.BLIP)
            say(if (pet.soundOn) "SOUND ON" else "SOUND OFF", 15)
            return
        }

        val b = hitButton(lx, ly)
        if (b >= 0) {
            pressIdx = b
            pressTimer = 2
            press(b)
            return
        }

        // tap on the egg or on the cat
        if (pet.stage == Stage.EGG) {
            val ex = (W - 32) / 2
            if (lx >= ex && lx <= ex + 32 && ly >= feetY - 32 && ly <= feetY) {
                wiggle = 8
                pet.stageSec += 1.5f
                Chip.play(Snd.BLIP)
                say("TAP TAP...", 15)
            }
        } else if (pet.alive) {
            val py = petY()
            if (lx >= catX - 4 && lx <= catX + petW() + 4 && ly >= py - 4 && ly <= py + petH() + 4) {
                if (pet.sleeping) {
                    say("SHHH... SLEEPING", 15)
                } else {
                    pet.stroke()
                    happyTimer = 12
                    burst(3)
                    Chip.play(Snd.MEOW)
                    say("PURRR~", 15)
                }
            }
        }
    }

    private fun press(i: Int) {
        if (pet.stage == Stage.EGG) {
            Chip.play(Snd.BLIP)
            say("WAIT FOR IT...", 15)
            return
        }
        when (i) {
            0 -> when (pet.feed()) {
                Res.OK -> { eatTimer = 15; Chip.play(Snd.EAT); say("YUM YUM!") }
                Res.FULL -> { Chip.play(Snd.BLIP); say("NOT HUNGRY") }
                Res.ASLEEP -> { Chip.play(Snd.BLIP); say("SHHH... SLEEPING") }
                else -> {}
            }
            1 -> when (pet.canPlay()) {
                Res.OK -> { Chip.play(Snd.BLIP); startGame() }
                Res.ASLEEP -> { Chip.play(Snd.BLIP); say("SHHH... SLEEPING") }
                Res.SICK -> { Chip.play(Snd.SICK); say("TOO SICK TO PLAY") }
                Res.TOO_TIRED -> { Chip.play(Snd.SAD); say("TOO TIRED TO PLAY") }
                else -> {}
            }
            2 -> {
                val wasSleeping = pet.sleeping
                when (pet.toggleSleep()) {
                    Res.OK -> {
                        Chip.play(Snd.BLIP)
                        say(if (wasSleeping) "WAKE UP!" else "NIGHT NIGHT...")
                    }
                    Res.NOT_TIRED -> { Chip.play(Snd.BLIP); say("NOT SLEEPY YET") }
                    else -> {}
                }
            }
            3 -> when (pet.clean()) {
                Res.OK -> { Chip.play(Snd.HAPPY); say("SPARKLING CLEAN!") }
                Res.NO_POOP -> { Chip.play(Snd.BLIP); say("ALREADY CLEAN") }
                else -> {}
            }
            4 -> when (pet.heal()) {
                Res.OK -> { Chip.play(Snd.HAPPY); burst(4); say("FEELING BETTER!") }
                Res.NOT_SICK -> { Chip.play(Snd.BLIP); say("NOT SICK") }
                else -> {}
            }
        }
    }

    // ------------------------------------------------------------- drawing
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = maxOf(1, w / W)
        lh = maxOf(150, h / scale)
        ox = (w - W * scale) / 2f
        oy = (h - lh * scale) / 2f
    }

    private fun rect(c: Canvas, x: Int, y: Int, w: Int, h: Int, color: Int) {
        paint.color = color
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    private fun frame(c: Canvas, x: Int, y: Int, w: Int, h: Int, fill: Int, line: Int) {
        rect(c, x, y, w, h, line)
        rect(c, x + 1, y + 1, w - 2, h - 2, fill)
    }

    private fun textW(s: String, k: Int = 1) = s.length * 4 * k - k

    private fun text(c: Canvas, s: String, x: Int, y: Int, color: Int, k: Int = 1) {
        paint.color = color
        var cx = x
        for (ch in s) {
            val g = Font.glyphs[ch.uppercaseChar()] ?: Font.glyphs['?']!!
            for (r in 0 until 5) {
                for (b in 0 until 3) {
                    if ((g[r] and (4 shr b)) != 0) {
                        val px = (cx + b * k).toFloat()
                        val py = (y + r * k).toFloat()
                        c.drawRect(px, py, px + k, py + k, paint)
                    }
                }
            }
            cx += 4 * k
        }
    }

    private fun textCenter(c: Canvas, s: String, y: Int, color: Int, k: Int = 1) {
        text(c, s, (W - textW(s, k)) / 2, y, color, k)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale.toFloat(), scale.toFloat())

        if (mode == Mode.INTRO) {
            drawIntro(canvas)
            canvas.restore()
            return
        }
        if (!pet.adopted) {
            drawAdopt(canvas)
            canvas.restore()
            return
        }
        drawHud(canvas)
        drawRoom(canvas)
        drawActors(canvas)
        if (pet.sleeping && mode == Mode.ROOM) {
            paint.color = argb(0x6610204A)
            canvas.drawRect(0f, roomTop.toFloat(), W.toFloat(), roomBottom.toFloat(), paint)
        }
        drawHearts(canvas)
        drawMessage(canvas)
        drawButtons(canvas)

        canvas.restore()
    }

    private fun bar(c: Canvas, x: Int, y: Int, w: Int, h: Int, v: Float) {
        frame(c, x, y, w, h, WHITE, INK)
        val inner = ((w - 2) * v.coerceIn(0f, 1f)).toInt()
        val low = v < 0.25f
        if (inner > 0 && !(low && tickCount % 4 < 2)) {
            rect(c, x + 1, y + 1, inner, h - 2, if (low) RED else ORANGE)
        }
    }

    private fun drawHud(c: Canvas) {
        val values = floatArrayOf(pet.hunger, pet.happy, pet.energy)
        for (i in 0..2) {
            val cx = 4 + i * 38
            when (i) {
                0 -> Art.ICON_FISH.draw(c, cx, 4, paint, mono(argb(0xFF6FA8DC)))
                1 -> Art.HEART_FULL.draw(c, cx + 2, 6, paint, palHeart)
                else -> Art.ICON_MOON.draw(c, cx, 4, paint, palMoon)
            }
            bar(c, cx + 13, 7, 21, 5, values[i] / 100f)
        }
        rect(c, 0, hudBottom, W, 1, INK)

        val label = when (pet.stage) {
            Stage.EGG -> "EGG"
            Stage.KITTEN -> "KITTEN"
            Stage.CAT -> "CAT"
            Stage.DEAD -> "R.I.P."
        }
        val mins = (pet.totalSec / 60f).toInt()
        val age = if (mins >= 60) "AGE ${mins / 60}H${(mins % 60).toString().padStart(2, '0')}M" else "AGE ${mins}M"
        val ageX = W - 18 - textW(age) - 3
        text(c, pet.name, 4, 27, INK)
        val labelX = 4 + textW(pet.name) + 4
        if (labelX + textW(label) < ageX - 3) text(c, label, labelX, 27, MUTED)
        text(c, age, ageX, 27, MUTED)
        (if (pet.soundOn) Art.SPK_ON else Art.SPK_OFF).draw(c, W - 14, 26, paint, palInk)
        rect(c, 0, roomTop - 1, W, 1, argb(0xFFE3DCCB))
    }

    private fun drawRoom(c: Canvas) {
        // wallpaper dots
        paint.color = argb(0xFFEBE4D3)
        var yy = roomTop + 6
        var row = 0
        while (yy < floorY - 2) {
            var xx = if (row % 2 == 0) 6 else 12
            while (xx < W - 2) {
                c.drawRect(xx.toFloat(), yy.toFloat(), (xx + 1).toFloat(), (yy + 1).toFloat(), paint)
                xx += 12
            }
            yy += 8
            row++
        }

        // window
        val wx = 10
        val wy = roomTop + 14
        val night = pet.sleeping
        rect(c, wx - 2, wy - 2, 38, 44, argb(0xFF8B5A2B))
        rect(c, wx, wy, 34, 40, if (night) argb(0xFF1E2A55) else argb(0xFFCFE8F7))
        if (night) {
            Art.ICON_MOON.draw(c, wx + 18, wy + 6, paint, palMoon)
            rect(c, wx + 5, wy + 8, 1, 1, WHITE)
            rect(c, wx + 12, wy + 20, 1, 1, WHITE)
            rect(c, wx + 26, wy + 24, 1, 1, WHITE)
        } else {
            rect(c, wx + 6, wy + 8, 12, 4, WHITE)
            rect(c, wx + 9, wy + 6, 8, 3, WHITE)
        }
        rect(c, wx + 16, wy, 2, 40, argb(0xFF8B5A2B))
        rect(c, wx, wy + 19, 34, 2, argb(0xFF8B5A2B))
        rect(c, wx - 3, wy + 42, 40, 2, argb(0xFF6E4620))

        // picture frame
        val px = W - 34
        val py = roomTop + 18
        rect(c, px, py, 22, 26, argb(0xFF8B5A2B))
        rect(c, px + 2, py + 2, 18, 22, argb(0xFFFFF2E0))
        Art.HEART_FULL.draw(c, px + 4, py + 8, paint, palHeart, false, 2)

        // floor: skirting + checkered tiles
        rect(c, 0, floorY, W, 3, argb(0xFFB89968))
        val fh = roomBottom - floorY - 3
        var ty = 0
        while (ty < fh) {
            var tx = 0
            var k = 0
            while (tx < W) {
                val col = if ((k + ty / 8) % 2 == 0) argb(0xFFE6D5B8) else argb(0xFFEFE2C8)
                rect(c, tx, floorY + 3 + ty, 8, minOf(8, fh - ty), col)
                tx += 8
                k++
            }
            ty += 8
        }
        Art.BED.draw(c, W / 2 - 12, feetY - 3, paint, palRug)
    }

    private fun drawActors(c: Canvas) {
        // poops on the floor
        for (i in 0 until pet.poops) {
            Art.POOP.draw(c, 6 + i * 14, roomBottom - 10, paint, palPoop)
        }

        when (pet.stage) {
            Stage.EGG -> drawEgg(c)
            Stage.DEAD -> {
                val bob = if ((tickCount / 4) % 2 == 0) -1 else 1
                Art.GHOST.draw(c, (W - 32) / 2, feetY - 40 + bob, paint, palGhost, false, 2)
            }
            else -> drawPet(c)
        }

        // fish: falling in the mini-game, or being eaten
        if (mode == Mode.CATCH) {
            for (f in fishes) Art.FISH.draw(c, f.x.toInt(), f.y.toInt(), paint, palFish, false, 2)
        } else if (eatTimer > 0) {
            val p = 1f - eatTimer / 15f
            val fx = catX.toInt() + petW() / 2 - 13
            val fy = (petY() - 14 + (p * 22f)).toInt()
            Art.FISH.draw(c, fx, fy, paint, palFish, false, 2)
        }
    }

    private fun drawEgg(c: Canvas) {
        val p = pet.stageSec / Pet.EGG_SEC
        val wob = if (wiggle > 0) (if (tickCount % 2 == 0) -1 else 1) else 0
        val x = (W - 32) / 2 + wob
        val y = feetY - 32
        Art.EGG.draw(c, x, y, paint, palEgg, false, 2)
        if (p > 0.5f) Art.EGG_CRACK1.draw(c, x, y, paint, palEgg, false, 2)
        if (p > 0.8f) Art.EGG_CRACK2.draw(c, x, y, paint, palEgg, false, 2)
    }

    private fun faceIndex(): Int = when {
        pet.sleeping -> Face.SLEEP
        eatTimer > 0 -> if (tickCount % 4 < 2) Face.EAT else Face.HAPPY
        happyTimer > 0 -> Face.HAPPY
        pet.sick -> Face.SICK
        pet.hunger < 25f || pet.happy < 25f -> Face.SAD
        blinkLen > 0 -> Face.BLINK
        else -> Face.NEUTRAL
    }

    private fun drawPet(c: Canvas) {
        val set = petSprites()
        val x = catX.toInt()
        val y = petY()
        val flip = dir < 0
        val tail = if ((tickCount / 5) % 2 == 0) set.tailA else set.tailB
        tail.draw(c, x, y, paint, palCat(), flip, 2)
        set.base.draw(c, x, y, paint, palCat(), flip, 2)
        set.faces[faceIndex()].draw(c, x, y, paint, palCat(), flip, 2)

        if (pet.sleeping) {
            val z = (tickCount / 5) % 3
            text(c, "Z", x + petW() - 8 + z * 3, y - 6 - z * 5, INK, 2 - (if (z == 2) 1 else 0))
        }
    }

    private fun drawHearts(c: Canvas) {
        for (h in hearts) Art.HEART_FULL.draw(c, h.x.toInt(), h.y.toInt(), paint, palHeart)
    }

    private fun drawMessage(c: Canvas) {
        val s = when {
            mode == Mode.CATCH && msgTimer <= 0 -> {
                val t = (gameTicksMax - gameTicks) / 10
                "FISH $score  MISS $misses/5  T$t"
            }
            msgTimer > 0 -> msg
            else -> statusText()
        }
        textCenter(c, s, msgY, INK)
    }

    private fun drawButtons(c: Canvas) {
        if (mode == Mode.CATCH) {
            wideButton(c, "EXIT")
            return
        }
        if (pet.stage == Stage.DEAD) {
            wideButton(c, "NEW EGG")
            return
        }
        val enabled = pet.alive
        val iconPal = arrayOf(
            mono(argb(0xFF6FA8DC)), mono(argb(0xFFE06666)), mono(argb(0xFFF6C945)),
            mono(argb(0xFF7FD1E8)), mono(argb(0xFFE05555))
        )
        val icons = arrayOf(Art.ICON_FISH, Art.ICON_YARN, Art.ICON_MOON, Art.ICON_SOAP, Art.ICON_CROSS)
        for (i in 0..4) {
            val x = 1 + i * 24
            val pressed = pressTimer > 0 && pressIdx == i
            val fill = when {
                pressed -> INK
                enabled -> WHITE
                else -> argb(0xFFEAE6DC)
            }
            frame(c, x, btnTop, 22, btnH, fill, if (enabled) INK else MUTED)
            icons[i].draw(c, x + 5, btnTop + 3, paint, iconPal[i])
            val label = if (i == 2 && pet.sleeping) "WAKE" else btnLabels[i]
            val color = when {
                pressed -> WHITE
                enabled -> INK
                else -> MUTED
            }
            text(c, label, x + (22 - textW(label)) / 2, btnTop + 17, color)
        }
    }

    // ------------------------------------------------------------- intro / title screen
    private fun introFeet() = maxOf(lh - 56, 158)

    private fun outlineText(c: Canvas, s: String, x: Int, y: Int, color: Int, k: Int, o: Int) {
        for (dy in -o..o) {
            for (dx in -o..o) {
                if (dx != 0 || dy != 0) text(c, s, x + dx, y + dy, INK, k)
            }
        }
        text(c, s, x, y, color, k)
    }

    private fun introDown(lx: Float, ly: Float) {
        if (lx >= W - 22 && ly <= 18) {          // speaker switch, top right
            pet.soundOn = !pet.soundOn
            Chip.enabled = pet.soundOn
            if (pet.soundOn) Chip.startMusic() else Chip.stopMusic()
            return
        }
        Chip.stopMusic()
        Chip.play(Snd.HAPPY)
        mode = Mode.ROOM
        notes.clear()
        onSave?.invoke()
    }

    private fun drawIntro(c: Canvas) {
        val feet = introFeet()

        // twinkling stars
        for (i in starX.indices) {
            val col = if (i % 2 == 0) argb(0xFFF6C945) else argb(0xFFF4B6B6)
            val x = starX[i]
            val y = starY[i]
            when ((tickCount / 4 + starPh[i]) % 3) {
                0 -> rect(c, x, y, 1, 1, col)
                1 -> { rect(c, x - 1, y, 3, 1, col); rect(c, x, y - 1, 1, 3, col) }
                else -> { rect(c, x - 2, y, 5, 1, col); rect(c, x, y - 2, 1, 5, col) }
            }
        }

        // checkered floor
        rect(c, 3, feet - 4, W - 6, 3, argb(0xFFB89968))
        var ty = feet - 1
        var row = 0
        while (ty < lh - 3) {
            val h = minOf(8, lh - 3 - ty)
            var tx = 3
            var k = 0
            while (tx < W - 3) {
                val w = minOf(8, W - 3 - tx)
                rect(c, tx, ty, w, h, if ((k + row) % 2 == 0) argb(0xFFE6D5B8) else argb(0xFFEFE2C8))
                tx += 8
                k++
            }
            ty += 8
            row++
        }

        // logo
        outlineText(c, "RETRO", (W - textW("RETRO", 4)) / 2, 20, RED, 4, 1)
        outlineText(c, "CAT", (W - textW("CAT", 7)) / 2, 46, ORANGE, 7, 2)
        textCenter(c, "8-BIT VIRTUAL PET", 88, MUTED)

        // the cat hops on every beat (150 BPM = one beat per 4 ticks), in the player's own fur colour
        val set = Art.cat
        val s = 3
        val beat = tickCount % 4
        val hop = if (beat < 2) 3 else 0
        val x = (W - set.base.w * s) / 2
        val y = feet - set.base.h * s + 2 - hop
        val flip = (tickCount / 16) % 2 == 1
        val face = when {
            beat < 2 -> Face.HAPPY
            blinkLen > 0 -> Face.BLINK
            else -> Face.NEUTRAL
        }
        rect(c, x + 8, feet, set.base.w * s - 16, 2, argb(0x33000000))
        val tail = if ((tickCount / 3) % 2 == 0) set.tailA else set.tailB
        tail.draw(c, x, y, paint, palCat(), flip, s)
        set.base.draw(c, x, y, paint, palCat(), flip, s)
        set.faces[face].draw(c, x, y, paint, palCat(), flip, s)

        // floating music notes
        for (n in notes) noteSprite.draw(c, n.x.toInt(), n.y.toInt(), paint, palInk)

        // "tap to start" plate
        val py = lh - 40
        frame(c, 8, py, 104, 16, WHITE, INK)
        if ((tickCount / 5) % 2 == 0) textCenter(c, "TAP TO START", py + 3, INK, 2)

        // retro cabinet border
        rect(c, 0, 0, W, 2, INK)
        rect(c, 0, lh - 2, W, 2, INK)
        rect(c, 0, 0, 2, lh, INK)
        rect(c, W - 2, 0, 2, lh, INK)
        rect(c, 2, 2, W - 4, 1, ORANGE)
        rect(c, 2, lh - 3, W - 4, 1, ORANGE)
        rect(c, 2, 2, 1, lh - 4, ORANGE)
        rect(c, W - 3, 2, 1, lh - 4, ORANGE)

        (if (pet.soundOn) Art.SPK_ON else Art.SPK_OFF).draw(c, W - 15, 6, paint, palInk)
    }

    // ------------------------------------------------------------- adoption screen
    private fun cleanName(raw: String): String {
        val s = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == ' ' }.trim()
        return if (s.isEmpty()) "MIMI" else s.take(8)
    }

    private fun adoptDown(lx: Float, ly: Float) {
        val panelTop = btnTop - 38
        if (ly >= panelTop + 4 && ly <= panelTop + 22) {
            for (i in 0 until Pet.COATS) {
                val x = 7 + i * 22
                if (lx >= x && lx <= x + 18) {
                    pet.coat = i
                    happyTimer = 8
                    Chip.play(Snd.BLIP)
                    return
                }
            }
        }
        if (ly >= btnTop && ly <= btnTop + btnH) {
            pressTimer = 2
            if (lx < 60f) {
                pressIdx = 0
                Chip.play(Snd.BLIP)
                onAskName?.invoke(pet.name, { n -> pet.name = cleanName(n) })
            } else {
                pressIdx = 1
                pet.adopted = true
                lastMs = System.currentTimeMillis()
                Chip.play(Snd.HAPPY)
                say("TAKE GOOD CARE!", 35)
                onSave?.invoke()
            }
            return
        }
        if (ly >= panelTop - 66 && ly < panelTop) {
            Chip.play(Snd.MEOW)
            happyTimer = 12
        }
    }

    private fun drawAdopt(c: Canvas) {
        val panelTop = btnTop - 38
        textCenter(c, "ADOPT A KITTY", 6, INK, 2)
        textCenter(c, "NAME: ${pet.name}", 24, MUTED)
        drawRoom(c)

        // the kitty to be adopted, big and wagging its tail
        val set = Art.cat
        val s = 3
        val x = (W - set.base.w * s) / 2
        val y = panelTop - 4 - set.base.h * s
        Art.BED.draw(c, (W - 48) / 2, panelTop - 10, paint, palRug, false, 2)
        val face = when {
            happyTimer > 0 -> Face.HAPPY
            blinkLen > 0 -> Face.BLINK
            else -> Face.NEUTRAL
        }
        val tail = if ((tickCount / 5) % 2 == 0) set.tailA else set.tailB
        tail.draw(c, x, y, paint, palCat(), false, s)
        set.base.draw(c, x, y, paint, palCat(), false, s)
        set.faces[face].draw(c, x, y, paint, palCat(), false, s)

        // colour picker panel
        rect(c, 0, panelTop, W, btnTop - 2 - panelTop, BG)
        rect(c, 0, panelTop, W, 1, INK)
        for (i in 0 until Pet.COATS) {
            val sx = 7 + i * 22
            val sy = panelTop + 4
            val sel = i == pet.coat
            rect(c, sx, sy, 18, 18, if (sel) INK else MUTED)
            val ins = if (sel) 2 else 1
            rect(c, sx + ins, sy + ins, 18 - 2 * ins, 18 - 2 * ins, coats[i][1])
            rect(c, sx + 5, sy + 5, 2, 6, coats[i][2])
            rect(c, sx + 11, sy + 5, 2, 6, coats[i][2])
        }
        textCenter(c, coatNames[pet.coat.coerceIn(0, coatNames.size - 1)], panelTop + 26, INK)

        for (k in 0..1) {
            val x0 = 1 + k * 60
            val pressed = pressTimer > 0 && pressIdx == k
            frame(c, x0, btnTop, 59, btnH, if (pressed) INK else WHITE, INK)
            val lab = if (k == 0) "NAME" else "START"
            text(c, lab, x0 + (59 - textW(lab, 2)) / 2, btnTop + (btnH - 10) / 2, if (pressed) WHITE else INK, 2)
        }
    }

    private fun wideButton(c: Canvas, label: String) {
        frame(c, 1, btnTop, 118, btnH, WHITE, INK)
        textCenter(c, label, btnTop + (btnH - 10) / 2, INK, 2)
    }
}
