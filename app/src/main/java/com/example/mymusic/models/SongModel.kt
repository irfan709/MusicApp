package com.example.mymusic.models

import android.net.Uri

data class SongModel(

    val id: Long,
    var songName: String,
    var songImage: Uri,
    var songUri: Uri,
    var songDuration: Int,
    var songSize: Int

)
