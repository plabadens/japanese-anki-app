#include <jni.h>
#include <mecab.h>

#include <cstdint>
#include <exception>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void throwError(JNIEnv* env, const char* message) {
    if (message == nullptr) message = "MeCab native operation failed";
    jclass error = env->FindClass("java/lang/IllegalStateException");
    if (error != nullptr) env->ThrowNew(error, message);
}

std::string fromJava(JNIEnv* env, jstring value) {
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codePoint = chars[index];
        if (codePoint == 0) {
            env->ReleaseStringChars(value, chars);
            throw std::invalid_argument("Input contains a null character");
        }
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && index + 1 < length &&
            chars[index + 1] >= 0xDC00 && chars[index + 1] <= 0xDFFF) {
            codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (chars[++index] - 0xDC00);
        } else if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            codePoint = 0xFFFD;
        }
        if (codePoint <= 0x7F) {
            result.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return result;
}

jstring toJava(JNIEnv* env, const std::string& value) {
    std::vector<jchar> result;
    result.reserve(value.size());
    for (size_t index = 0; index < value.size();) {
        const unsigned char first = static_cast<unsigned char>(value[index++]);
        uint32_t codePoint;
        size_t continuationCount;
        if (first <= 0x7F) {
            codePoint = first;
            continuationCount = 0;
        } else if ((first & 0xE0) == 0xC0) {
            codePoint = first & 0x1F;
            continuationCount = 1;
        } else if ((first & 0xF0) == 0xE0) {
            codePoint = first & 0x0F;
            continuationCount = 2;
        } else if ((first & 0xF8) == 0xF0) {
            codePoint = first & 0x07;
            continuationCount = 3;
        } else {
            codePoint = 0xFFFD;
            continuationCount = 0;
        }
        bool valid = index + continuationCount <= value.size();
        for (size_t part = 0; valid && part < continuationCount; ++part) {
            const unsigned char next = static_cast<unsigned char>(value[index + part]);
            if ((next & 0xC0) != 0x80) {
                valid = false;
            } else {
                codePoint = (codePoint << 6) | (next & 0x3F);
            }
        }
        if (valid) {
            index += continuationCount;
        } else {
            codePoint = 0xFFFD;
        }
        if (codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            codePoint = 0xFFFD;
        }
        if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<jchar>(codePoint));
        } else {
            codePoint -= 0x10000;
            result.push_back(static_cast<jchar>(0xD800 + (codePoint >> 10)));
            result.push_back(static_cast<jchar>(0xDC00 + (codePoint & 0x3FF)));
        }
    }
    return env->NewString(result.data(), static_cast<jsize>(result.size()));
}

std::vector<std::string> csvFields(const char* source) {
    std::vector<std::string> fields;
    std::string field;
    bool quoted = false;
    for (size_t index = 0; source[index] != '\0'; ++index) {
        const char character = source[index];
        if (character == '"') {
            if (quoted && source[index + 1] == '"') {
                field.push_back('"');
                ++index;
            } else {
                quoted = !quoted;
            }
        } else if (character == ',' && !quoted) {
            fields.push_back(field);
            field.clear();
        } else {
            field.push_back(character);
        }
    }
    fields.push_back(field);
    return fields;
}

void appendJsonString(std::string* output, const std::string& value) {
    static const char hex[] = "0123456789abcdef";
    output->push_back('"');
    for (unsigned char character : value) {
        switch (character) {
            case '"': output->append("\\\""); break;
            case '\\': output->append("\\\\"); break;
            case '\b': output->append("\\b"); break;
            case '\f': output->append("\\f"); break;
            case '\n': output->append("\\n"); break;
            case '\r': output->append("\\r"); break;
            case '\t': output->append("\\t"); break;
            default:
                if (character < 0x20) {
                    output->append("\\u00");
                    output->push_back(hex[character >> 4]);
                    output->push_back(hex[character & 0x0F]);
                } else {
                    output->push_back(static_cast<char>(character));
                }
        }
    }
    output->push_back('"');
}

std::string feature(const std::vector<std::string>& fields, size_t index) {
    if (index >= fields.size() || fields[index].empty() || fields[index] == "*") return {};
    return fields[index];
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pierre_japaneseanki_NativeMeCab_nativeOpen(JNIEnv* env, jobject, jstring path) {
    try {
        const std::string directory = fromJava(env, path);
        if (env->ExceptionCheck()) return 0;
        std::vector<std::string> arguments{"japaneseanki", "-r", "/dev/null", "-d", directory};
        std::vector<char*> argv;
        argv.reserve(arguments.size());
        for (std::string& argument : arguments) argv.push_back(&argument[0]);
        mecab_t* tagger = mecab_new(static_cast<int>(argv.size()), argv.data());
        if (tagger == nullptr) throwError(env, mecab_strerror(nullptr));
        return reinterpret_cast<jlong>(tagger);
    } catch (const std::exception& error) {
        throwError(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pierre_japaneseanki_NativeMeCab_nativeClose(JNIEnv*, jobject, jlong handle) {
    if (handle != 0) mecab_destroy(reinterpret_cast<mecab_t*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_pierre_japaneseanki_NativeMeCab_nativeAnalyze(JNIEnv* env, jobject, jlong handle, jstring input) {
    if (handle == 0) {
        throwError(env, "MeCab is not open");
        return nullptr;
    }
    try {
        const std::string sentence = fromJava(env, input);
        if (env->ExceptionCheck()) return nullptr;
        mecab_t* tagger = reinterpret_cast<mecab_t*>(handle);
        const mecab_node_t* node = mecab_sparse_tonode2(tagger, sentence.data(), sentence.size());
        if (node == nullptr) {
            throwError(env, mecab_strerror(tagger));
            return nullptr;
        }

        std::string result = "[";
        bool firstNode = true;
        for (; node != nullptr; node = node->next) {
            if (node->stat == MECAB_BOS_NODE || node->stat == MECAB_EOS_NODE || node->stat == MECAB_EON_NODE) {
                continue;
            }
            if (!firstNode) result.push_back(',');
            firstNode = false;
            const std::string surface(node->surface, node->length);
            const std::vector<std::string> fields = csvFields(node->feature);
            std::string lemma = feature(fields, 10);
            if (lemma.empty()) lemma = surface;
            std::string pos;
            for (size_t index = 0; index < 4; ++index) {
                const std::string part = feature(fields, index);
                if (part.empty()) continue;
                if (!pos.empty()) pos.push_back('-');
                pos += part;
            }
            result.push_back('[');
            appendJsonString(&result, surface);
            result.push_back(',');
            appendJsonString(&result, lemma);
            result.push_back(',');
            appendJsonString(&result, feature(fields, 9));
            result.push_back(',');
            appendJsonString(&result, feature(fields, 6));
            result.push_back(',');
            appendJsonString(&result, pos);
            result.push_back(']');
        }
        result.push_back(']');
        return toJava(env, result);
    } catch (const std::exception& error) {
        throwError(env, error.what());
        return nullptr;
    }
}
