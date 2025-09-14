// MetalRenderer.swift
// Metal rendering integration for Bevy

import Metal
import MetalKit
import simd

class MetalRenderer: NSObject {
    
    // MARK: - Properties
    
    private let device: MTLDevice
    private let commandQueue: MTLCommandQueue
    private let ffi = HopeFFI.shared
    
    private var renderPipelineState: MTLRenderPipelineState?
    private var depthStencilState: MTLDepthStencilState?
    
    private weak var metalView: MTKView?
    
    // Render targets for Bevy
    private var bevyRenderTexture: MTLTexture?
    private var bevyDepthTexture: MTLTexture?
    
    // MARK: - Initialization
    
    init(device: MTLDevice, metalView: MTKView) {
        self.device = device
        self.metalView = metalView
        
        guard let queue = device.makeCommandQueue() else {
            fatalError("Failed to create command queue")
        }
        self.commandQueue = queue
        
        super.init()
        
        setupRenderPipeline()
        setupDepthStencilState()
        createRenderTargets()
    }
    
    // MARK: - Setup
    
    private func setupRenderPipeline() {
        // Create a simple pass-through pipeline for Bevy's rendered content
        let library = device.makeDefaultLibrary()
        
        let vertexDescriptor = MTLVertexDescriptor()
        vertexDescriptor.attributes[0].format = .float3
        vertexDescriptor.attributes[0].offset = 0
        vertexDescriptor.attributes[0].bufferIndex = 0
        vertexDescriptor.layouts[0].stride = MemoryLayout<float3>.stride
        
        let pipelineDescriptor = MTLRenderPipelineDescriptor()
        pipelineDescriptor.label = "Bevy Render Pipeline"
        pipelineDescriptor.vertexFunction = library?.makeFunction(name: "vertex_passthrough")
        pipelineDescriptor.fragmentFunction = library?.makeFunction(name: "fragment_passthrough")
        pipelineDescriptor.vertexDescriptor = vertexDescriptor
        pipelineDescriptor.colorAttachments[0].pixelFormat = metalView?.colorPixelFormat ?? .bgra8Unorm
        pipelineDescriptor.depthAttachmentPixelFormat = metalView?.depthStencilPixelFormat ?? .depth32Float
        
        do {
            renderPipelineState = try device.makeRenderPipelineState(descriptor: pipelineDescriptor)
        } catch {
            print("Failed to create render pipeline state: \(error)")
        }
    }
    
    private func setupDepthStencilState() {
        let depthDescriptor = MTLDepthStencilDescriptor()
        depthDescriptor.depthCompareFunction = .less
        depthDescriptor.isDepthWriteEnabled = true
        depthStencilState = device.makeDepthStencilState(descriptor: depthDescriptor)
    }
    
    private func createRenderTargets() {
        guard let metalView = metalView else { return }
        
        let width = Int(metalView.drawableSize.width)
        let height = Int(metalView.drawableSize.height)
        
        // Create texture for Bevy to render into
        let textureDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: metalView.colorPixelFormat,
            width: width,
            height: height,
            mipmapped: false
        )
        textureDescriptor.usage = [.renderTarget, .shaderRead]
        textureDescriptor.storageMode = .shared
        
        bevyRenderTexture = device.makeTexture(descriptor: textureDescriptor)
        
        // Create depth texture
        let depthDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: metalView.depthStencilPixelFormat,
            width: width,
            height: height,
            mipmapped: false
        )
        depthDescriptor.usage = [.renderTarget]
        depthDescriptor.storageMode = .private
        
        bevyDepthTexture = device.makeTexture(descriptor: depthDescriptor)
    }
    
    // MARK: - Rendering
    
    private func render(in view: MTKView) {
        guard let drawable = view.currentDrawable,
              let renderPassDescriptor = view.currentRenderPassDescriptor else {
            return
        }
        
        // Call Bevy render
        ffi.render()
        
        // Create command buffer
        guard let commandBuffer = commandQueue.makeCommandBuffer() else { return }
        commandBuffer.label = "Bevy Frame"
        
        // Render Bevy content
        if let renderEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPassDescriptor) {
            renderEncoder.label = "Bevy Render Encoder"
            
            // Set pipeline state
            if let pipelineState = renderPipelineState {
                renderEncoder.setRenderPipelineState(pipelineState)
            }
            
            // Set depth stencil state
            if let depthStencil = depthStencilState {
                renderEncoder.setDepthStencilState(depthStencil)
            }
            
            // Here you would normally:
            // 1. Get vertex/index buffers from Bevy via FFI
            // 2. Set those buffers on the encoder
            // 3. Issue draw calls
            
            // For now, we'll just clear to the background color
            renderEncoder.endEncoding()
        }
        
        // Present drawable
        commandBuffer.present(drawable)
        commandBuffer.commit()
    }
}

// MARK: - MTKViewDelegate

extension MetalRenderer: MTKViewDelegate {
    
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        // Recreate render targets with new size
        createRenderTargets()
    }
    
    func draw(in view: MTKView) {
        render(in: view)
    }
}