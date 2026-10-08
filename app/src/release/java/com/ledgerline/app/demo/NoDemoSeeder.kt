package com.ledgerline.app.demo

import com.ledgerline.app.domain.repository.DemoSeeder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoDemoSeeder @Inject constructor() : DemoSeeder {
    override suspend fun seedIfEmpty() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {
    @Binds
    abstract fun bind(impl: NoDemoSeeder): DemoSeeder
}
