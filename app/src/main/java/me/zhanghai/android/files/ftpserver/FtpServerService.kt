/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.WakeWifiLock
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat
import org.apache.ftpserver.usermanager.impl.BaseUser
import java.util.concurrent.Executors

class FtpServerService : Service() {
    private var state = State.STOPPED
        set(value) {
            field = value
            _stateLiveData.value = value
        }

    private lateinit var wakeWifiLock: WakeWifiLock

    private lateinit var notification: FtpServerNotification

    private val executorService = Executors.newSingleThreadExecutor()

    private var server: FtpServer? = null

    private var httpServer: HttpServer? = null

    // The server reads its settings when it starts, so it has to be restarted for a change of
    // them to take effect.
    private val settingsObserver = Observer<Any> { restartIfRunning() }
    private val observedSettings =
        listOf(
            Settings.FTP_SERVER_ANONYMOUS_LOGIN,
            Settings.FTP_SERVER_USERNAME,
            Settings.FTP_SERVER_PASSWORD,
            Settings.FTP_SERVER_PORT,
            Settings.FTP_SERVER_HOME_DIRECTORY,
            Settings.FTP_SERVER_WRITABLE,
            Settings.FTP_SERVER_TLS,
            Settings.FTP_SERVER_HTTP_PORT
        )

    override fun onCreate() {
        super.onCreate()

        wakeWifiLock = WakeWifiLock(FtpServerService::class.java.simpleName)
        notification = FtpServerNotification(this)
        for (setting in observedSettings) {
            setting.observeForever(settingsObserver)
        }
        executeStart()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()

        for (setting in observedSettings) {
            setting.removeObserver(settingsObserver)
        }
        executeStop()
        executorService.shutdown()
    }

    private fun restartIfRunning() {
        if (state != State.RUNNING && state != State.STARTING) {
            return
        }
        executorService.execute {
            doStop()
            doStart()
        }
    }

    private fun executeStart() {
        if (state == State.STARTING || state == State.RUNNING) {
            return
        }
        wakeWifiLock.isAcquired = true
        notification.startForeground()
        state = State.STARTING
        executorService.execute { doStart() }
    }

    private fun onStartError(exception: Exception) {
        state = State.STOPPED
        showToast(exception)
        notification.stopForeground()
        wakeWifiLock.isAcquired = false
        stopSelf()
    }

    private fun executeStop() {
        if (state == State.STOPPING || state == State.STOPPED) {
            return
        }
        state = State.STOPPING
        executorService.execute { doStop() }
        notification.stopForeground()
        wakeWifiLock.isAcquired = false
    }

    @WorkerThread
    private fun postState(state: State) {
        mainExecutorCompat.execute { this.state = state }
    }

    @WorkerThread
    private fun doStart() {
        if (server != null) {
            return
        }
        val username: String?
        val password: String?
        if (Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
            username = USERNAME_ANONYMOUS
            password = null
        } else {
            username = Settings.FTP_SERVER_USERNAME.valueCompat
            password = Settings.FTP_SERVER_PASSWORD.valueCompat
        }
        val port = Settings.FTP_SERVER_PORT.valueCompat
        val homeDirectory = Settings.FTP_SERVER_HOME_DIRECTORY.valueCompat
        val writable = Settings.FTP_SERVER_WRITABLE.valueCompat
        val tls = Settings.FTP_SERVER_TLS.valueCompat
        val server = FtpServer(username, password, port, homeDirectory, writable, tls)
        this.server = server
        try {
            server.start()
        } catch (e: Exception) {
            e.printStackTrace()
            this.server = null
            mainExecutorCompat.execute { onStartError(e) }
            return
        }
        val httpPort = Settings.FTP_SERVER_HTTP_PORT.valueCompat
        if (httpPort > 0) {
            val homeDirectoryUri = homeDirectory.toUri().toString()
            val httpServer = HttpServer(
                httpPort, ProviderFileSystemFactory().createFileSystemView(
                    BaseUser().apply {
                        name = username
                        this.homeDirectory = homeDirectoryUri
                    }
                )
            )
            try {
                httpServer.start()
                this.httpServer = httpServer
            } catch (e: Exception) {
                e.printStackTrace()
                mainExecutorCompat.execute { showToast(e.toString()) }
            }
        }
        mainExecutorCompat.execute { FtpServerNsd.register(port, tls) }
        postState(State.RUNNING)
    }

    @WorkerThread
    private fun doStop() {
        mainExecutorCompat.execute { FtpServerNsd.unregister() }
        httpServer?.stop()
        httpServer = null
        val server = server ?: return
        server.stop()
        this.server = null
        postState(State.STOPPED)
    }

    companion object {
        const val USERNAME_ANONYMOUS = "anonymous"

        private val _stateLiveData = MutableLiveData(State.STOPPED)
        val stateLiveData: LiveData<State>
            get() = _stateLiveData

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, FtpServerService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FtpServerService::class.java))
        }

        fun toggle(context: Context) {
            when (val state = _stateLiveData.valueCompat) {
                State.STARTING, State.STOPPING -> {}
                State.RUNNING -> stop(context)
                State.STOPPED -> start(context)
                else -> throw AssertionError(state)
            }
        }
    }

    enum class State {
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED
    }
}
