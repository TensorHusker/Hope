#!/bin/bash
# High-performance iOS build script with optimizations
# Builds universal binary for iOS devices and simulator

set -e

echo "🚀 Building Hope for iOS with performance optimizations..."

# Configuration
PROJECT_NAME="hope_game"
IOS_DEPLOYMENT_TARGET="14.0"
RUST_VERSION="stable"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check for required tools
check_requirements() {
    echo "Checking requirements..."
    
    if ! command -v cargo &> /dev/null; then
        echo -e "${RED}Error: Rust/Cargo not installed${NC}"
        exit 1
    fi
    
    if ! command -v xcodebuild &> /dev/null; then
        echo -e "${RED}Error: Xcode not installed${NC}"
        exit 1
    fi
    
    if ! command -v lipo &> /dev/null; then
        echo -e "${RED}Error: lipo not found (install Xcode Command Line Tools)${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ All requirements met${NC}"
}

# Setup Rust targets
setup_targets() {
    echo "Setting up iOS targets..."
    
    rustup target add aarch64-apple-ios
    rustup target add aarch64-apple-ios-sim
    rustup target add x86_64-apple-ios
    
    echo -e "${GREEN}✓ iOS targets installed${NC}"
}

# Build for specific target with optimizations
build_target() {
    local TARGET=$1
    local PROFILE=$2
    
    echo -e "${YELLOW}Building for $TARGET...${NC}"
    
    # Set environment variables for optimized build
    export RUSTFLAGS="-C link-arg=-s -C opt-level=3 -C lto=fat -C embed-bitcode=yes"
    export CARGO_PROFILE_RELEASE_LTO="fat"
    export CARGO_PROFILE_RELEASE_CODEGEN_UNITS="1"
    export CARGO_PROFILE_RELEASE_OPT_LEVEL="3"
    export CARGO_PROFILE_RELEASE_PANIC="abort"
    
    # Additional iOS-specific flags
    if [[ "$TARGET" == *"ios"* ]]; then
        export RUSTFLAGS="$RUSTFLAGS -C target-cpu=apple-a14"
    fi
    
    # Build with cargo
    cargo build --lib --target $TARGET --profile $PROFILE
    
    echo -e "${GREEN}✓ Built for $TARGET${NC}"
}

# Create universal binary
create_universal_binary() {
    echo "Creating universal binary..."
    
    local OUTPUT_DIR="target/universal/release"
    mkdir -p $OUTPUT_DIR
    
    # Create device binary (arm64)
    lipo -create \
        target/aarch64-apple-ios/release-ios/lib${PROJECT_NAME}.a \
        -output $OUTPUT_DIR/lib${PROJECT_NAME}_device.a
    
    # Create simulator binary (arm64 + x86_64)
    lipo -create \
        target/aarch64-apple-ios-sim/release-ios/lib${PROJECT_NAME}.a \
        target/x86_64-apple-ios/release-ios/lib${PROJECT_NAME}.a \
        -output $OUTPUT_DIR/lib${PROJECT_NAME}_simulator.a
    
    # Create XCFramework for modern Xcode
    create_xcframework
    
    echo -e "${GREEN}✓ Universal binary created${NC}"
}

# Create XCFramework
create_xcframework() {
    echo "Creating XCFramework..."
    
    local FRAMEWORK_DIR="target/HopeGame.xcframework"
    rm -rf $FRAMEWORK_DIR
    
    xcodebuild -create-xcframework \
        -library target/universal/release/lib${PROJECT_NAME}_device.a \
        -headers ios/Hope/BridgeHeaders \
        -library target/universal/release/lib${PROJECT_NAME}_simulator.a \
        -headers ios/Hope/BridgeHeaders \
        -output $FRAMEWORK_DIR
    
    echo -e "${GREEN}✓ XCFramework created at $FRAMEWORK_DIR${NC}"
}

