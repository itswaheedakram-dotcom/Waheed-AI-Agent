#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "llama.h"

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;

static void unload_locked() {
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_waheed_aiaagent_LocalLlmEngine_nativeLoad(
        JNIEnv * env, jclass, jstring jpath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    unload_locked();

    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_model) {
        return env->NewStringUTF("ERROR: Could not load GGUF model.");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 512;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;

    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        unload_locked();
        return env->NewStringUTF("ERROR: Could not create local AI context.");
    }

    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return env->NewStringUTF("OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_waheed_aiaagent_LocalLlmEngine_nativeGenerate(
        JNIEnv * env, jclass, jstring jprompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx || !g_sampler) {
        return env->NewStringUTF("ERROR: Local model is not loaded.");
    }

    const char * promptChars = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(jprompt, promptChars);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(),
                                          nullptr, 0, true, true);
    if (n_prompt <= 0 || n_prompt > 1900) {
        return env->NewStringUTF("ERROR: Prompt is too long for the local context.");
    }

    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(),
                       tokens.data(), n_prompt, true, true) < 0) {
        return env->NewStringUTF("ERROR: Could not tokenize prompt.");
    }

    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_sampler_reset(g_sampler);

    llama_batch batch = llama_batch_init(n_prompt, 0, 1);
    for (int i = 0; i < n_prompt; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_prompt - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("ERROR: Local model could not process the prompt.");
    }

    std::string result;
    const int limit = std::max(1, std::min((int)maxTokens, 256));
    int32_t pos = n_prompt;

    for (int i = 0; i < limit; ++i) {
        llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char piece[256];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) result.append(piece, n);

        batch.n_tokens = 1;
        batch.token[0] = id;
        batch.pos[0] = pos++;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(g_ctx, batch) != 0) break;
    }

    llama_batch_free(batch);

    if (result.empty()) result = "I could not generate a local response.";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_waheed_aiaagent_LocalLlmEngine_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
}
