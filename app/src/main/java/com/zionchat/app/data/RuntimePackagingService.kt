package com.zionchat.app.data

interface RuntimePackagingService {
    suspend fun triggerRuntimePackaging(
        app: SavedApp,
        deployUrl: String,
        versionModel: Int
    ): Result<SavedApp>

    suspend fun syncRuntimePackaging(app: SavedApp): Result<SavedApp>
}

class DisabledRuntimePackagingService : RuntimePackagingService {
    override suspend fun triggerRuntimePackaging(
        app: SavedApp,
        deployUrl: String,
        versionModel: Int
    ): Result<SavedApp> {
        return Result.success(
            app.copy(
                runtimeBuildStatus = "disabled",
                runtimeBuildError = "Runtime APK module has been removed.",
                runtimeBuildUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun syncRuntimePackaging(app: SavedApp): Result<SavedApp> {
        return Result.success(app)
    }
}
