package sancho.gnarlymusicplayer.playbackservice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.os.PowerManager
import sancho.gnarlymusicplayer.App
import kotlin.math.log2

class AudioPlayer(context: Context, completionCallback: (Boolean) -> Unit ): MediaPlayer()
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

	fun setVolumeDivider()
	{
		_volumeDivider = log2((App.volumeStepsTotal + 1).toFloat())
	}

	fun setVolume(stepIdx: Int)
	{
		// can't just divide step/max - setVolume input needs to be logarithmically scaled
		// at low levels grows slowly, at high levels grows rapidly
		// something like 0, 0.05, 0.11, 0.18, 0.27, 0.37, 0.5, 0.68, 1 for 8 volume levels
		val vol = 1 - log2((App.volumeStepsTotal - stepIdx + 1).toFloat()) / _volumeDivider
		setVolume(vol, vol)
	}

	fun setMaxVolume()
	{
		setVolume(1f, 1f)
	}
}
