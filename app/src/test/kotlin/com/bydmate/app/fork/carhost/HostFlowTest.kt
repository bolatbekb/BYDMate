package com.bydmate.app.fork.carhost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFlowTest {

    private fun running(hostLatest: Int = 6): HostFlow {
        val f = HostFlow(hostLatest)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        f.on(HostEvent.HandshakeDone)
        f.on(HostEvent.Created)
        f.on(HostEvent.Started)
        assertEquals(HostState.RUNNING, f.state)
        return f
    }

    @Test
    fun `happy path`() {
        val f = HostFlow(6)
        assertEquals(HostState.IDLE, f.state)
        assertEquals(listOf<HostAction>(HostAction.GetAppInfo), f.on(HostEvent.Bound))
        assertEquals(HostState.BINDING, f.state)
        assertEquals(listOf<HostAction>(HostAction.Handshake(6)), f.on(HostEvent.AppInfoGot(4, 7)))
        assertEquals(HostState.HANDSHAKE, f.state)
        assertEquals(listOf<HostAction>(HostAction.Create), f.on(HostEvent.HandshakeDone))
        assertEquals(HostState.CREATING, f.state)
        assertEquals(listOf<HostAction>(HostAction.StartResume), f.on(HostEvent.Created))
        assertEquals(
            listOf(HostAction.GetManagers, HostAction.FetchTemplate),
            f.on(HostEvent.Started),
        )
        assertEquals(HostState.RUNNING, f.state)
    }

    @Test
    fun `api mismatch`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        val out = f.on(HostEvent.AppInfoGot(8, 9))
        assertEquals(
            listOf(
                HostAction.Log(
                    "carhost_refused",
                    listOf("reason" to "api_mismatch", "app_min" to "8", "host" to "6"),
                ),
                HostAction.Unbind,
            ),
            out,
        )
        assertEquals(HostState.FAILED, f.state)
    }

    @Test
    fun `refused host`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        val err = "SecurityException: Unknown host"
        val out = f.on(HostEvent.CallFailed("handshake", err))
        assertEquals(
            listOf(
                HostAction.Log(
                    "carhost_refused",
                    listOf("call" to "handshake", "error" to err),
                ),
                HostAction.Unbind,
            ),
            out,
        )
        assertEquals(HostState.FAILED, f.state)
        assertEquals(emptyList<HostAction>(), f.on(HostEvent.CallFailed("handshake", err)))
    }

    @Test
    fun `binder died while running`() {
        val f = running()
        val out = f.on(HostEvent.BinderDied)
        assertEquals(
            listOf<HostAction>(
                HostAction.Log("carhost_disconnected", listOf("reason" to "binder_died")),
            ),
            out,
        )
        assertEquals(HostState.DISCONNECTED, f.state)
        assertTrue(out.none { it == HostAction.Unbind })
    }

    @Test
    fun `cap reached while running`() {
        val f = running()
        assertEquals(
            listOf(
                HostAction.Log("carhost_disconnected", listOf("reason" to "cap")),
                HostAction.Unbind,
            ),
            f.on(HostEvent.CapReached),
        )
        assertEquals(HostState.DISCONNECTED, f.state)
    }

    @Test
    fun `stop asked while running`() {
        val f = running()
        assertEquals(
            listOf(
                HostAction.Log("carhost_disconnected", listOf("reason" to "stop")),
                HostAction.Unbind,
            ),
            f.on(HostEvent.StopAsked),
        )
        assertEquals(HostState.DISCONNECTED, f.state)
    }

    @Test
    fun `stop asked during handshake`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        assertEquals(HostState.HANDSHAKE, f.state)
        assertEquals(
            listOf(
                HostAction.Log("carhost_disconnected", listOf("reason" to "stop")),
                HostAction.Unbind,
            ),
            f.on(HostEvent.StopAsked),
        )
        assertEquals(HostState.DISCONNECTED, f.state)
        assertEquals(emptyList<HostAction>(), f.on(HostEvent.BinderDied))
    }

    @Test
    fun `failed call while running keeps the session`() {
        val f = running()
        val out = f.on(HostEvent.CallFailed("getTemplate", "RemoteException"))
        assertEquals(
            listOf<HostAction>(
                HostAction.Log(
                    "carhost_call_failed",
                    listOf("call" to "getTemplate", "error" to "RemoteException"),
                ),
            ),
            out,
        )
        assertEquals(HostState.RUNNING, f.state)
    }

    @Test
    fun `events in a wrong state are ignored`() {
        val f = HostFlow(6)
        assertEquals(emptyList<HostAction>(), f.on(HostEvent.Created))
        assertEquals(HostState.IDLE, f.state)
    }

    private fun disc(reason: String) = listOf(
        HostAction.Log("carhost_disconnected", listOf("reason" to reason)),
        HostAction.Unbind,
    )

    private fun refused(call: String, error: String) = listOf(
        HostAction.Log("carhost_refused", listOf("call" to call, "error" to error)),
        HostAction.Unbind,
    )

    @Test
    fun `stop in idle`() {
        val f = HostFlow(6)
        assertEquals(disc("stop"), f.on(HostEvent.StopAsked))
        assertEquals(HostState.DISCONNECTED, f.state)
    }

    @Test
    fun `cap in idle`() {
        val f = HostFlow(6)
        assertEquals(disc("cap"), f.on(HostEvent.CapReached))
        assertEquals(HostState.DISCONNECTED, f.state)
    }

    @Test
    fun `call failed in idle`() {
        val f = HostFlow(6)
        assertEquals(refused("bind", "false"), f.on(HostEvent.CallFailed("bind", "false")))
        assertEquals(HostState.FAILED, f.state)
    }

    @Test
    fun `duplicate created is ignored`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        f.on(HostEvent.HandshakeDone)
        assertEquals(listOf<HostAction>(HostAction.StartResume), f.on(HostEvent.Created))
        assertEquals(emptyList<HostAction>(), f.on(HostEvent.Created))
        assertEquals(HostState.CREATING, f.state)
    }

    @Test
    fun `started before created is ignored`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        f.on(HostEvent.HandshakeDone)
        assertEquals(emptyList<HostAction>(), f.on(HostEvent.Started))
        assertEquals(HostState.CREATING, f.state)
    }

    @Test
    fun `call failed in creating`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        f.on(HostEvent.AppInfoGot(4, 7))
        f.on(HostEvent.HandshakeDone)
        assertEquals(refused("onAppCreate", "boom"), f.on(HostEvent.CallFailed("onAppCreate", "boom")))
        assertEquals(HostState.FAILED, f.state)
    }

    @Test
    fun `binder died in binding`() {
        val f = HostFlow(6)
        f.on(HostEvent.Bound)
        assertEquals(
            listOf<HostAction>(
                HostAction.Log("carhost_disconnected", listOf("reason" to "binder_died")),
            ),
            f.on(HostEvent.BinderDied),
        )
        assertEquals(HostState.DISCONNECTED, f.state)
    }
}
