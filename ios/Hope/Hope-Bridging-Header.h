// Hope-Bridging-Header.h
// Bridging header for Swift to access C FFI functions from Rust

#ifndef Hope_Bridging_Header_h
#define Hope_Bridging_Header_h

#include <stdint.h>
#include <stdbool.h>

// Core engine lifecycle functions
void bevy_init(void);
void bevy_update(float delta_time);
void bevy_render(void);
void bevy_shutdown(void);

// Game state management
void bevy_pause(void);
void bevy_resume(void);
bool bevy_is_running(void);

// Input handling
void bevy_touch_event(float x, float y, int32_t event_type);
void bevy_accelerometer_update(float x, float y, float z);

// Audio management
void bevy_set_audio_volume(float volume);
void bevy_mute_audio(bool muted);

// Game state queries
int32_t bevy_get_score(void);
int32_t bevy_get_level(void);
const char* bevy_get_save_data(void);
void bevy_load_save_data(const char* data);

// Platform callbacks
typedef void (*platform_callback_t)(const char* event_name, const char* data);
void bevy_register_platform_callback(platform_callback_t callback);

// Memory management
void bevy_free_string(char* str);

#endif /* Hope_Bridging_Header_h */