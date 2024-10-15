package moe.fuqiuluo.portal.android.coro

import kotlinx.coroutines.channels.Channel

class CoroutineController {
    private val controlChannel = Channel<ControlCommand>(Channel.UNLIMITED)
    var isPaused = false

    suspend fun controlledCoroutine() {
        checkControl()
    }

    private suspend fun checkControl() {
        controlChannel.tryReceive().getOrNull()?.let {
            when (it) {
                ControlCommand.Pause -> {
                    isPaused = true
                    while (controlChannel.receive() != ControlCommand.Resume) {
                        // do nothing
                    }
                    isPaused = false
                }
                ControlCommand.Resume -> {}
            }
        }
    }

    fun pause() {
        controlChannel.trySend(ControlCommand.Pause)
    }

    fun resume() {
        controlChannel.trySend(ControlCommand.Resume)
    }
}

enum class ControlCommand {
    Pause,
    Resume
}