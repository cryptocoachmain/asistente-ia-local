#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "AILocal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct ModelContext {
  llama_model *model = nullptr;
  llama_context *ctx = nullptr;
  const llama_vocab *vocab = nullptr;
  int n_ctx = 0;
};

static std::mutex g_mutex;
static ModelContext *g_modelContext = nullptr;
static bool g_backend_initialized = false;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_loadModel(
    JNIEnv *env, jobject /* this */, jstring modelPath, jint nThreads, jint maxContextLength) {
  std::lock_guard<std::mutex> lock(g_mutex);

  if (g_modelContext != nullptr) {
    LOGI("Model already loaded, returning existing context");
    return reinterpret_cast<jlong>(g_modelContext);
  }

  if (!g_backend_initialized) {
    llama_backend_init();
    g_backend_initialized = true;
  }

  const char *path = env->GetStringUTFChars(modelPath, nullptr);
  LOGI("Loading model from: %s", path);

  llama_model_params model_params = llama_model_default_params();
  llama_model *model = llama_model_load_from_file(path, model_params);

  if (!model) {
    LOGE("Failed to load model from: %s", path);
    env->ReleaseStringUTFChars(modelPath, path);
    return 0;
  }

  int trained_context_size = llama_model_n_ctx_train(model);
  LOGI("Model trained context size: %d", trained_context_size);

  int ctx_size = trained_context_size;
  if (maxContextLength > 0) {
    ctx_size = maxContextLength;
  } else if (ctx_size > 2048) {
    LOGI("Limiting context to 2048 for mobile performance");
    ctx_size = 2048;
  }
  if (ctx_size > trained_context_size) {
    ctx_size = trained_context_size;
  }
  if (ctx_size < 512) {
    ctx_size = 512;
  }

  llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = ctx_size;
  ctx_params.n_batch = 512;
  ctx_params.n_ubatch = 512;
  ctx_params.n_threads = nThreads <= 0 ? 4 : nThreads;
  ctx_params.n_threads_batch = nThreads <= 0 ? 4 : nThreads;

  LOGI("Creating context: n_ctx=%d, n_threads=%d", ctx_params.n_ctx,
       ctx_params.n_threads);

  llama_context *ctx = llama_init_from_model(model, ctx_params);
  if (!ctx) {
    LOGE("Failed to create context");
    llama_model_free(model);
    env->ReleaseStringUTFChars(modelPath, path);
    return 0;
  }

  g_modelContext = new ModelContext();
  g_modelContext->model = model;
  g_modelContext->ctx = ctx;
  g_modelContext->n_ctx = llama_n_ctx(ctx);
  g_modelContext->vocab = llama_model_get_vocab(model);

  env->ReleaseStringUTFChars(modelPath, path);
  LOGI("Model loaded successfully at contextPtr: %p", g_modelContext);
  return reinterpret_cast<jlong>(g_modelContext);
}

