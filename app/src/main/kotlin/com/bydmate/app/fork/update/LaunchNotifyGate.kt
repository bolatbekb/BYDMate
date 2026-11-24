package com.bydmate.app.fork.update

import java.util.concurrent.atomic.AtomicBoolean

/**
 * «Не чаще раза за запуск приложения»: lets the «готово к установке» notice through once per app
 * launch and never again, so a recomposition or a download finishing mid-session cannot nag the
 * driver twice. One instance lives as long as the process (held by the [BackgroundUpdateManager]
 * singleton). fork-local.
 */
class LaunchNotifyGate {
    private val notified = AtomicBoolean(false)

    /** Returns true the first time it is called in this launch, false on every call after. */
    fun shouldNotify(): Boolean = notified.compareAndSet(false, true)
}
