package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.inbox.InboxRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InboxModule {

    @Binds
    @Singleton
    abstract fun bindInboxRepository(impl: InboxRepositoryImpl): InboxRepository
}
