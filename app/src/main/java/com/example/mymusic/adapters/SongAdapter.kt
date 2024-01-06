package com.example.mymusic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.mymusic.R
import com.example.mymusic.databinding.SongItemBinding
import com.example.mymusic.models.SongModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import java.text.DecimalFormat

class SongAdapter(
    private var songs: List<SongModel>,
    private var player: ExoPlayer,
    private val songScreen: ConstraintLayout,
    private val onItemClick: (SongModel) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongsViewHolder>() {

    class SongsViewHolder(private val binding: SongItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: SongModel) {
            binding.apply {
                songName.text = song.songName
                songDuration.text = getSongDuration(song.songDuration)
                songSize.text = getSongSize(song.songSize)

                val image = song.songImage

                if (image != null) {
                    songImage.setImageURI(image)

                    if (songImage.drawable == null) {
                        songImage.setImageResource(R.drawable.music_note)
                    }
                }
//                root.setOnClickListener {
//                    Toast.makeText(root.context, song.songName, Toast.LENGTH_SHORT).show()
//                }
            }
        }

        private fun getSongSize(bytes: Int): String {
            val songSize: String

            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            val tb = gb / 1024.0

            val dec = DecimalFormat("0.00")

            songSize = if (tb >= 1) {
                dec.format(tb) + " TB"
            } else if (gb >= 1) {
                dec.format(gb) + " GB"
            } else if (mb >= 1) {
                dec.format(mb) + " MB"
            } else if (kb >= 1) {
                dec.format(kb) + " KB"
            } else {
                dec.format(bytes) + " B"
            }

            return songSize
        }

        private fun getSongDuration(totalDuration: Int): String {
            val songDuration: String

            val hrs = totalDuration / (1000 * 60 * 60)
            val min = (totalDuration % (1000 * 60 * 60)) / (1000 * 60)
            val sec = (totalDuration % (1000 * 60)) / 1000

            songDuration = if (hrs < 1) {
                String.format("%02d:%02d", min, sec)
            } else {
                String.format("%1d:%02d:%02d", hrs, min, sec)
            }

            return songDuration
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongsViewHolder {
        val binding = SongItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongsViewHolder(binding)
    }

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: SongsViewHolder, position: Int) {
        holder.bind(songs[position])
        val song = songs[position]

        holder.itemView.setOnClickListener {
            onItemClick(song)

            if (!player.isPlaying) {
                player.setMediaItems(getMediaItems(), position, 0)
            }
            else {
                player.pause()
                player.seekTo(position, 0)
            }

            player.prepare()
            player.play()
            Toast.makeText(holder.itemView.context, song.songName, Toast.LENGTH_SHORT).show()
            songScreen.visibility = View.VISIBLE
        }
    }

    private fun getMediaItems(): MutableList<MediaItem> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        for (song in songs) {
            val mediaItem = MediaItem.Builder()
                .setUri(song.songUri)
                .setMediaMetadata(getMetaData(song))
                .build()

            mediaItems.add(mediaItem)
        }
        return mediaItems
    }

    private fun getMetaData(song: SongModel): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(song.songName)
            .setArtworkUri(song.songImage)
            .build()
    }

    fun filterSongs(filteredList: List<SongModel>) {
        songs = filteredList
        notifyDataSetChanged()
    }
}