package com.vernont.application.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Base service for integrating with Ollama (local Llama3 inference)
 * Provides low-level API calls to the Ollama HTTP endpoint
 */
@Service
class LlamaService(
    private val objectMapper: ObjectMapper,
    @Value("\${ollama.host:http://localhost:11434}") private val ollamaHost: String,
    @Value("\${ollama.model:llama3.2:1b}") private val defaultModel: String,
    @Value("\${ollama.timeout:30}") private val timeoutSeconds: Long
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * Generate a completion from Llama3 given a prompt
     *
     * @param prompt The input prompt to send to the model
     * @param model The model to use (defaults to llama3.2:1b)
     * @param temperature Controls randomness (0.0 = deterministic, 1.0 = creative)
     * @param maxTokens Maximum number of tokens to generate
     * @return The generated text response
     */
    suspend fun generate(
        prompt: String,
        model: String = defaultModel,
        temperature: Double = 0.7,
        maxTokens: Int = 512
    ): String = withContext(Dispatchers.IO) {
        try {
            logger.debug { "Generating completion with model=$model, temp=$temperature, maxTokens=$maxTokens" }

            val requestBody = mapOf(
                "model" to model,
                "prompt" to prompt,
                "stream" to false,
                "options" to mapOf(
                    "temperature" to temperature,
                    "num_predict" to maxTokens
                )
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$ollamaHost/api/generate"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error { "Ollama API error: ${response.statusCode()} - ${response.body()}" }
                throw LlamaServiceException("Ollama API returned ${response.statusCode()}")
            }

            val responseBody = objectMapper.readValue(response.body(), OllamaGenerateResponse::class.java)

            logger.debug { "Generated ${responseBody.response.length} characters in ${responseBody.totalDuration?.div(1_000_000)}ms" }

            responseBody.response

        } catch (ex: Exception) {
            logger.error(ex) { "Failed to generate completion from Ollama" }
            throw LlamaServiceException("Failed to generate completion: ${ex.message}", ex)
        }
    }

    /**
     * Generate structured JSON output from Llama3
     * Useful for extracting structured data from text
     */
    suspend fun generateJson(
        prompt: String,
        model: String = defaultModel,
        temperature: Double = 0.3 // Lower for more deterministic JSON
    ): String = withContext(Dispatchers.IO) {
        try {
            val jsonPrompt = """
                $prompt

                Respond ONLY with valid JSON. Do not include any explanatory text before or after the JSON.
            """.trimIndent()

            val response = generate(jsonPrompt, model, temperature, maxTokens = 1024)

            // Extract JSON from response (remove markdown code blocks if present)
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Validate it's valid JSON
            objectMapper.readTree(cleanedResponse)

            cleanedResponse

        } catch (ex: Exception) {
            logger.error(ex) { "Failed to generate valid JSON from Ollama" }
            throw LlamaServiceException("Failed to generate valid JSON: ${ex.message}", ex)
        }
    }

    /**
     * Generate a streaming completion from Llama3
     * Emits tokens as they are generated for real-time display
     *
     * @param prompt The input prompt
     * @param model The model to use
     * @param temperature Controls randomness
     * @param maxTokens Maximum tokens to generate
     * @return Flow of text chunks as they're generated
     */
    suspend fun generateStream(
        prompt: String,
        model: String = defaultModel,
        temperature: Double = 0.7,
        maxTokens: Int = 512
    ): Flow<String> = withContext(Dispatchers.IO) {
        flow {
            try {
                logger.debug { "Starting streaming generation with model=$model" }

                val requestBody = mapOf(
                    "model" to model,
                    "prompt" to prompt,
                    "stream" to true, // Enable streaming
                    "options" to mapOf(
                        "temperature" to temperature,
                        "num_predict" to maxTokens
                    )
                )

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$ollamaHost/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build()

                // Use InputStream for streaming response
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

                if (response.statusCode() != 200) {
                    logger.error { "Ollama streaming API error: ${response.statusCode()}" }
                    throw LlamaServiceException("Ollama API returned ${response.statusCode()}")
                }

                // Read streaming response line by line
                response.body().bufferedReader().use { reader ->
                    var currentLine: String?
                    while (reader.readLine().also { currentLine = it } != null) {
                        val line = currentLine
                        if (line.isNullOrBlank()) continue
                        try {
                            val chunk = objectMapper.readValue(line, OllamaStreamChunk::class.java)
                            if (chunk.response.isNotEmpty()) {
                                emit(chunk.response) // Emit each token
                            }
                        } catch (ex: Exception) {
                            logger.warn { "Failed to parse stream chunk: $line" }
                        }
                    }
                }

                logger.debug { "Streaming generation completed" }

            } catch (ex: Exception) {
                logger.error(ex) { "Failed to stream from Ollama" }
                throw LlamaServiceException("Failed to stream: ${ex.message}", ex)
            }
        }
    }

    /**
     * Check if Ollama is available and the model is loaded
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$ollamaHost/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.warn { "Ollama health check failed: ${response.statusCode()}" }
                return@withContext false
            }

            val models = objectMapper.readValue(response.body(), OllamaModelsResponse::class.java)
            val modelExists = models.models.any { it.name == defaultModel }

            if (!modelExists) {
                logger.warn { "Model $defaultModel not found. Available: ${models.models.map { it.name }}" }
            }

            modelExists

        } catch (ex: Exception) {
            logger.error(ex) { "Ollama health check failed" }
            false
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    @JsonProperty("created_at") val createdAt: String,
    val done: Boolean,
    @JsonProperty("done_reason") val doneReason: String?,
    @JsonProperty("total_duration") val totalDuration: Long?,
    @JsonProperty("load_duration") val loadDuration: Long?,
    @JsonProperty("prompt_eval_count") val promptEvalCount: Int?,
    @JsonProperty("eval_count") val evalCount: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaStreamChunk(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false
)

data class OllamaModelsResponse(
    val models: List<OllamaModel>
)

data class OllamaModel(
    val name: String,
    val size: Long,
    @JsonProperty("modified_at") val modifiedAt: String
)

class LlamaServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
