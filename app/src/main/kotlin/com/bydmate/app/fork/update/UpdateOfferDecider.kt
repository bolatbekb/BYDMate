package com.bydmate.app.fork.update

/**
 * The one decision the feature turns on: given whether a downloaded update is ready and whether
 * the car is parked, what do we show when the app opens? Pure and side-effect free so it is the
 * part that unit tests pin down. fork-local.
 */
object UpdateOfferDecider {

    /**
     * @param hasReadyApk a newer version has finished downloading and is waiting in the cache
     * @param parked      the car is on the handbrake equivalent: gear P and speed 0 (see [ParkState])
     */
    fun decide(hasReadyApk: Boolean, parked: Boolean): UpdateOffer = when {
        !hasReadyApk -> UpdateOffer.SILENT
        parked -> UpdateOffer.OFFER
        else -> UpdateOffer.NOTIFY
    }
}
