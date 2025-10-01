package com.odinu.forwardsms.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @FormUrlEncoded
    @POST
    suspend fun sendMessagePost(
        @Url url: String,
        @Field("message") message: String,
        @Field("sender") sender: String?,
        @Field("timestamp") timestamp: Long
    ): Response<Unit>

    @GET
    suspend fun sendMessageGet(
        @Url url: String,
        @Query("message") message: String,
        @Query("sender") sender: String?,
        @Query("timestamp") timestamp: Long
    ): Response<Unit>

    @GET
    suspend fun sendGetRequest(
        @Url fullUrl: String
    ): Response<Unit>
}