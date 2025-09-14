// Shaders.metal
// Metal shaders for rendering Bevy content

#include <metal_stdlib>
using namespace metal;

// Vertex structure
struct Vertex {
    float3 position [[attribute(0)]];
    float2 texCoord [[attribute(1)]];
    float4 color [[attribute(2)]];
};

// Vertex output / Fragment input
struct VertexOut {
    float4 position [[position]];
    float2 texCoord;
    float4 color;
};

// Uniforms
struct Uniforms {
    float4x4 modelViewProjection;
    float time;
    float2 resolution;
};

// Passthrough vertex shader
vertex VertexOut vertex_passthrough(
    Vertex in [[stage_in]],
    constant Uniforms& uniforms [[buffer(1)]]
) {
    VertexOut out;
    out.position = uniforms.modelViewProjection * float4(in.position, 1.0);
    out.texCoord = in.texCoord;
    out.color = in.color;
    return out;
}

// Passthrough fragment shader
fragment float4 fragment_passthrough(
    VertexOut in [[stage_in]],
    texture2d<float> texture [[texture(0)]],
    sampler textureSampler [[sampler(0)]]
) {
    float4 color = texture.sample(textureSampler, in.texCoord);
    return color * in.color;
}

// Simple color fragment shader (no texture)
fragment float4 fragment_color(VertexOut in [[stage_in]]) {
    return in.color;
}

// Glow effect fragment shader
fragment float4 fragment_glow(
    VertexOut in [[stage_in]],
    texture2d<float> texture [[texture(0)]],
    sampler textureSampler [[sampler(0)]],
    constant Uniforms& uniforms [[buffer(1)]]
) {
    float4 color = texture.sample(textureSampler, in.texCoord);
    
    // Add time-based glow effect
    float glow = sin(uniforms.time * 2.0) * 0.5 + 0.5;
    color.rgb += color.rgb * glow * 0.3;
    
    return color * in.color;
}

// Pixelation post-processing
fragment float4 fragment_pixelate(
    VertexOut in [[stage_in]],
    texture2d<float> texture [[texture(0)]],
    sampler textureSampler [[sampler(0)]],
    constant Uniforms& uniforms [[buffer(1)]]
) {
    float pixelSize = 4.0;
    float2 pixelCoord = floor(in.texCoord * uniforms.resolution / pixelSize) * pixelSize / uniforms.resolution;
    float4 color = texture.sample(textureSampler, pixelCoord);
    return color;
}

// CRT monitor effect
fragment float4 fragment_crt(
    VertexOut in [[stage_in]],
    texture2d<float> texture [[texture(0)]],
    sampler textureSampler [[sampler(0)]],
    constant Uniforms& uniforms [[buffer(1)]]
) {
    float2 uv = in.texCoord;
    
    // Barrel distortion
    float2 dc = uv - 0.5;
    float2 distort = dc * (1.0 + dot(dc, dc) * 0.2);
    uv = distort + 0.5;
    
    // Sample with distortion
    float4 color = texture.sample(textureSampler, uv);
    
    // Scanlines
    float scanline = sin(uv.y * uniforms.resolution.y * 2.0) * 0.04;
    color.rgb -= scanline;
    
    // Vignette
    float vignette = 1.0 - dot(dc, dc) * 0.5;
    color.rgb *= vignette;
    
    return color;
}