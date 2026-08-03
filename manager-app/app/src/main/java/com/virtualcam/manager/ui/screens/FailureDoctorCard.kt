package com.virtualcam.manager.ui.screens

import androidx.compose.runtime.Composable
import com.virtualcam.manager.data.PrerequisiteStatus

/** Stub until APK path is green; full UI re-enabled next. */
@Composable
fun FailureDoctorCard(
    status: PrerequisiteStatus?,
    enabled: Boolean,
    live: Boolean,
    partial: Boolean,
    hook: String,
    frames: Int,
    hits: Int,
    rootOk: Boolean,
    busy: Boolean,
    onProbe: () -> Unit,
    onRefresh: () -> Unit,
) {
}
