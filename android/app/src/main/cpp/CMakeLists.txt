cmake_minimum_required(VERSION 3.22.1)
project("kestrel_native" LANGUAGES C CXX)

add_library(terminal_pty SHARED terminal_pty.cpp)
find_library(log-lib log)
target_link_libraries(terminal_pty ${log-lib})

add_executable(kestrel_utils kestrel_utils.c)
set_target_properties(kestrel_utils PROPERTIES
    OUTPUT_NAME "kestrel_utils"
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)
target_compile_options(kestrel_utils PRIVATE -fPIE)
target_link_options(kestrel_utils PRIVATE -pie)
