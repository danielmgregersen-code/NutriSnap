package com.danielag_nutritrack.app.api

import com.google.gson.annotations.SerializedName
import com.danielag_nutritrack.app.data.FoodComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_completion_tokens")
    val maxTokens: Int = 20000
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

data class OpenAIResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
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

interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun analyzeFood(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse

    companion object {
        private const val BASE_URL = "https://api.openai.com/"

        fun create(): OpenAIService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept-Charset", "UTF-8")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
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