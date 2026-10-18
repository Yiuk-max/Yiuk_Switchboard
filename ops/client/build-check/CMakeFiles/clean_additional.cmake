# Additional clean files
cmake_minimum_required(VERSION 3.16)

if("${CONFIG}" STREQUAL "" OR "${CONFIG}" STREQUAL "Release")
  file(REMOVE_RECURSE
  "CMakeFiles\\yiuk-ops_autogen.dir\\AutogenUsed.txt"
  "CMakeFiles\\yiuk-ops_autogen.dir\\ParseCache.txt"
  "yiuk-ops_autogen"
  )
endif()
