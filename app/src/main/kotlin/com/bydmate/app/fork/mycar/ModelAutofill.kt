package com.bydmate.app.fork.mycar

import com.bydmate.app.fork.cells.data.CarProfile

/**
 * Maps the head unit's AutoType model code to a [CarProfile]. 153 is the owner's Leopard 5; the
 * Leopard 8 shares this profile (owner, 2026-11-25). Unknown codes map to NONE. More head-unit codes
 * from other users are to be gathered later and added to this map.
 */
fun profileForAutoType(code: Int?): CarProfile = when (code) {
    LEOPARD_5_AUTOTYPE -> CarProfile.LEOPARD5_318
    else -> CarProfile.NONE
}

/**
 * One-shot model autofill at first setup: when the model was never autofilled and none is chosen,
 * set it from the head unit code and remember that it was done. A known code is required to mark the
 * flag, so a transient failed read (null) is retried next time; once set, the user's later choice of
 * «Не выбрано» sticks because the flag is already set.
 */
fun autofillModelOnce(
    alreadyAutofilled: Boolean,
    currentProfile: CarProfile,
    code: Int?,
    setProfile: (CarProfile) -> Unit,
    markAutofilled: () -> Unit,
) {
    if (alreadyAutofilled || currentProfile != CarProfile.NONE) return
    val detected = profileForAutoType(code)
    if (detected == CarProfile.NONE) return
    setProfile(detected)
    markAutofilled()
}

private const val LEOPARD_5_AUTOTYPE = 153
