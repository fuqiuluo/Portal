package moe.fuqiuluo.portal.android.coro

import kotlinx.coroutines.channels.Channel

class CoroutineRouteMock {
    private val routeMockChannel = Channel<RouteMockCommand>(Channel.UNLIMITED)
    var isPaused = false

    suspend fun routeMockCoroutine() {
        checkRouteMockStatus()
    }

    private suspend fun checkRouteMockStatus() {
        routeMockChannel.tryReceive().getOrNull()?.let {
            when (it) {
                RouteMockCommand.Pause -> {
                    isPaused = true
                    while (routeMockChannel.receive() != RouteMockCommand.Resume) {
                        // do nothing
                    }
                    isPaused = false
                }
                RouteMockCommand.Resume -> {}
            }
        }
    }

    fun pause() {
        routeMockChannel.trySend(RouteMockCommand.Pause)
    }

    fun resume() {
        routeMockChannel.trySend(RouteMockCommand.Resume)
    }
}

enum class RouteMockCommand {
    Pause,
    Resume
}