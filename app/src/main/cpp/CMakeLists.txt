cmake_minimum_required(VERSION 3.22.1)

project("ailocal")

# Configurar C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimizaciones
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -fPIC")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG -fPIC")

# Deshabilitar features no necesarias de llama.cpp
set(LLAMA_CUBLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Backend CPU solamente
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DGGML_USE_CPU")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DGGML_USE_CPU")

# Añadir llama.cpp
add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

# Crear biblioteca JNI
add_library(ailocal SHARED
    jni-bridge.cpp
)

# Enlazar con llama y librerías Android
target_link_libraries(ailocal
    llama
    log
)

# Incluir headers
target_include_directories(ailocal PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)
