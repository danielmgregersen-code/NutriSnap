package com.danielag_nutritrack.app.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.danielag_nutritrack.app.data.FoodComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import java.io.IOException
import java.util.concurrent.TimeUnit

data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_completion_tokens")
    val maxTokens: Int = 20000,
    val stream: Boolean? = null
)

data class Message(
    val role: String,
    val content: Any  // Can be String or List<Content>
)

data class Content(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class NutritionInfo(
    val name: String,
    val description: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val category: String,
    val confidence: Int,
    val components: Map<String, FoodComponent>? = null  // NEW: Component breakdown
)

// Server-sent-event chunk shape for streaming chat completions
internal data class StreamChunk(val choices: List<StreamChoice>? = null)
internal data class StreamChoice(
    val delta: StreamDelta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)
internal data class StreamDelta(val content: String? = null)

class OpenAIException(message: String) : Exception(message)

interface OpenAIService {
    @Streaming
    @POST("v1/chat/completions")
    suspend fun analyzeFoodStreaming(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequest
    ): ResponseBody

    companion object {
        private const val BASE_URL = "https://api.openai.com/"
        internal const val TAG = "OpenAIService"

        fun create(): OpenAIService {
            // HEADERS, not BODY: BODY buffers the whole request (megabytes of base64 image)
            // and the whole response into memory, which would also defeat streaming.
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept-Charset", "UTF-8")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                // Uploading several images over a slow mobile connection takes a while.
                .writeTimeout(180, TimeUnit.SECONDS)
                // With streaming this is the gap between chunks, not the total generation time.
                .readTimeout(180, TimeUnit.SECONDS)
                // No ceiling on the call as a whole — a long analysis must not be cut off.
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(OpenAIService::class.java)
        }
    }
}

/**
 * Runs a chat completion and returns the assistant's message.
 *
 * The request is streamed. A non-streamed completion sends nothing until the model is
 * completely done, so a long analysis trips the socket read timeout even though OpenAI
 * finished it — the answer then only exists in the OpenAI logs. Streaming keeps data
 * flowing, so the read timeout applies between chunks instead of to the whole generation.
 *
 * Network failures are retried once, since the first attempt returned nothing usable.
 */
suspend fun OpenAIService.requestCompletion(
    apiKey: String,
    request: OpenAIRequest,
    retries: Int = 1
): String {
    var lastError: IOException? = null
    repeat(retries + 1) { attempt ->
        try {
            return streamCompletion(apiKey, request)
        } catch (e: IOException) {
            lastError = e
            Log.w(OpenAIService.TAG, "Streaming attempt ${attempt + 1} failed: ${e.message}")
        }
    }
    throw lastError ?: IOException("OpenAI request failed")
}

private suspend fun OpenAIService.streamCompletion(
    apiKey: String,
    request: OpenAIRequest
): String = withContext(Dispatchers.IO) {
    val gson = Gson()
    val content = StringBuilder()
    var finishReason: String? = null

    val response = try {
        analyzeFoodStreaming("Bearer $apiKey", request.copy(stream = true))
    } catch (e: HttpException) {
        // Surface what OpenAI actually complained about instead of a bare "HTTP 400"
        val detail = e.response()?.errorBody()?.string().orEmpty().take(500)
        throw OpenAIException(
            "OpenAI rejected the request (HTTP ${e.code()})" + if (detail.isBlank()) "" else ": $detail"
        )
    }

    response.use { body ->
        val source = body.source()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue  // blank lines and SSE comments

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break

            val chunk = try {
                gson.fromJson(payload, StreamChunk::class.java)
            } catch (e: JsonSyntaxException) {
                Log.w(OpenAIService.TAG, "Skipping unparseable stream chunk: ${e.message}")
                null
            } ?: continue

            val choice = chunk.choices?.firstOrNull() ?: continue
            choice.delta?.content?.let { content.append(it) }
            choice.finishReason?.let { finishReason = it }
        }
    }

    Log.d(OpenAIService.TAG, "Stream finished: ${content.length} chars, finish_reason=$finishReason")

    when {
        finishReason == "length" -> throw OpenAIException(
            "The model hit the token limit before finishing its answer. Try again or raise max_completion_tokens."
        )
        content.isEmpty() -> throw OpenAIException(
            "OpenAI returned no content (finish_reason=${finishReason ?: "none"})."
        )
        else -> content.toString()
    }
}
