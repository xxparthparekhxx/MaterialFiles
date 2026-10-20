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
            .build()
        exoPlayer.setMediaItem(MediaItem.fromUri(path.fileProviderUri.toString()))
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        binding.playerView.player = exoPlayer
        player = exoPlayer
    }

    override fun onStart() {
        super.onStart()

        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()

        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()

        player?.release()
        player = null
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs
}
