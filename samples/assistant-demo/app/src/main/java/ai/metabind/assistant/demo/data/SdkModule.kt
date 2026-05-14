/*
 * SdkModule.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.data

import ai.metabind.assistant.MetabindAgentProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SdkModule {
    @Provides
    @Singleton
    fun provideMetabindAgentProvider(): MetabindAgentProvider = MetabindAgentProvider()
}
