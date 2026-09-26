cmake_minimum_required(VERSION 3.22.1)
project(japaneseanki_mecab LANGUAGES CXX)

if(NOT DEFINED MECAB_SOURCE_DIR)
    message(FATAL_ERROR "MECAB_SOURCE_DIR must point to the pinned MeCab source")
endif()

configure_file(config.h "${CMAKE_CURRENT_BINARY_DIR}/config.h" COPYONLY)

add_library(japaneseanki_mecab SHARED
    native_mecab.cpp
    "${MECAB_SOURCE_DIR}/src/viterbi.cpp"
    "${MECAB_SOURCE_DIR}/src/tagger.cpp"
    "${MECAB_SOURCE_DIR}/src/utils.cpp"
    "${MECAB_SOURCE_DIR}/src/eval.cpp"
    "${MECAB_SOURCE_DIR}/src/iconv_utils.cpp"
    "${MECAB_SOURCE_DIR}/src/dictionary_rewriter.cpp"
    "${MECAB_SOURCE_DIR}/src/dictionary_generator.cpp"
    "${MECAB_SOURCE_DIR}/src/dictionary_compiler.cpp"
    "${MECAB_SOURCE_DIR}/src/context_id.cpp"
    "${MECAB_SOURCE_DIR}/src/connector.cpp"
    "${MECAB_SOURCE_DIR}/src/nbest_generator.cpp"
    "${MECAB_SOURCE_DIR}/src/writer.cpp"
    "${MECAB_SOURCE_DIR}/src/string_buffer.cpp"
    "${MECAB_SOURCE_DIR}/src/param.cpp"
    "${MECAB_SOURCE_DIR}/src/tokenizer.cpp"
    "${MECAB_SOURCE_DIR}/src/char_property.cpp"
    "${MECAB_SOURCE_DIR}/src/dictionary.cpp"
    "${MECAB_SOURCE_DIR}/src/feature_index.cpp"
    "${MECAB_SOURCE_DIR}/src/lbfgs.cpp"
    "${MECAB_SOURCE_DIR}/src/learner_tagger.cpp"
    "${MECAB_SOURCE_DIR}/src/learner.cpp"
    "${MECAB_SOURCE_DIR}/src/libmecab.cpp"
)

target_include_directories(japaneseanki_mecab PRIVATE
    "${MECAB_SOURCE_DIR}/src"
    "${CMAKE_CURRENT_BINARY_DIR}"
)
target_compile_definitions(japaneseanki_mecab PRIVATE
    HAVE_CONFIG_H
    DIC_VERSION=102
    MECAB_USE_UTF8_ONLY
    _LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION
)
target_compile_features(japaneseanki_mecab PRIVATE cxx_std_14)
target_compile_options(japaneseanki_mecab PRIVATE -Wno-register)
