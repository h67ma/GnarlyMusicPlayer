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
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sancho.gnarlymusicplayer.*
import sancho.gnarlymusicplayer.models.Track
import java.io.IOException

private const val MIN_TRACK_TIME_S_TO_SAVE = 30
const val ACTION_START_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.startplayback"
const val ACTION_STOP_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.stopplayback"
const val ACTION_REPLAY_TRACK = "sancho.gnarlymusicplayer.action.replaytrack"
const val ACTION_PREV_TRACK = "sancho.gnarlymusicplayer.action.prevtrack"
const val ACTION_PLAYPAUSE = "sancho.gnarlymusicplayer.action.playpause"
const val ACTION_NEXT_TRACK = "sancho.gnarlymusicplayer.action.nexttrack"
const val ACTION_UPDATE_MAX_VOLUME = "sancho.gnarlymusicplayer.action.updatemaxvolume"

class MediaPlaybackService : Service()
{
	companion object
	{
		var mediaPlaybackServiceStarted: Boolean = false
		var serviceBound: Boolean = false

		// needs to be global because is used in service and in settings activity
		// when session doesn't exist set it to error value
		var audioSessionId: Int = AudioManager.ERROR
	}

	private lateinit var _notificationMaker: MediaNotificationMaker

	private lateinit var _player: AudioPlayer

	private val _track: Track = Track()

	private lateinit var _mediaSession: MediaSessionCompat
	private lateinit var _sessionCallback: MediaSessionCompat.Callback
	private lateinit var _audioManager: AudioManager
	private lateinit var _mediaSessionManager: MediaSessionManager
	private val _intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

	private var _setTrackJob: Job? = null

	private val _noisyAudioReceiver = object : BroadcastReceiver()
	{
		override fun onReceive(context: Context?, intent: Intent?)
		{
			if (!AppSettingsManager.ignoreAf)
				_sessionCallback.onPause()
		}
	}

	private var _receiverRegistered = false

