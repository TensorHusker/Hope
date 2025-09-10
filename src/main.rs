use bevy::prelude::*;
use rand::prelude::*;

// Game configuration constants
const GRID_SIZE: usize = 3;
const CELL_SIZE: f32 = 60.0;
const CELL_SPACING: f32 = 10.0;
const PATTERN_DISPLAY_TIME: f32 = 2.0;  // Seconds to show pattern
const INITIAL_PATTERN_CELLS: u32 = 3;   // Starting pattern complexity
const SCORE_DIFFICULTY_SCALE: u32 = 3;  // Score points per difficulty increase

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, States, Default)]
enum GameState {
    #[default]
    ShowPattern,
    PlayerInput,
    CheckResult,
}

#[derive(Component)]
struct GridCell {
    x: usize,
    y: usize,
}

#[derive(Component)]
struct PatternCell;

#[derive(Component)]
struct PlayerCell;

#[derive(Resource)]
struct Pattern {
    grid: [[bool; GRID_SIZE]; GRID_SIZE],
}

#[derive(Resource)]
struct PlayerPattern {
    grid: [[bool; GRID_SIZE]; GRID_SIZE],
}

#[derive(Resource)]
struct GameTimer(Timer);

#[derive(Resource)]
struct Score(u32);

fn main() {
    App::new()
        .add_plugins(DefaultPlugins.set(WindowPlugin {
            primary_window: Some(Window {
                title: "Hope - Pattern Echo".to_string(),
                resolution: (800., 600.).into(),
                ..default()
            }),
            ..default()
        }))
        .init_state::<GameState>()
        .insert_resource(Pattern {
            grid: [[false; GRID_SIZE]; GRID_SIZE],
        })
        .insert_resource(PlayerPattern {
            grid: [[false; GRID_SIZE]; GRID_SIZE],
        })
        .insert_resource(GameTimer(Timer::from_seconds(PATTERN_DISPLAY_TIME, TimerMode::Once)))
        .insert_resource(Score(0))
        .add_systems(Startup, setup)
        .add_systems(
            Update,
            (
                generate_pattern.run_if(in_state(GameState::ShowPattern)),
                show_pattern_timer.run_if(in_state(GameState::ShowPattern)),
                handle_player_input.run_if(in_state(GameState::PlayerInput)),
                check_result.run_if(in_state(GameState::CheckResult)),
            ),
        )
        .run();
}

fn setup(mut commands: Commands) {
    // Camera
    commands.spawn(Camera2dBundle::default());

    // UI Text
    commands.spawn(
        TextBundle::from_section(
            "Watch the pattern!",
            TextStyle {
                font_size: 30.0,
                color: Color::WHITE,
                ..default()
            },
        )
        .with_style(Style {
            position_type: PositionType::Absolute,
            top: Val::Px(10.0),
            left: Val::Px(10.0),
            ..default()
        }),
    );

    // Score text
    commands.spawn((
        TextBundle::from_section(
            "Score: 0",
            TextStyle {
                font_size: 24.0,
                color: Color::srgb(0.9, 0.9, 0.9),
                ..default()
            },
        )
        .with_style(Style {
            position_type: PositionType::Absolute,
            top: Val::Px(50.0),
            left: Val::Px(10.0),
            ..default()
        }),
    ));

    // Create grid cells
    let start_x = -(GRID_SIZE as f32 * (CELL_SIZE + CELL_SPACING)) / 2.0 + CELL_SIZE / 2.0;
    let start_y = -(GRID_SIZE as f32 * (CELL_SIZE + CELL_SPACING)) / 2.0 + CELL_SIZE / 2.0;

    for x in 0..GRID_SIZE {
        for y in 0..GRID_SIZE {
            let pos_x = start_x + x as f32 * (CELL_SIZE + CELL_SPACING);
            let pos_y = start_y + y as f32 * (CELL_SIZE + CELL_SPACING);

            commands.spawn((
                SpriteBundle {
                    sprite: Sprite {
                        color: Color::srgb(0.2, 0.2, 0.2),
                        custom_size: Some(Vec2::new(CELL_SIZE, CELL_SIZE)),
                        ..default()
                    },
                    transform: Transform::from_xyz(pos_x, pos_y, 0.0),
                    ..default()
                },
                GridCell { x, y },
            ));
        }
    }
}

fn generate_pattern(
    mut pattern: ResMut<Pattern>,
    mut player_pattern: ResMut<PlayerPattern>,
    mut timer: ResMut<GameTimer>,
    score: Res<Score>,
) {
    // Clear patterns
    pattern.grid = [[false; GRID_SIZE]; GRID_SIZE];
    player_pattern.grid = [[false; GRID_SIZE]; GRID_SIZE];

    // Generate random pattern (complexity increases with score)
    let mut rng = thread_rng();
    let num_cells = (INITIAL_PATTERN_CELLS + score.0 / SCORE_DIFFICULTY_SCALE)
        .min((GRID_SIZE * GRID_SIZE - 1) as u32) as usize;

    for _ in 0..num_cells {
        let x = rng.gen_range(0..GRID_SIZE);
        let y = rng.gen_range(0..GRID_SIZE);
        // Bounds checking for safety
        if x < GRID_SIZE && y < GRID_SIZE {
            pattern.grid[x][y] = true;
        }
    }

    // Reset timer
    timer.0.reset();
}

