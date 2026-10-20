/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.databinding.MediaPlayerFragmentBinding
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.extraPath

class MediaPlayerFragment : Fragment() {
    private val args by args<Args>()

    private var player: ExoPlayer? = null

    // The position of the playback and whether it is playing, so that they survive a
    // configuration change.
    private var position = 0L
    private var playWhenReady = true

    private lateinit var binding: MediaPlayerFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        MediaPlayerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            position = savedInstanceState.getLong(STATE_POSITION)
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true)
        }

        val path: Path? = args.intent.extraPath
        if (path == null) {
            finish()
            return
        }

        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            title = path.fileName?.toString()
        }
        binding.appBarLayout.applySystemWindowInsetsToPadding(left = true, top = true, right = true)
        initializePlayer(path)
    }

    private fun initializePlayer(path: Path) {
        val context = requireContext().applicationContext
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                // The player asks for the audio focus, so that it does not play over another app.
                true
            )
            .build()
        exoPlayer.setMediaItem(MediaItem.fromUri(path.fileProviderUri.toString()))
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
        if (position > 0) {
            exoPlayer.seekTo(position)
        }
        binding.playerView.player = exoPlayer
        player = exoPlayer
    }

    override fun onStart() {
        super.onStart()

        // The playback is only continued when it was not paused by the user.
        player?.playWhenReady = playWhenReady
    }

    override fun onStop() {
        super.onStop()

        playWhenReady = player?.playWhenReady ?: playWhenReady
        player?.playWhenReady = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        player?.let {
            outState.putLong(STATE_POSITION, it.currentPosition)
            outState.putBoolean(STATE_PLAY_WHEN_READY, it.playWhenReady)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        player?.release()
        player = null
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    companion object {
        private const val STATE_POSITION = "position"
        private const val STATE_PLAY_WHEN_READY = "playWhenReady"
    }
}