	private val _focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).run {
		val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
			when (focusChange) {
				AudioManager.AUDIOFOCUS_LOSS -> {
					if (!AppSettingsManager.ignoreAf)
						_sessionCallback.onPause()
				}
				AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
					if (!AppSettingsManager.ignoreAf)
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

		setOnAudioFocusChangeListener(afChangeListener)
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
		var listeners: BoundServiceListeners? = null
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
			ACTION_START_PLAYBACK_SERVICE -> start()
			ACTION_REPLAY_TRACK -> replay()
			ACTION_PREV_TRACK -> _sessionCallback.onSkipToPrevious()
			ACTION_PLAYPAUSE -> playPause()
			ACTION_NEXT_TRACK -> _sessionCallback.onSkipToNext()
			ACTION_STOP_PLAYBACK_SERVICE ->	_sessionCallback.onStop()
			ACTION_UPDATE_MAX_VOLUME -> _player.setVolumeProvider()
		}

		return START_STICKY
	}

	private fun start()
	{
		if(!mediaPlaybackServiceStarted)
		{
			// first service call

			// set media volume
			if (AppSettingsManager.volumeSystemSet)
			{
				val max = _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
				if (AppSettingsManager.volumeSystemLevel > max) AppSettingsManager.volumeSystemLevel = max

				_audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, AppSettingsManager.volumeSystemLevel, 0)
			}

			if (AppSettingsManager.volumeInappEnabled)
				_player.setVolume(AppSettingsManager.volumeStepIdx)

			startForeground(NOTIFICATION_ID, _notificationMaker.makeNotification(true, _track)) // on start show playing icon

			mediaPlaybackServiceStarted = true

			_setTrackJob = GlobalScope.launch(Dispatchers.IO) {
				setTrackJob()

				_sessionCallback.onPlay()
				_notificationMaker.updateNotification(_player.isPlaying, _track)
			}
		}
		else
		{
			// service already running
			playAndUpdateNotification()
		}
	}

	private fun replay()
	{
		_player.seekTo(0)
		if (!_player.isPlaying)
			_sessionCallback.onPlay()
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

				if (AppSettingsManager.ignoreAf || result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					_player.start()
					_notificationMaker.updateNotification(_player.isPlaying)
					playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
					_mediaSession.setPlaybackState(playbackStateBuilder.build())
					registerNoisyReceiver()
				}
			}

			override fun onPause()
			{
				_player.pause()
				_notificationMaker.updateNotification(_player.isPlaying)
				playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
				_mediaSession.setPlaybackState(playbackStateBuilder.build())
				AudioManagerCompat.abandonAudioFocusRequest(_audioManager, _focusRequest)
				unregisterNoisyReceiver()
			}

			override fun onStop()
			{
				end(true)
			}
		}

		_mediaSession.setCallback(_sessionCallback)

		_mediaSession.isActive = true

		_mediaSession.setPlaybackState(playbackStateBuilder.build())
	}

	private fun nextTrack(trackFinished: Boolean)
	{
		if (!trackFinished)
			saveTrackPosition()

		val oldPos = PlaybackQueue.currentIdx

		if (trackFinished && PlaybackQueue.autoClean) // autoremove if playback ended "naturally" (no skip)
		{
			if (PlaybackQueue.removeCurrent()) // remove takes care of idx change in case track was at the bottom of queue
			{
				end(false)
				if(serviceBound)
					_binder.listeners?.onTrackChanged(oldPos, trackFinished)
				return
			}
		}
		else
			PlaybackQueue.setNextTrackIdx() // just calculate next track index

		if(serviceBound)
			_binder.listeners?.onTrackChanged(oldPos, trackFinished)

		setTrack(trackFinished)
	}

	private fun prevTrack()
	{
		saveTrackPosition()

		val oldPos = PlaybackQueue.currentIdx

		PlaybackQueue.setPrevTrackIdx()

		if(serviceBound)
			_binder.listeners?.onTrackChanged(oldPos, false)

		setTrack(false)
	}

	private fun setTrackJob()
	{
		var error = false
		try
		{
			if (PlaybackQueue.trackSelected())
			{
				TagExtractor.setTrackMeta(_track)
				_player.reset()
				_player.setDataSource(_track.path)
				_player.prepare()
			}
			else
				error = true
		}
		catch(_: IOException)
		{
			error = true
		}
		catch (_: IllegalArgumentException)
		{
			error = true
		}
		catch (_: IllegalStateException)
		{
			error = true
		}

		if (error) // this is so bad
		{
			GlobalScope.launch(Dispatchers.Main) {
				Toaster.show(applicationContext, getString(R.string.cant_play_track))
			}
		}
	}

	fun setTrack(forcePlay: Boolean)
	{
		if (_setTrackJob?.isActive == true)
			return // better let it finish

		val wasPlaying = _player.isPlaying

		_setTrackJob = GlobalScope.launch(Dispatchers.IO) {
			setTrackJob()
			if (forcePlay || wasPlaying) _sessionCallback.onPlay()
			_notificationMaker.updateNotification(_player.isPlaying, _track)
		}
	}

	private fun playAndUpdateNotification()
	{
		_sessionCallback.onPlay()
		_notificationMaker.updateNotification(_player.isPlaying)
	}

	fun playPause()
	{
		if (_setTrackJob?.isActive == true)
			return // mission failed, we'll get em next time

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
		if (currTime < MIN_TRACK_TIME_S_TO_SAVE || getTotalTime() - currTime < MIN_TRACK_TIME_S_TO_SAVE) return
		AppSettingsManager.savedTrackPath = _track.path
		AppSettingsManager.savedTrackTime = currTime
	}

	private fun registerNoisyReceiver()
	{
		if (!_receiverRegistered)
		{
			registerReceiver(_noisyAudioReceiver, _intentFilter)
			_receiverRegistered = true
		}
	}

	private fun unregisterNoisyReceiver()
	{
		if (_receiverRegistered)
		{
			unregisterReceiver(_noisyAudioReceiver)
			_receiverRegistered = false
		}
	}

	// called by MainActivity when all tracks get removed
	// also called when notification is closed
	fun end(saveTrack: Boolean)
	{
		AudioManagerCompat.abandonAudioFocusRequest(_audioManager, _focusRequest)
		unregisterNoisyReceiver()
		_mediaSession.isActive = false

		if(serviceBound)
			_binder.listeners?.onEnd()

		saveTrackPosition()

		_mediaSession.release()

		AppSettingsManager.saveToPrefs(this, saveTrack)

		if (audioSessionId != AudioManager.ERROR)
		{
			// send broadcast to equalizer thing to close audio session
			val eqIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
			eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
			eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
			eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			sendBroadcast(eqIntent)
		}

		audioSessionId = AudioManager.ERROR
		_player.reset()
		_player.release()

		mediaPlaybackServiceStarted = false
		stopForeground(true)
		stopSelf()
	}
}
