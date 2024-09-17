package com.example.shuntingapp

import com.example.shuntingapp.message.ExecutionMsg
import com.example.shuntingapp.message.ReceiptMsg
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @GET("{endpoint}")
    fun getDynamicEndpoint(
        @Path("endpoint") endpoint: String
    ): Call<ApiResponse>

    @POST("{endpoint}")
    fun postReceipt(
        @Path("endpoint") endpoint: String,
        @Body requestData: ReceiptMsg
    ): Call<ApiResponse>

    @POST("{endpoint}")
    fun postExecution(
        @Path("endpoint") endpoint: String,
        @Body requestData: ExecutionMsg
    ): Call<ApiResponse>

    @POST("{endpoint}")
    fun postImageString(
        @Path("endpoint") endpoint: String,
        @Body requestData: String
    ): Call<ApiResponse>

    @Multipart
    @POST("{endpoint}")
    fun postImage(
        @Path("endpoint") endpoint: String,
        @Part("IsNew") isNew: Boolean,
        @Part("DevID") devId: Int,
        @Part("LocomNum") locomNum: Int,
        @Part("Time") time: String,
        @Part image: MultipartBody.Part
    ): Call<ApiResponse>
}

data class ApiResponse(
    val Code: Int,
    val Message: String,
    val Data: JsonObject
)