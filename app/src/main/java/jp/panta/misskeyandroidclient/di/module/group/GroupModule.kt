package jp.panta.misskeyandroidclient.di.module.group

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.panta.misskeyandroidclient.model.group.GroupDataSource
import jp.panta.misskeyandroidclient.model.group.impl.InMemoryGroupDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GroupModule {
    @Binds
    @Singleton
    abstract fun groupDataSource(ds: InMemoryGroupDataSource) : GroupDataSource
}