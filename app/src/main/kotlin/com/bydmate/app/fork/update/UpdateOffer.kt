package com.bydmate.app.fork.update

/**
 * What to do with a downloaded-and-ready update when the app opens. fork-local.
 *
 * Decided purely from (is there a ready APK?, is the car parked?) — see [UpdateOfferDecider].
 */
enum class UpdateOffer {
    /** Ready APK and the car is parked (P and speed 0): show the «Установить обновление X» prompt. */
    OFFER,

    /** Ready APK but the car is not parked: show an unobtrusive notice, no install button. */
    NOTIFY,

    /** Nothing ready, or a download is still running: show nothing. */
    SILENT,
}
