package com.capji.netpulse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import com.capji.netpulse.domain.identify.DeviceIdentifier
import com.capji.netpulse.domain.model.UiText

/** Vendor names are stored language-neutral ("@private", "@unknown") and localised only when shown. */
@Composable
fun vendorLabel(vendor: String): String = when (vendor) {
    DeviceIdentifier.VENDOR_PRIVATE -> stringResource(R.string.vendor_private)
    DeviceIdentifier.VENDOR_UNKNOWN -> stringResource(R.string.vendor_unknown)
    else -> vendor
}

@Composable
fun rateLabel(kbps: Int): String = if (kbps == 0) stringResource(R.string.unlimited) else formatRate(kbps)

/** Same as [rateLabel] but for non-composable code (toasts): "unlimited" becomes a UiText argument. */
fun rateArg(kbps: Int): Any = if (kbps == 0) UiText(R.string.unlimited) else formatRate(kbps)
