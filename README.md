# Hope - Pattern Echo Prototype

A lite version of Runetika focused on pattern recognition gameplay.

## How to Play

1. **Watch** - A pattern appears on the 3x3 grid for 2 seconds
2. **Memorize** - Remember which cells were lit up
3. **Recreate** - Click cells to recreate the pattern from memory
4. **Submit** - Press SPACE when done
5. **Score** - Get it right to increase score, wrong resets to 0

## Controls

- **Mouse Click** - Toggle grid cells on/off
- **SPACE** - Submit your pattern

## Running the Game

```bash
# Build and run
cargo run

# Or build release version for better performance
cargo run --release
```

## Game Features

- Progressive difficulty (more cells as score increases)
- Visual feedback (green=correct, red=missed, yellow=extra)
- Score tracking
- Instant restart after each round

## Silicon Mind Narrative

"The Silicon Mind's last message fragmented across time. Each echo you restore brings you closer to understanding what they tried to warn us about."

## Development Status

This is a rapid prototype to validate the core mechanic for Hope/Runetika. Current features:
- ✅ 3x3 grid system
- ✅ Pattern generation
- ✅ Timer system
- ✅ Player input
- ✅ Pattern matching
- ✅ Score progression

## Next Steps

- Add sound effects
- Implement particle effects
- Add more pattern types
- Create narrative integration
- Polish visual style