package com.procrastilearn.app.di

import com.procrastilearn.app.data.connectivity.AndroidNetworkConnectivityObserver
import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityObserver(impl: AndroidNetworkConnectivityObserver): NetworkConnectivityObserver
}