JNIEXPORT jstring JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_generateText(
    JNIEnv *env, jobject /* this */, jlong contextPtr, jstring prompt,
    jint maxTokens, jfloat temperature, jint topK, jfloat topP) {
  if (contextPtr == 0) {
    return env->NewStringUTF("Error: Context null");
  }

  auto *ctx_wrapper = reinterpret_cast<ModelContext *>(contextPtr);
  const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
  const llama_vocab *vocab = ctx_wrapper->vocab;

  LOGI("Generation started. Prompt length: %zu", strlen(promptStr));

  try {
    // Tokenizar prompt
    int n_needed_estimate = -llama_tokenize(vocab, promptStr, strlen(promptStr),
                                            nullptr, 0, true, true);
    if (n_needed_estimate <= 0)
      n_needed_estimate = 4096; // fallback

    std::vector<llama_token> tokens_list(n_needed_estimate);
    int n_tokens =
        llama_tokenize(vocab, promptStr, strlen(promptStr), tokens_list.data(),
                       tokens_list.size(), true, true);

    if (n_tokens < 0) {
      // Buffer too small, resize
      tokens_list.resize(-n_tokens);
      n_tokens =
          llama_tokenize(vocab, promptStr, strlen(promptStr),
                         tokens_list.data(), tokens_list.size(), true, true);
    }

    if (n_tokens <= 0) {
      env->ReleaseStringUTFChars(prompt, promptStr);
      return env->NewStringUTF("Error: Tokenization failed");
    }

    LOGI("Prompt tokenized into %d tokens", n_tokens);

    // Truncar si es necesario
    int context_size = ctx_wrapper->n_ctx;
    int safe_max_tokens = maxTokens;
    if (safe_max_tokens > context_size - 32) {
      safe_max_tokens = std::max(1, context_size - 32);
    }
    int max_prompt_tokens = context_size - safe_max_tokens - 32; // dejar margen
    if (max_prompt_tokens < 1) {
      max_prompt_tokens = 1;
    }
    int start_token = 0;
    if (n_tokens > max_prompt_tokens) {
      LOGI("Prompt too long, truncating to last %d tokens", max_prompt_tokens);
      start_token = n_tokens - max_prompt_tokens;
      n_tokens = max_prompt_tokens;
    }

    // Limpiar memoria KV
    llama_memory_clear(llama_get_memory(ctx_wrapper->ctx), true);

    // Prefill
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
      batch.token[i] = tokens_list[start_token + i];
      batch.pos[i] = i;
      batch.n_seq_id[i] = 1;
      batch.seq_id[i][0] = 0;
      batch.logits[i] = (i == n_tokens - 1);
    }

    LOGI("Starting prefill with %d tokens...", n_tokens);
    if (llama_decode(ctx_wrapper->ctx, batch) != 0) {
      LOGE("Prefill failed");
      llama_batch_free(batch);
      env->ReleaseStringUTFChars(prompt, promptStr);
      return env->NewStringUTF("Error: Prefill failed");
    }

    // Configurar sampler
    llama_sampler *sampler =
        llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature > 0) {
      llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
      llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
      llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
      llama_sampler_chain_add(sampler, llama_sampler_init_dist(time(nullptr)));
    } else {
      llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    std::string result = "";
    int n_cur = n_tokens;
    int n_gen = 0;

    LOGI("Looping for generation (max %d tokens)...", maxTokens);

    while (n_gen < safe_max_tokens && n_cur < context_size) {
      llama_token token = llama_sampler_sample(sampler, ctx_wrapper->ctx, -1);

      if (llama_vocab_is_eog(vocab, token)) {
        LOGI("EOG detected at token %d", n_gen);
        break;
      }

      char buf[256];
      int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
      if (n > 0) {
        result.append(buf, n);
      }

      llama_batch_free(batch);
      batch = llama_batch_init(1, 0, 1);
      batch.n_tokens = 1;
      batch.token[0] = token;
      batch.pos[0] = n_cur;
      batch.n_seq_id[0] = 1;
      batch.seq_id[0][0] = 0;
      batch.logits[0] = true;

      if (llama_decode(ctx_wrapper->ctx, batch) != 0) {
        LOGE("Decode failed during generation");
        break;
      }

      n_cur++;
      n_gen++;
    }

    LOGI("Generation finished. Tokens: %d, Text length: %zu", n_gen,
         result.length());

    llama_batch_free(batch);
    llama_sampler_free(sampler);
    env->ReleaseStringUTFChars(prompt, promptStr);

    if (result.empty())
      return env->NewStringUTF("[Sin respuesta]");
    return env->NewStringUTF(result.c_str());

  } catch (const std::exception &e) {
    LOGE("C++ Exception: %s", e.what());
    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF((std::string("Crash: ") + e.what()).c_str());
  }
}

