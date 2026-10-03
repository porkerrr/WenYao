# Native component notices

The resolved APK contains unmodified Filament, gltfio and filament-utils Android
artifacts at **1.52.0**. Their artifact SHA-256 values are in
[RELEASE_DEPENDENCIES.json](../RELEASE_DEPENDENCIES.json).

The official [Android gltfio link configuration](https://github.com/google/filament/blob/v1.52.0/android/gltfio-android/CMakeLists.txt)
links Draco, meshoptimizer, stb, Basis transcoder, Zstandard, and Filament support
libraries. The [filament-utils configuration](https://github.com/google/filament/blob/v1.52.0/android/filament-utils-android/CMakeLists.txt)
also links CivetWeb and image/viewer support. Header-only cgltf/robin-map and the
shader/math components retain their own notices.

Original texts from the fixed `v1.52.0` source tree are retained under
`filament-1.52.0/`, with source URLs and SHA-256 in [sources.json](sources.json):

- cgltf, meshoptimizer, robin-map, stb: original MIT/public-domain alternatives.
- Basis Universal: upstream license plus Apache-2.0, BSD and Zlib component texts.
- Zstandard: bundled BSD notice; Draco: Apache-2.0.
- CivetWeb: complete upstream feature-specific license collection.
- FSR shaders: upstream AMD notice; zlib: upstream Zlib license.
- smol-v, Vulkan Memory Allocator, glslang, SPIR-V Cross/Headers/Tools: upstream texts.
- hat-trie, imgui, libpng and tinyexr: upstream supporting-component texts.

The collection conservatively retains notices for optional native shader/viewer
paths; listing one is not a claim that every upstream optional feature is enabled.
Blender, upstream test frameworks and build-only executables are not bundled.

AndroidX graphics-path and DataStore native artifacts retain the AndroidX
Apache-2.0 notice. graphics-path's published source and upstream native copyright
headers identify Android Open Source Project / Apache-2.0; no claim of project
authorship is made for those binaries.
