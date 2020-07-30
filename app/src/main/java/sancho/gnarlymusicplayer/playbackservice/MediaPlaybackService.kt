package sancho.gnarlymusicplayer.playbackservice

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.preference.PreferenceManager
import sancho.gnarlymusicplayer.App
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.models.Track
import sancho.gnarlymusicplayer.setTrackMeta
import java.io.IOException

class MediaPlaybackService : Service()
{
	private lateinit var _notificationMaker: MediaNotificationMaker

	private lateinit var _player: AudioPlayer

	private val _track: Track = Track()

	private lateinit var _mediaSession: MediaSessionCompat
	private lateinit var _sessionCallback: MediaSessionCompat.Callback
	private lateinit var _audioManager: AudioManager
	private lateinit var _mediaSessionManager: MediaSessionManager
	private val _intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

	private val _noisyAudioReceiver = object : BroadcastReceiver()
	{
		override fun onReceive(context: Context?, intent: Intent?)
		{
			_sessionCallback.onPause()
		}
	}

	private var _receiverRegistered = false

	private val _afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
		when (focusChange) {
			AudioManager.AUDIOFOCUS_LOSS -> {
				_sessionCallback.onPause()
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
				_sessionCallback.onPause()
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
				_audioManager.adjustVolume(AudioManager.ADJUST_LOWER, 0) // does this work?
			}
			AudioManager.AUDIOFOCUS_GAIN -> {
				_audioManager.adjustVolume(AudioManager.ADJUST_RAISE, 0) // does this work?
				playAndUpdateNotification()
			}
		}
	}

	private val _focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).run {
		setOnAudioFocusChangeListener(_afChangeListener)
		setAudioAttributes(AudioAttributesCompat.Builder().run {
			setUsage(AudioAttributesCompat.USAGE_MEDIA)
			setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
			build()
		})
		build()
	}

	private val _binder = LocalBinder()

	inner class LocalBinder : Binder()
	{
		lateinit var listeners: BoundServiceListeners
			private set

		fun getService(listeners: BoundServiceListeners): MediaPlaybackService
		{
			this.listeners = listeners
			return this@MediaPlaybackService
		}
	}

	override fun onBind(intent: Intent): IBinder?
	{
		return _binder
	}

	override fun onCreate()
	{
		super.onCreate()

		_audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
		_mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

		prepareMediaSession()

		_player = AudioPlayer(this, _mediaSession, ::nextTrack) // now this is a weird lookin operator

		_player.setVolumeProvider()

		_notificationMaker = MediaNotificationMaker(this, _mediaSession)
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when (intent.action)
		{
			App.ACTION_START_PLAYBACK_SERVICE -> start()
			App.ACTION_REPLAY_TRACK -> _player.seekTo(0)
			App.ACTION_PREV_TRACK -> _sessionCallback.onSkipToPrevious()
			App.ACTION_PLAYPAUSE -> playPause()
			App.ACTION_NEXT_TRACK -> _sessionCallback.onSkipToNext()
			App.ACTION_STOP_PLAYBACK_SERVICE ->	_sessionCallback.onStop()
			App.ACTION_UPDATE_MAX_VOLUME -> _player.setVolumeProvider()
		}

		return START_STICKY
	}

	private fun start()
	{
		if(!App.mediaPlaybackServiceStarted)
		{
			// first service call

			// set media volume
			if (App.volumeSystemSet)
			{
				val max = _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
				if (App.volumeSystemLevel > max) App.volumeSystemLevel = max

				_audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, App.volumeSystemLevel, 0)
			}

			try
			{
				setTrackMeta(App.queue[App.currentTrack], _track)
				_player.setDataSource(_track.path)
				_player.prepare()
				_sessionCallback.onPlay()
			}
			catch (_: IOException)
			{
				Toast.makeText(this, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
			}

			if (App.volumeInappEnabled)
				_player.setVolume(App.volumeStepIdx)

			startForeground(App.NOTIFICATION_ID, _notificationMaker.makeNotification(_player.isPlaying, _track))

			App.mediaPlaybackServiceStarted = true
		}
		else
		{
			// service already running
			playAndUpdateNotification()
		}
	}

	private fun prepareMediaSession()
	{
		val playbackStateBuilder = PlaybackStateCompat.Builder()
			.setActions(
				PlaybackStateCompat.ACTION_PLAY or
						PlaybackStateCompat.ACTION_PAUSE or
						PlaybackStateCompat.ACTION_STOP or
						PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
						PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
			.setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)

		_mediaSession = MediaSessionCompat(this, "shirley")

		_sessionCallback = object: MediaSessionCompat.Callback()
		{
			override fun onSkipToNext()
			{
				nextTrack(false)
			}

			override fun onSkipToPrevious()
			{
				prevTrack()
			}

			override fun onPlay()
			{
				val result = AudioManagerCompat.requestAudioFocus(_audioManager, _focusRequest)

				if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					_player.start()
					_notificationMaker.updateNotification(_player.isPlaying)
					playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
					_mediaSession.setPlaybackState(playbackStateBuilder.build())
					registerReceiver(_noisyAudioReceiver, _intentFilter)
					_receiverRegistered = true
				}
			}

			override fun onPause()
			{
				_player.pause()
				_notificationMaker.updateNotification(_player.isPlaying)
				playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
				_mediaSession.setPlaybackState(playbackStateBuilder.build())
				AudioManagerCompat.abandonAudioFocusRequest(_audioManager, _focusRequest)
				unregisterReceiver(_noisyAudioReceiver)
				_receiverRegistered = false
			}

			override fun onStop()
			{
				AudioManagerCompat.abandonAudioFocusRequest(_audioManager, _focusRequest)
				if (_receiverRegistered) unregisterReceiver(_noisyAudioReceiver)
				_mediaSession.isActive = false

				end(true)
			}
		}

		_mediaSession.setCallback(_sessionCallback)

		_mediaSession.isActive = true

		_mediaSession.setPlaybackState(playbackStateBuilder.build())
	}

	private fun nextTrack(trackFinished: Boolean)
	{
		if (!trackFinished) saveTrackPosition()

		val oldPos = App.currentTrack
		App.currentTrack = (App.currentTrack + 1) % App.queue.size

		if(_binder.isBinderAlive)
			_binder.listeners.onTrackChanged(oldPos)

		setTrack(trackFinished)
	}

	private fun prevTrack()
	{
		saveTrackPosition()

		val oldPos = App.currentTrack
		App.currentTrack--
		if (App.currentTrack < 0) App.currentTrack = App.queue.size - 1

		if(_binder.isBinderAlive)
			_binder.listeners.onTrackChanged(oldPos)

		setTrack(false)
	}

	fun setTrack(forcePlay: Boolean)
	{
		try
		{
			if (App.currentTrack < App.queue.size)
			{
				setTrackMeta(App.queue[App.currentTrack], _track)
				val wasPlaying = _player.isPlaying
				_player.reset()
				_player.setDataSource(_track.path)
				_player.prepare()
				if (forcePlay || wasPlaying) _sessionCallback.onPlay()

				_notificationMaker.updateNotification(_player.isPlaying, _track)
			}
			else
				Toast.makeText(this, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
		}
		catch(_: IOException)
		{
			Toast.makeText(this, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
		}
	}

	private fun playAndUpdateNotification()
	{
		_sessionCallback.onPlay()
		_notificationMaker.updateNotification(_player.isPlaying)
	}

	fun playPause()
	{
		if (_player.isPlaying)
			_sessionCallback.onPause()
		else
			playAndUpdateNotification()
	}

	fun seekAndPlay(seconds: Int)
	{
		_player.seekTo(seconds * 1000)
		if (!_player.isPlaying)
			playAndUpdateNotification()
	}

	fun getTotalTime() = _player.duration / 1000

	fun getCurrentTime() = _player.currentPosition / 1000

	fun saveTrackPosition()
	{
		val currTime = getCurrentTime()
		if (currTime < App.MIN_TRACK_TIME_S_TO_SAVE || getTotalTime() - currTime < App.MIN_TRACK_TIME_S_TO_SAVE) return
		App.savedTrackPath = _track.path
		App.savedTrackTime = currTime
	}

	// called by MainActivity when all tracks get removed
	// also called when notification is closed
	fun end(saveTrack: Boolean)
	{
		if(_binder.isBinderAlive)
			_binder.listeners.onEnd()

		saveTrackPosition()

		_mediaSession.release()

		// SAVE MEEEEEEE (can't wake up)
		val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
		if (saveTrack)
		{
			// not needed when all tracks have been cleared
			editor.putInt(App.PREFERENCE_CURRENTTRACK, App.currentTrack)
			editor.putString(App.PREFERENCE_SAVEDTRACK_PATH, App.savedTrackPath)
			editor.putInt(App.PREFERENCE_SAVEDTRACK_TIME, App.savedTrackTime)
		}

		// save last volume setting
		if (App.volumeInappEnabled)
			editor.putInt(App.PREFERENCE_VOLUME_STEP_IDX, App.volumeStepIdx)

		editor.apply()

		if (App.audioSessionId != AudioManager.ERROR)
		{
			// send broadcast to equalizer thing to close audio session
			val eqIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
			eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, App.audioSessionId)
			eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
			eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			sendBroadcast(eqIntent)
		}

		App.audioSessionId = AudioManager.ERROR
		_player.reset()
		_player.release()

		App.mediaPlaybackServiceStarted = false
		stopForeground(true)
		stopSelf()
	}
}
