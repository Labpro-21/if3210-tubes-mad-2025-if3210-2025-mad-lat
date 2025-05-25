package com.tubesmobile.purrytify.service

import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData

object DataKeeper {
    var email: String? = null
    var username: String? = null
    var likesAmount: Int = 0
    var songsAmount: Int = 0
    var listenedAmount: Int = 0
    var location: String? = null
    var currentSelectedCapsule: MonthlySoundCapsuleData? = null
}