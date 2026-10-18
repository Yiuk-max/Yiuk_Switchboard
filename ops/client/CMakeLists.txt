cmake_minimum_required(VERSION 3.21)
project(YiukOps VERSION 1.0 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
find_package(Qt6 6.4 REQUIRED COMPONENTS Widgets Network)
qt_standard_project_setup()
set(CMAKE_AUTORCC ON)
qt_add_executable(yiuk-ops WIN32 MACOSX_BUNDLE
    src/main.cpp src/mainwindow.cpp src/mainwindow.h src/loadchart.cpp src/loadchart.h
    resources.qrc)
target_link_libraries(yiuk-ops PRIVATE Qt6::Widgets Qt6::Network)
