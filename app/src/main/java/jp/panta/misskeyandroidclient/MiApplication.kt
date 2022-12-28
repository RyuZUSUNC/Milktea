package jp.panta.misskeyandroidclient

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import jp.panta.misskeyandroidclient.util.DebuggerSetupManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.app_store.setting.SettingStore
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common_android.platform.activeNetworkFlow
import net.pantasystem.milktea.data.infrastructure.drive.ClearUnUsedDriveFileCacheJob
import net.pantasystem.milktea.data.infrastructure.streaming.ChannelAPIMainEventDispatcherAdapter
import net.pantasystem.milktea.data.infrastructure.streaming.MediatorMainEventDispatcher
import net.pantasystem.milktea.data.streaming.SocketWithAccountProvider
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.account.ClientIdRepository
import net.pantasystem.milktea.worker.instance.ScheduleAuthInstancesPostWorker
import net.pantasystem.milktea.worker.meta.SyncMetaWorker
import net.pantasystem.milktea.worker.sw.RegisterAllSubscriptionRegistration
import net.pantasystem.milktea.worker.user.SyncLoggedInUserInfoWorker
import javax.inject.Inject

//基本的な情報はここを返して扱われる
@HiltAndroidApp
class MiApplication : Application(), Configuration.Provider {

    @Inject
    internal lateinit var mAccountRepository: AccountRepository

    @Inject
    internal lateinit var mSettingStore: SettingStore


    @Inject
    internal lateinit var mAccountStore: AccountStore


    @Inject
    internal lateinit var mSocketWithAccountProvider: SocketWithAccountProvider


    @Inject
    internal lateinit var mainEventDispatcherFactory: MediatorMainEventDispatcher.Factory

    @Inject
    internal lateinit var channelAPIMainEventDispatcherAdapter: ChannelAPIMainEventDispatcherAdapter

    @Inject
    internal lateinit var applicationScope: CoroutineScope

    @Inject
    internal lateinit var lf: Logger.Factory

    private val logger: Logger by lazy {
        lf.create("MiApplication")
    }

    @Inject
    internal lateinit var clearDriveCacheJob: ClearUnUsedDriveFileCacheJob


    @Inject
    internal lateinit var clientIdRepository: ClientIdRepository

    @Inject
    internal lateinit var debuggerSetupManager: DebuggerSetupManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        debuggerSetupManager.setup(this)

        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mainThreadId = Looper.getMainLooper().thread.id
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("MiApplication", "Thread上で致命的なエラーが発生しました thread id:${t.id}, name:${t.name}", e)
            if (mainThreadId == t.id) {
                defaultUncaughtExceptionHandler?.uncaughtException(t, e)
            }
        }

        val mainEventDispatcher = mainEventDispatcherFactory.create()
        channelAPIMainEventDispatcherAdapter(mainEventDispatcher)

        mAccountRepository.addEventListener { ev ->
            applicationScope.launch {
                try {
                    if (ev is AccountRepository.Event.Deleted) {
                        mSocketWithAccountProvider.get(ev.accountId)?.disconnect()
                    }
                    mAccountStore.initialize()
                } catch (e: Exception) {
                    logger.error("アカウントの更新があったのでStateを更新しようとしたところ失敗しました。", e)
                }
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            clearDriveCacheJob.checkAndClear().onFailure {
                logger.error("ドライブのキャッシュのクリーンアップに失敗しました", it)
            }
        }

        applicationScope.launch {
            try {
                mAccountStore.initialize()
            } catch (e: Exception) {
                logger.error("load account error", e = e)
            }
        }

        activeNetworkFlow().distinctUntilChanged().onEach {
            logger.debug("接続状態が変化:${if (it) "接続" else "未接続"}")
            mSocketWithAccountProvider.all().forEach { socket ->
                if (it) {
                    socket.onNetworkActive()
                } else {
                    socket.onNetworkInActive()
                }
            }
        }.catch { e ->
            logger.error("致命的なエラー", e)
        }.launchIn(applicationScope + Dispatchers.IO)


        WorkManager.getInstance(this).apply {
            enqueue(RegisterAllSubscriptionRegistration.createWorkRequest())
            enqueueUniquePeriodicWork(
                "syncMeta",
                ExistingPeriodicWorkPolicy.REPLACE,
                SyncMetaWorker.createPeriodicWorkRequest()
            )
            enqueueUniquePeriodicWork(
                "syncLoggedInUsers",
                ExistingPeriodicWorkPolicy.REPLACE,
                SyncLoggedInUserInfoWorker.createPeriodicWorkRequest(),
            )
            enqueueUniquePeriodicWork(
                "scheduleAuthInstancePostWorker",
                ExistingPeriodicWorkPolicy.REPLACE,
                ScheduleAuthInstancesPostWorker.createPeriodicWorkRequest(),
            )
        }

        applicationScope.launch {
            mSettingStore.configState.map {
                it.isCrashlyticsCollectionEnabled
            }.distinctUntilChanged().collect {
                FirebaseCrashlytics.getInstance()
                    .setCrashlyticsCollectionEnabled(it.isEnable)
            }
        }

        applicationScope.launch {
            mSettingStore.configState.map {
                it.isAnalyticsCollectionEnabled
            }.distinctUntilChanged().collect {
                FirebaseAnalytics.getInstance(applicationContext)
                    .setAnalyticsCollectionEnabled(it.isEnabled)
            }
        }

        FirebaseAnalytics.getInstance(this).setUserId(
            clientIdRepository.getOrCreate().clientId
        )
        FirebaseCrashlytics.getInstance().setUserId(
            clientIdRepository.getOrCreate().clientId
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

}