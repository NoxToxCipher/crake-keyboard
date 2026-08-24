cmake_minimum_required(VERSION 3.22)

project(florisboard)

if(CMAKE_HOST_WIN32)
    set(USER_HOME "$ENV{USERPROFILE}")
    set(CLANG_EXT ".cmd")
else()
    set(USER_HOME "$ENV{HOME}")
    set(CLANG_EXT "")
endif()

find_program(RUSTUP_EXECUTABLE NAMES rustup HINTS "${USER_HOME}/.cargo/bin")
find_program(CARGO_EXECUTABLE NAMES cargo HINTS "${USER_HOME}/.cargo/bin")
if(NOT RUSTUP_EXECUTABLE OR NOT CARGO_EXECUTABLE)
    message(FATAL_ERROR "Rust toolchain not found on this system. Please install Rust toolchain, then try again.")
else()
    message(STATUS "Using Rust toolchain: ${RUSTUP_EXECUTABLE}")
endif()

### FlorisBoard ###

add_library(fl_native SHARED src/lib.c)

if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(ANDROID_TARGET "armv7a-linux-androideabi")
    set(RUST_TARGET "armv7-linux-androideabi")
    set(ENV_LINKER_VAR "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(ANDROID_TARGET "aarch64-linux-android")
    set(RUST_TARGET "aarch64-linux-android")
    set(ENV_LINKER_VAR "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(ANDROID_TARGET "i686-linux-android")
    set(RUST_TARGET "i686-linux-android")
    set(ENV_LINKER_VAR "CARGO_TARGET_I686_LINUX_ANDROID_LINKER")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(ANDROID_TARGET "x86_64-linux-android")
    set(RUST_TARGET "x86_64-linux-android")
    set(ENV_LINKER_VAR "CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER")
else()
    message(FATAL_ERROR "Unsupported ABI: ${CMAKE_ANDROID_ARCH_ABI}")
endif()
get_filename_component(LLVM_TOOLCHAIN ${CMAKE_C_COMPILER} DIRECTORY)

set(CLANG_LINKER "${LLVM_TOOLCHAIN}/${ANDROID_TARGET}${CMAKE_ANDROID_API}-clang${CLANG_EXT}")
set(FL_NATIVE_RUST_PATH "${CMAKE_CURRENT_SOURCE_DIR}/target/${RUST_TARGET}/release/libfl_native_rust.a")

add_custom_target(
    setup_rust_target ALL
    COMMAND ${RUSTUP_EXECUTABLE} target add ${RUST_TARGET}
)
add_custom_target(
    fl_native_rust_build ALL
    COMMAND ${CMAKE_COMMAND} -E env
        "${ENV_LINKER_VAR}=${CLANG_LINKER}"
        "RUSTFLAGS=-C linker=${CLANG_LINKER}"
        ${CARGO_EXECUTABLE} build --release --target ${RUST_TARGET}
    DEPENDS setup_rust_target
    BYPRODUCTS ${FL_NATIVE_RUST_PATH}
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
)

add_dependencies(fl_native fl_native_rust_build)

target_link_libraries(fl_native
    android log -Wl,--whole-archive -Wl,--allow-multiple-definition -Wl,--build-id=none ${FL_NATIVE_RUST_PATH}
)
