cmake_minimum_required(VERSION 3.22.1)

project("fodfix" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    fodfix
    SHARED
    fodfix_core.cpp
    fodfix_jni.cpp
)

target_include_directories(
    fodfix
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_options(
    fodfix
    PRIVATE
    -Wall
    -Wextra
    -fvisibility=default
    -O2
)

find_library(log-lib log)

target_link_libraries(
    fodfix
    ${log-lib}
    dl
)
