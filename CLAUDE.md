# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Hope

A rapid-development, commercial lite version of Runetika with:
1. ONE innovative core game mechanic that can hook players immediately
2. ONE primary plot point that teases the larger Runetika narrative
3. MVP implementation focused on:
   - Quick market entry for revenue generation
   - Validating core Runetika mechanics
   - Building anticipation for the full game
   - Establishing player base and feedback loop

## Architecture Overview

### Simplified Stack
- **Game Engine**: Bevy ECS (minimal dependencies)
- **Rendering**: Simple 2D or terminal-based display
- **Core Loop**: Focus on the single innovative mechanic
- **State Management**: Minimal states - menu, gameplay, endgame

### Essential Components
- **Core Mechanic System**: [To be defined - the ONE innovative gameplay element]
- **Narrative Driver**: [To be defined - the ONE primary plot point]
- **Feedback Loop**: Immediate player feedback for the core mechanic

## Development Commands

### Project Setup
```bash
# Initialize Rust/Bevy project if not exists
cargo init
cargo add bevy avian2d

# Run the game
cargo run

# Run with release optimizations
cargo run --release

# Run tests
cargo test

# Check code without building
cargo check

# Format code
cargo fmt

# Lint code
cargo clippy -- -W clippy::all
```

### Asset Pipeline
```bash
# Assets should be placed in assets/ directory
# Organize as: assets/sprites/, assets/audio/, assets/fonts/
```

## Code Organization

### Minimal Module Structure
- `src/main.rs` - Entry point and Bevy app setup
- `src/game.rs` - Core game loop and state management
- `src/mechanic.rs` - The ONE innovative mechanic implementation
- `src/narrative.rs` - Story progression tied to the mechanic
- `src/ui.rs` - Minimal UI/display components

### Design Principles
- **KISS**: Keep It Simple, Stupid - focus on the core innovation
- **Rapid Prototyping**: Get the mechanic working first, polish later
- **Tight Feedback Loop**: Every action should have immediate, clear feedback
- **Emergent Complexity**: Simple rules leading to interesting outcomes

## Core Mechanic Focus

### The ONE Mechanic
[To be defined based on design decisions]
- Should be immediately understandable
- Must have depth for mastery
- Creates emergent gameplay possibilities
- Ties directly to the narrative

### The ONE Plot Point
[To be defined based on narrative decisions]
- Simple, emotionally resonant premise
- Directly connected to gameplay mechanic
- Allows for player interpretation
- Sets up potential for full Runetika expansion

## Development Priorities (Time-Boxed)

1. **Week 1: Core Mechanic** - Playable prototype
2. **Week 2: Game Loop** - Win/lose conditions, progression
3. **Week 3: Polish & Juice** - Sound, particles, feedback
4. **Week 4: Monetization** - Store integration, analytics
5. **Week 5: Ship It** - Bug fixes, launch on itch.io/Steam

## MVP Scope

### Must Have
- The ONE mechanic working perfectly
- 10-15 minutes of engaging gameplay
- Clear "Coming in Runetika" teaser
- Basic monetization (one-time purchase $4.99-$9.99)
- Analytics to track player behavior

### Nice to Have
- Leaderboards
- Daily challenges
- Cosmetic unlocks
- Discord integration

### Cut for Runetika
- Complex narrative branches
- Multiple mechanics
- Type theory systems
- ARC puzzle integration

## Testing Strategy

- Quick playtesting iterations
- Focus on mechanic clarity and fun
- Minimal automated testing initially
- Player feedback loops

## Success Metrics

### Commercial
- Break even within first month ($5K-10K revenue)
- 60%+ positive reviews
- 5+ minute average session time
- 20%+ wishlist Runetika after playing

### Design Validation
- Players "get it" within 30 seconds
- Core mechanic proves addictive
- Clear demand for "more like this"
- Streamer/content creator friendly

## Marketing Strategy

- **Pre-Launch**: Dev logs, GIFs on Twitter/Reddit
- **Launch**: Press kit to indie game sites
- **Post-Launch**: "Road to Runetika" updates
- **Cross-Promotion**: "Hope players get 20% off Runetika"

## Technical Shortcuts (Ship Fast)

- Use existing asset packs where possible
- Minimal custom art (style over detail)
- Simple particle effects for juice
- Stock sounds with light modification
- No complex save system (level codes instead)
- Web build first, native later

## From Hope to Runetika

Hope validates:
- Core pattern-matching mechanic
- Terminal-style interface appeal
- Silicon Mind character concept
- Market demand for mystical realism
- Player appetite for deeper systems
- I want this game to be created very quickly to make money fast and establish my mechanics, preview/trailer for Runetika, and more.