JNIEXPORT jstring JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_generateTextStream(
    JNIEnv *env, jobject /* this */, jlong contextPtr, jstring prompt,
    jint maxTokens, jfloat temperature, jint topK, jfloat topP,
    jobject listener) {
  if (contextPtr == 0) {
    return env->NewStringUTF("Error: Context null");
  }

  auto *ctx_wrapper = reinterpret_cast<ModelContext *>(contextPtr);
  const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
  const llama_vocab *vocab = ctx_wrapper->vocab;

  // Obtener información del listener
  jclass listenerClass = env->GetObjectClass(listener);
  jmethodID onTokenMethod =
      env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)V");

  LOGI("Streaming generation started. Prompt length: %zu", strlen(promptStr));

  try {
    // Tokenizar prompt
    int n_needed_estimate = -llama_tokenize(vocab, promptStr, strlen(promptStr),
                                            nullptr, 0, true, true);
    if (n_needed_estimate <= 0)
      n_needed_estimate = 4096;

    std::vector<llama_token> tokens_list(n_needed_estimate);
    int n_tokens =
        llama_tokenize(vocab, promptStr, strlen(promptStr), tokens_list.data(),
                       tokens_list.size(), true, true);

    if (n_tokens < 0) {
      tokens_list.resize(-n_tokens);
      n_tokens =
          llama_tokenize(vocab, promptStr, strlen(promptStr),
                         tokens_list.data(), tokens_list.size(), true, true);
    }

    if (n_tokens <= 0) {
      env->ReleaseStringUTFChars(prompt, promptStr);
      return env->NewStringUTF("Error: Tokenization failed");
    }

    int context_size = ctx_wrapper->n_ctx;
    int safe_max_tokens = maxTokens;
    if (safe_max_tokens > context_size - 32) {
      safe_max_tokens = std::max(1, context_size - 32);
    }
    int max_prompt_tokens = context_size - safe_max_tokens - 32;
    if (max_prompt_tokens < 1) {
      max_prompt_tokens = 1;
    }
    int start_token = 0;
    if (n_tokens > max_prompt_tokens) {
      start_token = n_tokens - max_prompt_tokens;
      n_tokens = max_prompt_tokens;
    }

    llama_memory_clear(llama_get_memory(ctx_wrapper->ctx), true);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
      batch.token[i] = tokens_list[start_token + i];
      batch.pos[i] = i;
      batch.n_seq_id[i] = 1;
      batch.seq_id[i][0] = 0;
      batch.logits[i] = (i == n_tokens - 1);
    }

    if (llama_decode(ctx_wrapper->ctx, batch) != 0) {
      llama_batch_free(batch);
      env->ReleaseStringUTFChars(prompt, promptStr);
      return env->NewStringUTF("Error: Prefill failed");
    }

    llama_sampler *sampler =
        llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature > 0) {
      llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
      llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
      llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
      llama_sampler_chain_add(sampler, llama_sampler_init_dist(time(nullptr)));
    } else {
      llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    std::string result = "";
    int n_cur = n_tokens;
    int n_gen = 0;

    // Secuencias de parada para evitar simulación
    const std::vector<std::string> stop_sequences = {
        "user\n",        "User\n",          "<|user|>",    "<|User|>",
        "[INST]",        "assistant\n",     "Assistant\n", "<|assistant|>",
        "<|Assistant|>", "<|im_start|>",    "<|im_end|>",  "</s>",
        "<end_of_turn>", "<start_of_turn>", "\nuser",      "\nUser",
        "\nassistant",   "\nAssistant",     "\n<|",        "\n["};

    while (n_gen < safe_max_tokens && n_cur < context_size) {
      llama_token token = llama_sampler_sample(sampler, ctx_wrapper->ctx, -1);

      if (llama_vocab_is_eog(vocab, token))
        break;

      char buf[256];
      int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
      if (n > 0) {
        std::string piece(buf, n);
        result += piece;

        // Verificar secuencias de parada en el texto acumulado
        bool should_stop = false;
        for (const auto &seq : stop_sequences) {
          if (result.size() >= seq.size()) {
            if (result.compare(result.size() - seq.size(), seq.size(), seq) ==
                0) {
              // Si encontramos una secuencia de parada, retrocedemos y paramos
              result.erase(result.size() - seq.size());
              should_stop = true;
              break;
            }
          }
        }

        if (should_stop)
          break;

        // Enviar token al listener
        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(listener, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);
      }

      llama_batch_free(batch);
      batch = llama_batch_init(1, 0, 1);
      batch.n_tokens = 1;
      batch.token[0] = token;
      batch.pos[0] = n_cur;
      batch.n_seq_id[0] = 1;
      batch.seq_id[0][0] = 0;
      batch.logits[0] = true;

      if (llama_decode(ctx_wrapper->ctx, batch) != 0)
        break;

      n_cur++;
      n_gen++;
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);
    env->ReleaseStringUTFChars(prompt, promptStr);

    return env->NewStringUTF(result.c_str());

  } catch (const std::exception &e) {
    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF((std::string("Crash: ") + e.what()).c_str());
  }
}

JNIEXPORT void JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_releaseModel(
    JNIEnv *env, jobject /* this */, jlong contextPtr) {
  std::lock_guard<std::mutex> lock(g_mutex);
  if (contextPtr == 0)
    return;

  auto *ctx_wrapper = reinterpret_cast<ModelContext *>(contextPtr);
  if (ctx_wrapper->ctx)
    llama_free(ctx_wrapper->ctx);
  if (ctx_wrapper->model)
    llama_model_free(ctx_wrapper->model);

  delete ctx_wrapper;
  g_modelContext = nullptr;
  LOGI("Model resources released");
}
}