# Generate bridging header
generate_bridging_header() {
    echo "Generating optimized bridging header..."
    
    local HEADER_DIR="ios/Hope/BridgeHeaders"
    mkdir -p $HEADER_DIR
    
    # Use cbindgen if available
    if command -v cbindgen &> /dev/null; then
        cbindgen --config cbindgen.toml --crate hope --output $HEADER_DIR/hope_game.h
    else
        echo -e "${YELLOW}Warning: cbindgen not found, using existing header${NC}"
    fi
    
    echo -e "${GREEN}✓ Bridging header ready${NC}"
}

# Run performance tests
run_performance_tests() {
    echo "Running performance benchmarks..."
    
    # Run Rust benchmarks
    cargo bench --target aarch64-apple-ios-sim
    
    echo -e "${GREEN}✓ Performance tests complete${NC}"
}

# Analyze binary size
analyze_binary() {
    echo "Analyzing binary size..."
    
    local BINARY="target/universal/release/lib${PROJECT_NAME}_device.a"
    
    if [ -f "$BINARY" ]; then
        local SIZE=$(du -h $BINARY | cut -f1)
        echo "Binary size: $SIZE"
        
        # Use bloaty if available for detailed analysis
        if command -v bloaty &> /dev/null; then
            bloaty -d compileunits --domain=vm $BINARY | head -20
        fi
        
        # Check if size is optimal
        local SIZE_KB=$(du -k $BINARY | cut -f1)
        if [ $SIZE_KB -gt 10240 ]; then
            echo -e "${YELLOW}Warning: Binary size exceeds 10MB, consider optimization${NC}"
        else
            echo -e "${GREEN}✓ Binary size is optimal${NC}"
        fi
    fi
}

# Create build report
create_build_report() {
    echo "Creating build report..."
    
    local REPORT_FILE="target/ios_build_report.txt"
    
    {
        echo "Hope iOS Build Report"
        echo "===================="
        echo "Date: $(date)"
        echo "Rust Version: $(rustc --version)"
        echo "Xcode Version: $(xcodebuild -version | head -1)"
        echo ""
        echo "Binary Sizes:"
        echo "Device: $(du -h target/universal/release/lib${PROJECT_NAME}_device.a | cut -f1)"
        echo "Simulator: $(du -h target/universal/release/lib${PROJECT_NAME}_simulator.a | cut -f1)"
        echo ""
        echo "Optimization Flags:"
        echo "RUSTFLAGS: $RUSTFLAGS"
        echo ""
        echo "Performance Targets:"
        echo "- 60fps stable on iPhone 12"
        echo "- <16ms touch latency"
        echo "- <200MB memory usage"
        echo "- <10% battery/hour"
    } > $REPORT_FILE
    
    echo -e "${GREEN}✓ Build report saved to $REPORT_FILE${NC}"
}

# Main build process
main() {
    echo "======================================"
    echo "   Hope iOS Performance Build"
    echo "======================================"
    echo ""
    
    check_requirements
    setup_targets
    
    # Clean previous builds
    echo "Cleaning previous builds..."
    cargo clean
    
    # Build for all targets with iOS-specific profile
    build_target "aarch64-apple-ios" "release-ios"
    build_target "aarch64-apple-ios-sim" "release-ios"
    build_target "x86_64-apple-ios" "release-ios"
    
    # Create universal binaries and frameworks
    create_universal_binary
    
    # Generate headers
    generate_bridging_header
    
    # Analyze results
    analyze_binary
    
    # Create report
    create_build_report
    
    echo ""
    echo "======================================"
    echo -e "${GREEN}✅ iOS build complete!${NC}"
    echo "======================================"
    echo ""
    echo "Next steps:"
    echo "1. Import HopeGame.xcframework into your Xcode project"
    echo "2. Add bridging header to Swift project settings"
    echo "3. Link with required system frameworks (Metal, CoreMotion)"
    echo "4. Run performance profiler to verify targets"
    echo ""
    echo "Performance tips:"
    echo "- Use Instruments to profile the app"
    echo "- Enable Metal validation in debug builds only"
    echo "- Test on actual devices for accurate metrics"
    echo "- Monitor thermal state during extended play"
}

# Run main function
main "$@"