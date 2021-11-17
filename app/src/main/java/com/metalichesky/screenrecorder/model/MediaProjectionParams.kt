package com.metalichesky.screenrecorder.model

import android.content.Intent
import java.io.Serializable

data class MediaProjectionParams(
    val resultCode: Int,
    val data: Intent
): Serializable