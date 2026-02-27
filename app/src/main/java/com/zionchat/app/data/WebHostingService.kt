package com.zionchat.app.data

interface WebHostingService {
    suspend fun validateConfig(config: WebHostingConfig): Result<Unit>

    suspend fun deployApp(
        appId: String,
        html: String,
        config: WebHostingConfig
    ): Result<String>
}

class DisabledWebHostingService : WebHostingService {
    override suspend fun validateConfig(config: WebHostingConfig): Result<Unit> {
        return Result.failure(IllegalStateException("Web hosting module has been removed."))
    }

    override suspend fun deployApp(
        appId: String,
        html: String,
        config: WebHostingConfig
    ): Result<String> {
        return Result.failure(IllegalStateException("Web hosting module has been removed."))
    }
}
