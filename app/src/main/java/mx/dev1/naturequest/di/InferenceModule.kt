package mx.dev1.naturequest.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mx.dev1.naturequest.data.hunt.ResourceFallbackItems
import mx.dev1.naturequest.data.inference.LiteRtInferenceEngine
import mx.dev1.naturequest.data.model.ModelStore
import mx.dev1.naturequest.data.prompts.PromptRepository
import mx.dev1.naturequest.domain.hunt.FallbackItems
import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.prompt.PromptSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {
    @Binds
    abstract fun bindPromptSource(impl: PromptRepository): PromptSource

    @Binds
    abstract fun bindFallbackItems(impl: ResourceFallbackItems): FallbackItems

    companion object {
        /** One engine for the whole app: requests are serialized and the model is loaded on demand. */
        @Provides
        @Singleton
        fun provideInferenceEngine(
            @ApplicationContext context: Context,
            modelStore: ModelStore,
        ): InferenceEngine = LiteRtInferenceEngine(context, modelStore)
    }
}
