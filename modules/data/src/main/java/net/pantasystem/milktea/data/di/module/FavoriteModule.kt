package net.pantasystem.milktea.data.di.module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.pantasystem.milktea.data.infrastructure.note.favorite.FavoriteRepositoryImpl
import net.pantasystem.milktea.model.note.favorite.FavoriteRepository
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class FavoriteModule {

    @Binds
    @Singleton
    abstract fun provideFavoriteRepository(
        impl: FavoriteRepositoryImpl
    ): FavoriteRepository
}