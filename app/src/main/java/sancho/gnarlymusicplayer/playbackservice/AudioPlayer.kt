package sancho.gnarlymusicplayer.playbackservice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.VolumeProviderCompat
import sancho.gnarlymusicplayer.App
import sancho.gnarlymusicplayer.AppSettingsManager
import kotlin.math.log2

class AudioPlayer(context: Context, private val _mediaSession: MediaSessionCompat, completionCallback: (Boolean) -> Unit): MediaPlayer()
{
	private var _volumeDivider: Float = 1f

	init
	{
		App.audioSessionId = audioSessionId

		if (audioSessionId != AudioManager.ERROR)
		{
			// send broadcast to equalizer thing so audio effects can are applied
			val eqIntent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
			eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
			eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
			eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			context.sendBroadcast(eqIntent)
		}

		isLooping = false
		setOnCompletionListener { completionCallback(true) }
		setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
	}

	private fun setVolumeDivider()
	{
		_volumeDivider = log2((AppSettingsManager.volumeStepsTotal + 1).toFloat())
	}

	fun setVolume(stepIdx: Int)
	{
		// can't just divide step/max - setVolume input needs to be logarithmically scaled
		// at low levels grows slowly, at high levels grows rapidly
		// something like 0, 0.05, 0.11, 0.18, 0.27, 0.37, 0.5, 0.68, 1 for 8 volume levels
		val vol = 1 - log2((AppSettingsManager.volumeStepsTotal - stepIdx + 1).toFloat()) / _volumeDivider
		setVolume(vol, vol)
	}

	private fun setMaxVolume()
	{
		setVolume(1f, 1f)
	}

	fun setVolumeProvider()
	{
		if (AppSettingsManager.volumeInappEnabled)
		{
			// don't adjust system volume - change inside-app player volume instead

			_mediaSession.setPlaybackToRemote(object : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, AppSettingsManager.volumeStepsTotal, AppSettingsManager.volumeStepIdx)
			{
				// volume btns presses
				override fun onAdjustVolume(direction: Int)
				{
					// documentation doesn't say anything about "direction", but it's 1/-1 on my phone, so I guess I'll roll with that -_-
					AppSettingsManager.volumeStepIdx += direction
					setVolume(AppSettingsManager.volumeStepIdx)
					currentVolume = AppSettingsManager.volumeStepIdx // update internal VolumeProviderCompat state
				}

				// volume slider drag/mute/unmute
				override fun onSetVolumeTo(volume: Int)
				{
					AppSettingsManager.volumeStepIdx = volume
					setVolume(AppSettingsManager.volumeStepIdx)
					currentVolume = AppSettingsManager.volumeStepIdx // update internal VolumeProviderCompat state
				}
			})

			setVolumeDivider()

			if (App.mediaPlaybackServiceStarted)
				setVolume(AppSettingsManager.volumeStepIdx) // player already initialized
		}
		else
		{
			// normal volume control
			_mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC) // removes remote volume thing

			if (App.mediaPlaybackServiceStarted)
				setMaxVolume() // player already initialized
		}
	}
}
