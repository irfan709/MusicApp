package com.example.mymusic

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mymusic.adapters.SongAdapter
import com.example.mymusic.databinding.ActivityMainBinding
import com.example.mymusic.databinding.SongPlayingScreenBinding
import com.example.mymusic.models.SongModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songScreenBinding: SongPlayingScreenBinding
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Intent>
    private var allSongs: ArrayList<SongModel> = ArrayList()
    private lateinit var songsAdapter: SongAdapter
    private lateinit var player: ExoPlayer
    private lateinit var songScreen: ConstraintLayout
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSongScreenRunnable: Runnable
    private var songRepeatMode: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songScreen = findViewById(R.id.songScreen)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        storagePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    fetchAllSongs()
                } else {
                    requestStoragePermission()
                }
            }

        player = ExoPlayer.Builder(this).build()

        checkPermissions()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem != null) {
                    binding.homeSongName.text = mediaItem.mediaMetadata.title
                }
            }

        })

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (songScreen.visibility == View.VISIBLE) {
                    exitPlayerScreen()
                } else {
                    isEnabled = false
                    super.handleOnBackCancelled()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun checkPermissions() {
        if (!hasStoragePermission()) {
            requestStoragePermission()
        } else {
            fetchAllSongs()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showPermissionExplanationDialog()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    private fun showPermissionDeniedMessage() {
        showToast("Permission denied...")
    }

    private fun fetchAllSongs() {
        val songsList = ArrayList<SongModel>()

        val mediaStoreUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC"

        contentResolver.query(mediaStoreUri, projection, null, null, sortOrder)?.use { cursor ->
            val songIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(songIdColumn)
                val name = cursor.getStringOrNull(nameColumn)?.substringBeforeLast('.')
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getInt(sizeColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val songUri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val songImage = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                val song = SongModel(id, name.orEmpty(), songImage, songUri, duration, size)

                songsList.add(song)
            }
            displaySongs(songsList)
        }
    }


    private fun displaySongs(songsList: ArrayList<SongModel>) {

        if (songsList.size == 0) {
            showToast("No songs in this device")
        }

        allSongs.clear()
        allSongs.addAll(songsList)

        val layoutManager = LinearLayoutManager(this)
        binding.songsRecyclerView.layoutManager = layoutManager
        songsAdapter = SongAdapter(songsList, player, songScreen) { song ->
            showSongPlayingScreen(song)
        }
//        songsAdapter = SongAdapter(songsList, player, { song ->
//            showSongPlayingScreen(song)
//        }, songScreen)

        val scaleInAdapter = ScaleInAnimationAdapter(songsAdapter)
        scaleInAdapter.setDuration(1000)
        scaleInAdapter.setInterpolator(OvershootInterpolator())
        scaleInAdapter.setFirstOnly(false)
        binding.songsRecyclerView.adapter = scaleInAdapter
    }

    private fun showSongPlayingScreen(song: SongModel) {
        showPlayerScreen(song)
        showMediaData()
        songScreenBinding.backArrowPlayScreen.setOnClickListener { exitPlayerScreen() }
        songScreenBinding.songsList.setOnClickListener { exitPlayerScreen() }
        songScreenBinding.shuffleSong.setOnClickListener { setSongRepeatModes() }
    }

    private fun setSongRepeatModes() {
        when (songRepeatMode) {
            1 -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                songRepeatMode = 2
                songScreenBinding.shuffleSong.setImageResource(R.drawable.repeat_one_on)
                showToast("Repeating the current song")
            }

            2 -> {
                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_ALL
                songRepeatMode = 3
                songScreenBinding.shuffleSong.setImageResource(R.drawable.shuffle)
                showToast("Shuffling all songs")
            }

            3 -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
                songRepeatMode = 1
                songScreenBinding.shuffleSong.setImageResource(R.drawable.repeat_on)
                showToast("Repeating all songs")
            }
        }
    }

    private fun showPlayerScreen(song: SongModel) {
        songScreenBinding = SongPlayingScreenBinding.inflate(layoutInflater)
        setContentView(songScreenBinding.root)
        songScreenBinding.songScreen.visibility = View.VISIBLE

        songScreenBinding.songNameScreen.text = song.songName

        songScreenBinding.nextSong.setOnClickListener { skipToNextSong() }
        songScreenBinding.previousSong.setOnClickListener { skipToPreviousSong() }
        songScreenBinding.songPlayPause.setOnClickListener { playOrPauseSong() }

        songScreenBinding.songSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            var progressValue = 0
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressValue = songScreenBinding.songSeekBar.progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (player.playbackState == ExoPlayer.STATE_READY) {
                    songScreenBinding.songSeekBar.progress = progressValue
                    songScreenBinding.songProgress.text = getSongProgress(progressValue.toLong())
                    player.seekTo(progressValue.toLong())
                }
            }

        })
    }

    private fun skipToNextSong() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
            if (!player.isPlaying) {
                player.play()
                songScreenBinding.songPlayPause.setImageResource(R.drawable.pause_circle)
            }
        }
    }

    private fun skipToPreviousSong() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
            if (!player.isPlaying) {
                player.play()
                songScreenBinding.songPlayPause.setImageResource(R.drawable.pause_circle)
            }
        }
    }

    private fun playOrPauseSong() {
        if (player.isPlaying) {
            player.pause()
            songScreenBinding.songPlayPause.setImageResource(R.drawable.play_circle)
            binding.homePlayPauseSong.setImageResource(R.drawable.play_circle)
        } else {
            player.play()
            songScreenBinding.songPlayPause.setImageResource(R.drawable.pause_circle)
            binding.homePlayPauseSong.setImageResource(R.drawable.pause_circle)
        }
    }

    private fun updateSongScreen() {
        songScreenBinding.songProgress.text = getSongProgress(player.currentPosition)
        songScreenBinding.songSeekBar.progress = player.currentPosition.toInt()

        handler.postDelayed(updateSongScreenRunnable, 1000)
    }


    private fun exitPlayerScreen() {
        songScreen.visibility = View.GONE
        setContentView(binding.root)
    }

    private fun showMediaData() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val duration = player.duration
                if (mediaItem != null) {
                    songScreenBinding.songNameScreen.text = mediaItem.mediaMetadata.title
                }
                songScreenBinding.songDuration.text = getSongProgress(duration)
                songScreenBinding.songSeekBar.max = duration.toInt()
                updateSongScreenRunnable = Runnable { updateSongScreen() }
                handler.post(updateSongScreenRunnable)
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
                if (state == Player.STATE_READY) {
                    val duration = player.duration
                    songScreenBinding.songProgress.text = getSongProgress(player.currentPosition)
                    songScreenBinding.songSeekBar.progress = player.currentPosition.toInt()
                    songScreenBinding.songDuration.text = getSongProgress(duration)
                    songScreenBinding.songSeekBar.max = duration.toInt()
                    songScreenBinding.songPlayPause.setImageResource(R.drawable.pause_circle)
                    binding.homePlayPauseSong.setImageResource(R.drawable.pause_circle)
                    updateSongScreenRunnable = Runnable { updateSongScreen() }
                    handler.post(updateSongScreenRunnable)
                }

                updateHomeControlsVisibility(state)
            }

        })
    }

