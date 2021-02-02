package com.jaber.uber

import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.jaber.uber.model.DriverInfoModel
import java.lang.StringBuilder

object Common {
    fun buildWelcomeMessage(): String {
    return StringBuilder("Welcome, ")
        .append(currentDriver!!.firstName)
        .append(" ")
        .append(currentDriver!!.lastName)
        .toString()
    }

    var currentDriver: DriverInfoModel? = null
    const val DRIVER_INFO_REFERENCE = "driverInfo"
    const val DRIVERS_LOCATION_REFERENCE: String = "driversLocation"


}