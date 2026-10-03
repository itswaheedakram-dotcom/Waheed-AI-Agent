cmake_minimum_required(VERSION 3.22.1)
project(waheed_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED LLAMA_DIR)
    message(FATAL_ERROR "LLAMA_DIR is required")
endif()

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama EXCLUDE_FROM_ALL)

add_library(waheed_native SHARED
    waheed_llm.cpp
)

target_include_directories(waheed_native PRIVATE
    ${LLAMA_DIR}/include
)

find_library(log-lib log)

target_link_libraries(waheed_native
    llama
    ${log-lib}
)
