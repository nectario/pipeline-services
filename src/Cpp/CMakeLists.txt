cmake_minimum_required(VERSION 3.20)

project(pipeline_services_cpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)

FetchContent_Declare(
  nlohmann_json
  GIT_REPOSITORY https://github.com/nlohmann/json.git
  GIT_TAG v3.11.3
)
FetchContent_MakeAvailable(nlohmann_json)

FetchContent_Declare(
  cpp_httplib
  GIT_REPOSITORY https://github.com/yhirose/cpp-httplib.git
  GIT_TAG v0.15.3
)
FetchContent_GetProperties(cpp_httplib)
if(NOT cpp_httplib_POPULATED)
  FetchContent_Populate(cpp_httplib)
endif()

add_library(cpp_httplib INTERFACE)
target_include_directories(cpp_httplib INTERFACE ${cpp_httplib_SOURCE_DIR})

find_package(Threads REQUIRED)

add_library(pipeline_services_cpp INTERFACE)
target_include_directories(pipeline_services_cpp INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(pipeline_services_cpp INTERFACE nlohmann_json::nlohmann_json cpp_httplib Threads::Threads)

add_executable(example00_import examples/example00_import.cpp)
target_link_libraries(example00_import PRIVATE pipeline_services_cpp)

add_executable(example01_text_clean examples/example01_text_clean.cpp)
target_link_libraries(example01_text_clean PRIVATE pipeline_services_cpp)

add_executable(example02_json_loader examples/example02_json_loader.cpp)
target_link_libraries(example02_json_loader PRIVATE pipeline_services_cpp)

add_executable(example03_runtime_pipeline examples/example03_runtime_pipeline.cpp)
target_link_libraries(example03_runtime_pipeline PRIVATE pipeline_services_cpp)

add_executable(example04_json_loader_remote_get examples/example04_json_loader_remote_get.cpp)
target_link_libraries(example04_json_loader_remote_get PRIVATE pipeline_services_cpp)

add_executable(example05_metrics_post_action examples/example05_metrics_post_action.cpp)
target_link_libraries(example05_metrics_post_action PRIVATE pipeline_services_cpp)

add_executable(benchmark01_pipeline_run examples/benchmark01_pipeline_run.cpp)
target_link_libraries(benchmark01_pipeline_run PRIVATE pipeline_services_cpp)

enable_testing()
add_executable(pipeline_services_tests tests/pipeline_test.cpp)
target_link_libraries(pipeline_services_tests PRIVATE pipeline_services_cpp)
add_test(NAME pipeline_services_tests COMMAND pipeline_services_tests)

