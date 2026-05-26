cmake_minimum_required(VERSION 3.22.1)
project("auracam")

# Add our high performance native computational photography library
add_library(
    auracam
    SHARED
    aura_pipeline.cpp
)

# Locate essential Android NDk library stubs
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics) # Allows fast native read/write on Java/Kotlin Bitmaps

target_link_libraries(
    auracam
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)
