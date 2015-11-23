cmake_minimum_required(VERSION 2.6)

# Define project and related variables
project (flow)
set (VERSION_MAJOR 3)
set (VERSION_MINOR 0)
set (VERSION_PATCH 1)
set (LIB_NAME flow${VERSION_MAJOR}) # major version is appended to name

# Command-line options
set (LIB_INSTALL_DIR lib CACHE PATH "Path in which to install libraries (Autoconf equivalent to --libdir).")
set (ENABLE_VERSIONED_LIB ON CACHE BOOLEAN "Generate versioned library files and symlinks.")

# Setup JNI
find_package(JNI REQUIRED)
if (JNI_FOUND)
    message (STATUS "JNI include directories: ${JNI_INCLUDE_DIRS}")
endif()

# Include directories
include_directories(.)
include_directories(${JNI_INCLUDE_DIRS})

# Setup main shared library
# Note: major version is appended to library name
add_library(${LIB_NAME} SHARED flow_jni.c platform/posix/flow.c)
if (ENABLE_VERSIONED_LIB)
   set_target_properties(
	${LIB_NAME}
	PROPERTIES
	VERSION 0.${VERSION_MINOR}.${VERSION_PATCH} # major version always 0, it is included in name
	SOVERSION 0
   )
endif()

# Installation targets
install(TARGETS ${LIB_NAME} LIBRARY DESTINATION ${LIB_INSTALL_DIR}) 