//    private fun updateHomeControlsVisibility(state: Int) {
//        binding.homeSongControls.visibility =
//            if (state == Player.STATE_READY) View.VISIBLE else View.GONE
//    }

    private fun updateHomeControlsVisibility(state: Int) {
        if (state == Player.STATE_READY) {
            binding.homeSongControls.visibility = View.VISIBLE
            binding.homePlayPauseSong.setOnClickListener { playOrPauseSong() }
            binding.homeNextSong.setOnClickListener { skipToNextSong() }
            binding.homePreviousSong.setOnClickListener { skipToPreviousSong() }
        } else {
            binding.homeSongControls.visibility = View.GONE
        }
    }


    private fun getSongProgress(duration: Long): String {
        val hrs = duration / (1000 * 60 * 60)
        val min = (duration % (1000 * 60 * 60)) / (1000 * 60)
        val sec = (duration % (1000 * 60)) / 1000

        return if (hrs < 1) {
            String.format("%02d:%02d", min, sec)
        } else {
            String.format("%02d:%02d:%02d", hrs, min, sec)
        }
    }

    private fun showPermissionExplanationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs storage permission to fetch all songs from the device.")
            .setCancelable(false)
            .setPositiveButton("Allow") { _, _ -> openPermissionSettings() }
            .setNegativeButton("Deny") { _, _ -> showPermissionDeniedMessage() }
            .create()
        dialog.show()
    }

    private fun openPermissionSettings() {
        storagePermissionLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchAllSongs()
            } else {
                showPermissionExplanationDialog()
            }
        }
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 101
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.search_menu, menu)
        val menuItem = menu?.findItem(R.id.searchBar)
        val searchView = menuItem?.actionView as androidx.appcompat.widget.SearchView
//        searchView.isIconified = false
//        searchView.requestFocus()

        searchSong(searchView)

        return super.onCreateOptionsMenu(menu)
    }

    private fun searchSong(searchView: androidx.appcompat.widget.SearchView) {
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText)
                return true
            }
        })
    }

    private fun filterSongs(query: String?) {
        val filteredList: MutableList<SongModel> = mutableListOf()

        if (allSongs.size > 0) {
            val lowerCaseQuery = query?.lowercase()

            for (song in allSongs) {
                val lowerCaseSongName = song.songName.lowercase()

                if (lowerCaseSongName.contains(lowerCaseQuery.orEmpty())) {
                    filteredList.add(song)
                }
            }

            songsAdapter.filterSongs(filteredList)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!player.isPlaying) {
            player.stop()
        }
        player.release()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}