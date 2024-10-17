cmake_minimum_required(VERSION 3.22.1)

project("Portal")

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# MumuEmulator crash? if enable x86_64

#find_package(lsplant REQUIRED CONFIG)
find_package(dobby REQUIRED CONFIG)

add_library(portal SHARED
        main.cpp
        elf_util.cpp
        sensor_hook.cpp)

target_link_libraries(portal android log)
target_link_libraries(portal dobby::dobby)
#target_link_libraries(${CMAKE_PROJECT_NAME} lsplant::lsplant)
