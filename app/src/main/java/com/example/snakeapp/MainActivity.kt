package com.example.snakeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF0F4C3) // Light Lime background
                ) {
                    SnakeGame()
                }
            }
        }
    }
}

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

data class Point(val x: Int, val y: Int)

@Composable
fun SnakeGame() {
    val gridSize = 20
    var snake by remember { mutableStateOf(listOf(Point(10, 10), Point(10, 11), Point(10, 12))) }
    var food by remember { mutableStateOf(Point(5, 5)) }
    var direction by remember { mutableStateOf(Direction.UP) }
    var score by remember { mutableStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }

    // Game loop
    var nextDirection by remember { mutableStateOf(direction) }

    LaunchedEffect(key1 = isGameOver) {
        if (isGameOver) return@LaunchedEffect
        while (true) {
            delay(150) // Slightly faster for fun
            direction = nextDirection
            val head = snake.first()
            val newHead = when (direction) {
                Direction.UP -> Point(head.x, (head.y - 1 + gridSize) % gridSize)
                Direction.DOWN -> Point(head.x, (head.y + 1) % gridSize)
                Direction.LEFT -> Point((head.x - 1 + gridSize) % gridSize, head.y)
                Direction.RIGHT -> Point((head.x + 1) % gridSize, head.y)
            }

            if (snake.contains(newHead)) {
                isGameOver = true
                break
            }

            val newSnake = mutableListOf(newHead) + snake
            if (newHead == food) {
                score += 1
                food = Point(Random.nextInt(gridSize), Random.nextInt(gridSize))
            } else {
                newSnake.removeAt(newSnake.size - 1)
            }
            snake = newSnake
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Happy Snake \uD83D\uDC0D",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2E7D32)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFC5E1A5)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "Score: $score",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = Color(0xFF1B5E20)
                )
            }
        }

        // Game Board
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .maxSize(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F8E9))
                .border(8.dp, Color(0xFF8BC34A), RoundedCornerShape(12.dp))
        ) {
            // Draw grid lines for a cleaner look
            for (i in 1 until gridSize) {
                HorizontalDivider(
                    color = Color(0xFFDCEDC8),
                    thickness = 1.dp,
                    modifier = Modifier.offset(y = (i * (320 / gridSize)).dp)
                )
                VerticalDivider(
                    color = Color(0xFFDCEDC8),
                    thickness = 1.dp,
                    modifier = Modifier.offset(x = (i * (320 / gridSize)).dp)
                )
            }

            val cellSize = 304 / gridSize // Adjusted for border

            snake.forEachIndexed { index, point ->
                val color = if (index == 0) Color(0xFF388E3C) else Color(0xFF66BB6A)
                Box(
                    modifier = Modifier
                        .offset(x = (point.x * cellSize).dp, y = (point.y * cellSize).dp)
                        .size(cellSize.dp)
                        .padding(2.dp)
                        .background(color, CircleShape)
                ) {
                    if (index == 0) {
                        // Eyes for the snake head
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                            Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                        }
                    }
                }
            }

            // Food
            Box(
                modifier = Modifier
                    .offset(x = (food.x * cellSize).dp, y = (food.y * cellSize).dp)
                    .size(cellSize.dp)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83C\uDF4E", fontSize = 14.sp)
            }
        }

        // Controls
        SnakeControls(onDirectionChange = { newDir ->
            val isOpposite = when (newDir) {
                Direction.UP -> direction == Direction.DOWN
                Direction.DOWN -> direction == Direction.UP
                Direction.LEFT -> direction == Direction.RIGHT
                Direction.RIGHT -> direction == Direction.LEFT
            }
            if (!isOpposite) nextDirection = newDir
        })

        if (isGameOver) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Game Over! \uD83D\uDE04") },
                text = { Text("Great job! Your happy snake grew so big! Final Score: $score") },
                confirmButton = {
                    Button(
                        onClick = {
                            snake = listOf(Point(10, 10), Point(10, 11), Point(10, 12))
                            direction = Direction.UP
                            nextDirection = Direction.UP
                            score = 0
                            isGameOver = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Play Again! \uD83C\uDFAE")
                    }
                }
            )
        }
    }
}

private fun Modifier.maxSize(maxSize: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.sizeIn(maxWidth = maxSize, maxHeight = maxSize)
)

@Composable
fun SnakeControls(onDirectionChange: (Direction) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton(Icons.Default.KeyboardArrowUp, "Up") { onDirectionChange(Direction.UP) }
        Row {
            ControlButton(Icons.Default.KeyboardArrowLeft, "Left") { onDirectionChange(Direction.LEFT) }
            Spacer(modifier = Modifier.width(48.dp))
            ControlButton(Icons.Default.KeyboardArrowRight, "Right") { onDirectionChange(Direction.RIGHT) }
        }
        ControlButton(Icons.Default.KeyboardArrowDown, "Down") { onDirectionChange(Direction.DOWN) }
    }
}

@Composable
fun ControlButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color(0xFF8BC34A), CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}