fn show_pattern_timer(
    time: Res<Time>,
    mut timer: ResMut<GameTimer>,
    mut state: ResMut<NextState<GameState>>,
    pattern: Res<Pattern>,
    mut query: Query<(&GridCell, &mut Sprite)>,
    mut text_query: Query<&mut Text>,
) {
    timer.0.tick(time.delta());

    // Update grid display
    for (cell, mut sprite) in query.iter_mut() {
        if pattern.grid[cell.x][cell.y] {
            sprite.color = Color::srgb(0.3, 0.8, 0.3); // Green for pattern
        } else {
            sprite.color = Color::srgb(0.2, 0.2, 0.2); // Dark gray
        }
    }

    // Update instruction text
    if let Ok(mut text) = text_query.get_single_mut() {
        text.sections[0].value = format!(
            "Memorize! {:.1}s",
            (timer.0.duration().as_secs_f32() - timer.0.elapsed_secs())
        );
    }

    if timer.0.finished() {
        // Clear display and switch to input mode
        for (_cell, mut sprite) in query.iter_mut() {
            sprite.color = Color::srgb(0.2, 0.2, 0.2);
        }
        state.set(GameState::PlayerInput);
    }
}

fn handle_player_input(
    mut player_pattern: ResMut<PlayerPattern>,
    mut query: Query<(&GridCell, &mut Sprite)>,
    mouse_button: Res<ButtonInput<MouseButton>>,
    camera: Query<(&Camera, &GlobalTransform)>,
    windows: Query<&Window>,
    mut state: ResMut<NextState<GameState>>,
    mut text_query: Query<&mut Text>,
    keyboard: Res<ButtonInput<KeyCode>>,
) {
    // Update instruction text
    if let Ok(mut text) = text_query.get_single_mut() {
        text.sections[0].value = "Recreate the pattern! (Right-click when done)".to_string();
    }

    // Handle mouse clicks
    if mouse_button.just_pressed(MouseButton::Left) {
        // Safe camera query with error handling
        let Ok((camera, camera_transform)) = camera.get_single() else {
            return;
        };
        let Ok(window) = windows.get_single() else {
            return;
        };

        if let Some(cursor_position) = window.cursor_position() {
            if let Some(world_position) = camera
                .viewport_to_world_2d(camera_transform, cursor_position)
            {
                // Check which cell was clicked
                for (cell, mut sprite) in query.iter_mut() {
                    let cell_x = -(GRID_SIZE as f32 * (CELL_SIZE + CELL_SPACING)) / 2.0
                        + CELL_SIZE / 2.0
                        + cell.x as f32 * (CELL_SIZE + CELL_SPACING);
                    let cell_y = -(GRID_SIZE as f32 * (CELL_SIZE + CELL_SPACING)) / 2.0
                        + CELL_SIZE / 2.0
                        + cell.y as f32 * (CELL_SIZE + CELL_SPACING);

                    let distance = world_position.distance(Vec2::new(cell_x, cell_y));
                    if distance < CELL_SIZE / 2.0 {
                        // Toggle cell
                        player_pattern.grid[cell.x][cell.y] = !player_pattern.grid[cell.x][cell.y];
                        
                        if player_pattern.grid[cell.x][cell.y] {
                            sprite.color = Color::srgb(0.3, 0.3, 0.8); // Blue for player selection
                        } else {
                            sprite.color = Color::srgb(0.2, 0.2, 0.2); // Dark gray
                        }
                    }
                }
            }
        }
    }

    // Check for submission - use right click or Enter key
    if mouse_button.just_pressed(MouseButton::Right) || keyboard.just_pressed(KeyCode::Enter) {
        state.set(GameState::CheckResult);
    }
}

fn check_result(
    pattern: Res<Pattern>,
    player_pattern: Res<PlayerPattern>,
    mut score: ResMut<Score>,
    mut state: ResMut<NextState<GameState>>,
    mut query: Query<(&GridCell, &mut Sprite)>,
    mut text_query: Query<&mut Text>,
) {
    // Check if patterns match (with early exit optimization)
    let mut matches = true;
    'outer: for x in 0..GRID_SIZE {
        for y in 0..GRID_SIZE {
            if pattern.grid[x][y] != player_pattern.grid[x][y] {
                matches = false;
                break 'outer;  // Early exit on first mismatch
            }
        }
    }

    // Show result
    for (cell, mut sprite) in query.iter_mut() {
        if pattern.grid[cell.x][cell.y] && player_pattern.grid[cell.x][cell.y] {
            sprite.color = Color::srgb(0.3, 0.8, 0.3); // Green - correct
        } else if pattern.grid[cell.x][cell.y] {
            sprite.color = Color::srgb(0.8, 0.3, 0.3); // Red - missed
        } else if player_pattern.grid[cell.x][cell.y] {
            sprite.color = Color::srgb(0.8, 0.8, 0.3); // Yellow - extra
        } else {
            sprite.color = Color::srgb(0.2, 0.2, 0.2); // Gray - empty
        }
    }

    if matches {
        score.0 = score.0.saturating_add(1);  // Prevent overflow
        if let Ok(mut text) = text_query.get_single_mut() {
            text.sections[0].value = format!("Correct! Score: {}", score.0);
        }
    } else {
        score.0 = 0;
        if let Ok(mut text) = text_query.get_single_mut() {
            text.sections[0].value = format!("Wrong! Score reset to 0");
        }
    }

    // Wait a moment then start new round
    // In a real game, we'd use a timer here
    state.set(GameState::ShowPattern);
}
