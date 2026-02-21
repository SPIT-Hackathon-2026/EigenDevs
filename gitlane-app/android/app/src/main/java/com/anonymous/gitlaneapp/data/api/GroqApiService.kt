package com.anonymous.gitlaneapp.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.2
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse
}
