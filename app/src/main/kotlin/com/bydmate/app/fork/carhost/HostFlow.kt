package com.bydmate.app.fork.carhost

/** Hard limit of one probe session. */
const val MAX_SESSION_MS = 15 * 60_000L

enum class HostState { IDLE, BINDING, HANDSHAKE, CREATING, RUNNING, FAILED, DISCONNECTED }

sealed interface HostEvent {
    object Bound : HostEvent
    data class AppInfoGot(val min: Int, val latest: Int) : HostEvent
    object HandshakeDone : HostEvent
    object Created : HostEvent
    object Started : HostEvent
    data class CallFailed(val call: String, val error: String) : HostEvent
    object BinderDied : HostEvent
    object StopAsked : HostEvent
    object CapReached : HostEvent
}

sealed interface HostAction {
    object GetAppInfo : HostAction
    data class Handshake(val level: Int) : HostAction
    object Create : HostAction
    object StartResume : HostAction
    object GetManagers : HostAction
    object FetchTemplate : HostAction
    object Unbind : HostAction
    data class Log(val what: String, val fields: List<Pair<String, String>>) : HostAction
}

/** Pure decision core of one host session: event in, binder actions out. */
class HostFlow(private val hostLatest: Int) {
    var state: HostState = HostState.IDLE
        private set

    fun on(e: HostEvent): List<HostAction> {
        if (state == HostState.FAILED || state == HostState.DISCONNECTED) return emptyList()
        return when (e) {
            HostEvent.Bound -> if (state == HostState.IDLE) {
                state = HostState.BINDING
                listOf(HostAction.GetAppInfo)
            } else emptyList()

            is HostEvent.AppInfoGot -> if (state == HostState.BINDING) {
                val level = minOf(e.latest, hostLatest)
                if (level < e.min) {
                    state = HostState.FAILED
                    listOf(
                        HostAction.Log(
                            "carhost_refused",
                            listOf(
                                "reason" to "api_mismatch",
                                "app_min" to e.min.toString(),
                                "host" to hostLatest.toString(),
                            ),
                        ),
                        HostAction.Unbind,
                    )
                } else {
                    state = HostState.HANDSHAKE
                    listOf(HostAction.Handshake(level))
                }
            } else emptyList()

            HostEvent.HandshakeDone -> if (state == HostState.HANDSHAKE) {
                state = HostState.CREATING
                listOf(HostAction.Create)
            } else emptyList()

            HostEvent.Created -> if (state == HostState.CREATING) {
                listOf(HostAction.StartResume)
            } else emptyList()

            HostEvent.Started -> if (state == HostState.CREATING) {
                state = HostState.RUNNING
                listOf(HostAction.GetManagers, HostAction.FetchTemplate)
            } else emptyList()

            is HostEvent.CallFailed -> when (state) {
                HostState.RUNNING -> listOf(
                    HostAction.Log(
                        "carhost_call_failed",
                        listOf("call" to e.call, "error" to e.error),
                    ),
                )
                HostState.IDLE -> emptyList()
                else -> {
                    state = HostState.FAILED
                    listOf(
                        HostAction.Log(
                            "carhost_refused",
                            listOf("call" to e.call, "error" to e.error),
                        ),
                        HostAction.Unbind,
                    )
                }
            }

            HostEvent.BinderDied -> if (state == HostState.IDLE) emptyList() else {
                state = HostState.DISCONNECTED
                listOf(HostAction.Log("carhost_disconnected", listOf("reason" to "binder_died")))
            }

            HostEvent.StopAsked -> disconnect("stop")
            HostEvent.CapReached -> disconnect("cap")
        }
    }

    private fun disconnect(reason: String): List<HostAction> {
        if (state == HostState.IDLE) return emptyList()
        state = HostState.DISCONNECTED
        return listOf(
            HostAction.Log("carhost_disconnected", listOf("reason" to reason)),
            HostAction.Unbind,
        )
    }
}
