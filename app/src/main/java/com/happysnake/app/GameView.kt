package com.happysnake.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.random.Random

class GameView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val GRID_COLS     = 20
        private const val INITIAL_DELAY = 200L   // ms per tick
        private const val MIN_DELAY     = 80L
        private const val SPEED_STEP    = 12L    // ms faster per 5 points
        private const val SCORE_STEP    = 5      // points between speed-ups
        private const val PREFS_NAME    = "happy_snake_prefs"
        private const val KEY_HIGH      = "high_score"
    }

    // ── Data ──────────────────────────────────────────────────────────────────
    private data class Cell(val x: Int, val y: Int)

    private enum class Dir { UP, DOWN, LEFT, RIGHT }
    private enum class State { READY, PLAYING, GAME_OVER }

    // ── Game state ────────────────────────────────────────────────────────────
    @Volatile private var state      = State.READY
    private val snake                = ArrayDeque<Cell>()
    private var food                 = Cell(0, 0)
    private var dir                  = Dir.RIGHT
    private var nextDir              = Dir.RIGHT
    private var score                = 0
    private var highScore            = 0
    private var delayMs              = INITIAL_DELAY
    private var foodColorIdx         = 0

    // ── Grid dimensions (set in surfaceChanged) ───────────────────────────────
    private var cellSize   = 0f
    private var gridRows   = 0

    // ── Threading ─────────────────────────────────────────────────────────────
    @Volatile private var running = false
    private var gameThread: Thread? = null

    // ── Colors ────────────────────────────────────────────────────────────────
    private val foodColors = intArrayOf(
        0xFFE53935.toInt(),  // red – apple
        0xFFFF9800.toInt(),  // orange
        0xFFFFEB3B.toInt(),  // yellow – banana
        0xFFE040FB.toInt(),  // purple – grape
        0xFFFF80AB.toInt(),  // pink – strawberry
        0xFF00BCD4.toInt(),  // cyan – blueberry
        0xFF8BC34A.toInt(),  // lime
        0xFFFF7043.toInt(),  // deep orange – peach
    )

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgPaint = Paint().apply {
        color = 0xFFF1F8E9.toInt(); style = Paint.Style.FILL
    }
    private val gridPaint = Paint().apply {
        color = 0xFFDCEDC8.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val bodyPaint = Paint().apply {
        color = 0xFF66BB6A.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val headPaint = Paint().apply {
        color = 0xFF2E7D32.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val foodPaint = Paint().apply {
        style = Paint.Style.FILL; isAntiAlias = true
    }
    private val foodShinePaint = Paint().apply {
        color = 0xAAFFFFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val eyeWhite = Paint().apply {
        color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val eyePupil = Paint().apply {
        color = 0xFF1B5E20.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val smilePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val scorePaint = Paint().apply {
        color = 0xFF33691E.toInt()
        style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val scoreBgPaint = Paint().apply {
        color = 0xAAFFFFFF.toInt(); style = Paint.Style.FILL
    }
    private val overlayPaint = Paint().apply {
        color = 0xCC000000.toInt(); style = Paint.Style.FILL
    }
    private val titlePaint = Paint().apply {
        color = 0xFFFFEB3B.toInt()
        style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.FILL
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val actionPaint = Paint().apply {
        color = 0xFF69F0AE.toInt()
        style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    // ── Gesture ───────────────────────────────────────────────────────────────
    private val gesture = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleInteraction()
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                vx: Float, vy: Float
            ): Boolean {
                if (state != State.PLAYING) { handleInteraction(); return true }
                val dx = e2.x - (e1?.x ?: return true)
                val dy = e2.y - (e1?.y ?: return true)
                if (abs(dx) > abs(dy)) {
                    if (dx > 0 && dir != Dir.LEFT)  nextDir = Dir.RIGHT
                    else if (dx < 0 && dir != Dir.RIGHT) nextDir = Dir.LEFT
                } else {
                    if (dy > 0 && dir != Dir.UP)    nextDir = Dir.DOWN
                    else if (dy < 0 && dir != Dir.DOWN)  nextDir = Dir.UP
                }
                return true
            }
        })

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        holder.addCallback(this)
        isFocusable = true
        loadHighScore()
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    private fun loadHighScore() {
        highScore = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_HIGH, 0)
    }

    private fun saveHighScore() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HIGH, highScore).apply()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        gameThread = Thread(::loop, "SnakeGameThread").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        cellSize = w.toFloat() / GRID_COLS
        gridRows = (h / cellSize).toInt()
        // Scale text sizes to cell size
        scorePaint.textSize   = cellSize * 0.72f
        titlePaint.textSize   = cellSize * 1.6f
        subPaint.textSize     = cellSize * 0.95f
        actionPaint.textSize  = cellSize * 0.82f
        smilePaint.strokeWidth = cellSize * 0.09f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        var retry = true
        while (retry) {
            try { gameThread?.join(); retry = false }
            catch (_: InterruptedException) { }
        }
    }

    fun onResume() { /* reserved for future use */ }
    fun onPause()  { /* reserved for future use */ }

    // ── Game loop ─────────────────────────────────────────────────────────────
    private fun loop() {
        while (running) {
            val t0 = System.currentTimeMillis()

            if (state == State.PLAYING) tick()

            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try { render(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            }

            val elapsed = System.currentTimeMillis() - t0
            val sleep   = (if (state == State.PLAYING) delayMs else 100L) - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) { }
        }
    }

    // ── Game logic ────────────────────────────────────────────────────────────
    private fun tick() {
        dir = nextDir
        val head = snake.firstOrNull() ?: return
        val next = when (dir) {
            Dir.UP    -> Cell(head.x, head.y - 1)
            Dir.DOWN  -> Cell(head.x, head.y + 1)
            Dir.LEFT  -> Cell(head.x - 1, head.y)
            Dir.RIGHT -> Cell(head.x + 1, head.y)
        }

        if (next.x !in 0 until GRID_COLS || next.y !in 0 until gridRows
            || snake.contains(next)) {
            state = State.GAME_OVER
            return
        }

        snake.addFirst(next)
        if (next == food) {
            score++
            if (score > highScore) { highScore = score; saveHighScore() }
            spawnFood()
            adjustSpeed()
        } else {
            snake.removeLast()
        }
    }

    private fun adjustSpeed() {
        val level = score / SCORE_STEP
        delayMs = maxOf(MIN_DELAY, INITIAL_DELAY - level * SPEED_STEP)
    }

    private fun spawnFood() {
        val occupied = snake.toHashSet()
        val free = mutableListOf<Cell>()
        for (x in 0 until GRID_COLS)
            for (y in 0 until gridRows)
                if (Cell(x, y) !in occupied) free.add(Cell(x, y))
        if (free.isNotEmpty()) food = free[Random.nextInt(free.size)]
        foodColorIdx = (foodColorIdx + 1) % foodColors.size
    }

    private fun startGame() {
        if (gridRows == 0) return
        snake.clear()
        val sx = GRID_COLS / 2
        val sy = gridRows / 2
        // Initial snake: 3 cells long, moving right
        snake.addLast(Cell(sx + 2, sy))
        snake.addLast(Cell(sx + 1, sy))
        snake.addLast(Cell(sx,     sy))
        dir      = Dir.RIGHT
        nextDir  = Dir.RIGHT
        score    = 0
        delayMs  = INITIAL_DELAY
        foodColorIdx = Random.nextInt(foodColors.size)
        spawnFood()
        state = State.PLAYING
    }

    private fun handleInteraction() {
        if (state == State.READY || state == State.GAME_OVER) startGame()
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    private fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGrid(canvas)
        when (state) {
            State.READY -> drawReadyOverlay(canvas)
            State.PLAYING -> {
                drawFood(canvas); drawSnake(canvas); drawScore(canvas)
            }
            State.GAME_OVER -> {
                drawFood(canvas); drawSnake(canvas); drawScore(canvas)
                drawGameOverOverlay(canvas)
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        for (c in 0..GRID_COLS)
            canvas.drawLine(c * cellSize, 0f, c * cellSize, height.toFloat(), gridPaint)
        for (r in 0..gridRows)
            canvas.drawLine(0f, r * cellSize, width.toFloat(), r * cellSize, gridPaint)
    }

    private fun drawFood(canvas: Canvas) {
        foodPaint.color = foodColors[foodColorIdx]
        val fx = food.x * cellSize + cellSize / 2f
        val fy = food.y * cellSize + cellSize / 2f
        val fr = cellSize * 0.38f
        canvas.drawCircle(fx, fy, fr, foodPaint)
        // Shine highlight
        canvas.drawCircle(fx - fr * 0.32f, fy - fr * 0.32f, fr * 0.26f, foodShinePaint)
    }

    private fun drawSnake(canvas: Canvas) {
        if (snake.isEmpty()) return
        val pad = cellSize * 0.09f
        val cr  = cellSize * 0.35f
        // Body (index 1 onwards)
        for (i in 1 until snake.size) {
            val s = snake[i]
            canvas.drawRoundRect(
                s.x * cellSize + pad, s.y * cellSize + pad,
                s.x * cellSize + cellSize - pad, s.y * cellSize + cellSize - pad,
                cr, cr, bodyPaint
            )
        }
        // Head
        val h   = snake.first()
        val pad2 = pad * 0.4f
        canvas.drawRoundRect(
            h.x * cellSize + pad2, h.y * cellSize + pad2,
            h.x * cellSize + cellSize - pad2, h.y * cellSize + cellSize - pad2,
            cr, cr, headPaint
        )
        drawFace(canvas, h.x * cellSize + cellSize / 2f, h.y * cellSize + cellSize / 2f)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        val rotation = when (dir) {
            Dir.RIGHT -> 0f; Dir.DOWN -> 90f; Dir.LEFT -> 180f; Dir.UP -> 270f
        }
        canvas.rotate(rotation, cx, cy)

        val eo = cellSize * 0.18f   // eye offset
        val er = cellSize * 0.09f   // eye radius
        val pr = cellSize * 0.05f   // pupil radius
        val ex = cellSize * 0.18f   // eye x push (toward face direction = +x after rotate)

        // Eyes (two circles, symmetric around centre-y, pushed in the facing direction)
        canvas.drawCircle(cx + ex, cy - eo, er, eyeWhite)
        canvas.drawCircle(cx + ex, cy + eo, er, eyeWhite)
        canvas.drawCircle(cx + ex, cy - eo, pr, eyePupil)
        canvas.drawCircle(cx + ex, cy + eo, pr, eyePupil)

        // Smile arc (U-shape centred a bit toward face direction)
        val sw = cellSize * 0.24f
        val sh = cellSize * 0.14f
        val smx = cx + ex * 0.3f
        val smy = cy + eo * 0.4f
        canvas.drawArc(smx - sw / 2, smy - sh / 2, smx + sw / 2, smy + sh / 2,
            0f, 180f, false, smilePaint)

        canvas.restore()
    }

    private fun drawScore(canvas: Canvas) {
        val pad = cellSize * 0.3f
        val lineH = scorePaint.textSize * 1.3f
        val text1 = "Score: $score"
        val text2 = "Best:  $highScore"
        val w = maxOf(scorePaint.measureText(text1), scorePaint.measureText(text2)) + pad * 2
        val h = lineH * 2 + pad
        canvas.drawRoundRect(pad / 2, pad / 2, pad / 2 + w, pad / 2 + h, 16f, 16f, scoreBgPaint)
        canvas.drawText(text1, pad, pad + scorePaint.textSize, scorePaint)
        canvas.drawText(text2, pad, pad + scorePaint.textSize + lineH, scorePaint)
    }

    private fun drawReadyOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cy = height / 2f
        canvas.drawText("Happy Snake!", width / 2f, cy - cellSize * 2.2f, titlePaint)
        if (highScore > 0)
            canvas.drawText("Best Score: $highScore", width / 2f, cy - cellSize * 0.3f, subPaint)
        canvas.drawText("Swipe or Tap to Play!", width / 2f, cy + cellSize * 1.8f, actionPaint)
    }

    private fun drawGameOverOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cy = height / 2f
        val msgs = arrayOf("Great try!", "Awesome!", "Nice one!", "Keep going!", "So close!")
        canvas.drawText(msgs[score % msgs.size], width / 2f, cy - cellSize * 2.2f, titlePaint)
        canvas.drawText("Score: $score",  width / 2f, cy - cellSize * 0.5f, subPaint)
        canvas.drawText("Best:  $highScore", width / 2f, cy + cellSize * 0.7f, subPaint)
        canvas.drawText("Tap to Play Again!", width / 2f, cy + cellSize * 2.4f, actionPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        return true
    }
}
