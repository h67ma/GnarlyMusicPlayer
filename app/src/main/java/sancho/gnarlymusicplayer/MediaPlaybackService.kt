package sancho.gnarlymusicplayer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.preference.PreferenceManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import sancho.gnarlymusicplayer.models.Track
import java.io.File
import java.io.IOException

class MediaPlaybackService : Service()
{
	private lateinit var _player: MediaPlayer

	private lateinit var _notification: NotificationCompat.Builder
	private lateinit var _remoteViewSmall: RemoteViews
	private lateinit var _remoteViewBig: RemoteViews

	private val _track: Track
		get() = if (App.currentTrack < App.queue.size) App.queue[App.currentTrack] else Track("error", "error")

	private lateinit var _mediaSession: MediaSessionCompat
	private lateinit var _sessionCallback: MediaSessionCompat.Callback
	private lateinit var _audioManager: AudioManager
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

		prepareNotifications()

		prepareMediaSession()
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when (intent.action)
		{
			App.ACTION_START_PLAYBACK_SERVICE ->
			{
				if(!App.mediaPlaybackServiceStarted)
				{
					// first service call

					_player = MediaPlayer()
					App.audioSessionId = _player.audioSessionId

					if (App.audioSessionId != AudioManager.ERROR)
					{
						// send broadcast to equalizer thing so audio effects can are applied
						val eqIntent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
						eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, App.audioSessionId)
						eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, applicationContext.packageName)
						eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
						sendBroadcast(eqIntent)
					}

					_player.isLooping = false
					_player.setOnCompletionListener { nextTrack(true) }
					_player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

					try
					{
						_player.setDataSource(_track.path)
						_player.prepare()
						_sessionCallback.onPlay()
					}
					catch (_: IOException)
					{
						Toast.makeText(applicationContext, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
					}

					startForeground(App.NOTIFICATION_ID, makeNotification())

					App.mediaPlaybackServiceStarted = true
				}
				else
				{
					// service already running
					playAndUpdateNotification()
				}
			}
			App.ACTION_REPLAY_TRACK ->
			{
				// seekTo(0) doesn't actually return to start of track :()
				setTrack(true)
			}
			App.ACTION_PREV_TRACK ->
			{
				_sessionCallback.onSkipToPrevious()
			}
			App.ACTION_PLAYPAUSE ->
			{
				playPause()
			}
			App.ACTION_NEXT_TRACK ->
			{
				_sessionCallback.onSkipToNext()
			}
			App.ACTION_STOP_PLAYBACK_SERVICE ->
			{
				_sessionCallback.onStop()
			}
		}

		return START_STICKY
	}

	private fun prepareNotifications()
	{
		val pcontentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)

		val replayIntent = Intent(this, MediaPlaybackService::class.java)
		replayIntent.action = App.ACTION_REPLAY_TRACK
		val preplayIntent = PendingIntent.getService(this, 0, replayIntent, 0)

		val previousIntent = Intent(this, MediaPlaybackService::class.java)
		previousIntent.action = App.ACTION_PREV_TRACK
		val ppreviousIntent = PendingIntent.getService(this, 0, previousIntent, 0)

		val playIntent = Intent(this, MediaPlaybackService::class.java)
		playIntent.action = App.ACTION_PLAYPAUSE
		val pplayIntent = PendingIntent.getService(this, 0, playIntent, 0)

		val nextIntent = Intent(this, MediaPlaybackService::class.java)
		nextIntent.action = App.ACTION_NEXT_TRACK
		val pnextIntent = PendingIntent.getService(this, 0, nextIntent, 0)

		val closeIntent = Intent(this, MediaPlaybackService::class.java)
		closeIntent.action = App.ACTION_STOP_PLAYBACK_SERVICE
		val pcloseIntent = PendingIntent.getService(this, 0, closeIntent, 0)

		_remoteViewSmall = RemoteViews(packageName, R.layout.notification_small)
		_remoteViewSmall.setOnClickPendingIntent(R.id.action_playpause_btn, pplayIntent)
		_remoteViewSmall.setOnClickPendingIntent(R.id.action_next_btn, pnextIntent)
		_remoteViewSmall.setOnClickPendingIntent(R.id.action_close_btn, pcloseIntent)

		_remoteViewBig = RemoteViews(packageName, R.layout.notification_big)
		_remoteViewBig.setOnClickPendingIntent(R.id.action_reset_btn, preplayIntent)
		_remoteViewBig.setOnClickPendingIntent(R.id.action_prev_btn, ppreviousIntent)
		_remoteViewBig.setOnClickPendingIntent(R.id.action_playpause_btn, pplayIntent)
		_remoteViewBig.setOnClickPendingIntent(R.id.action_next_btn, pnextIntent)
		_remoteViewBig.setOnClickPendingIntent(R.id.action_close_btn, pcloseIntent)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			createNotificationChannel()

		_notification = NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
			.setContentIntent(pcontentIntent)
			.setOngoing(true)
			.setCustomContentView(_remoteViewSmall)
			.setCustomBigContentView(_remoteViewBig)
	}

	private fun makeNotification(): Notification
	{
		_remoteViewSmall.setTextViewText(R.id.track_title, _track.name)
		_remoteViewBig.setTextViewText(R.id.track_title, _track.name)
		if (_player.isPlaying)
		{
			_remoteViewSmall.setImageViewResource(R.id.action_playpause_btn, R.drawable.pause)
			_remoteViewBig.setImageViewResource(R.id.action_playpause_btn, R.drawable.pause)
			_notification.setSmallIcon(R.drawable.play)
		}
		else
		{
			_remoteViewSmall.setImageViewResource(R.id.action_playpause_btn, R.drawable.play)
			_remoteViewBig.setImageViewResource(R.id.action_playpause_btn, R.drawable.play)
			_notification.setSmallIcon(R.drawable.pause)
		}

		return _notification.build()
	}

	private fun updateNotification()
	{
		with(NotificationManagerCompat.from(applicationContext)) {
			notify(App.NOTIFICATION_ID, makeNotification())
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
						PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) // or??? are you fckn kidding me kotlin???????
			.setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)

		_mediaSession = MediaSessionCompat(applicationContext, "shirley")

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

					playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
					_mediaSession.setPlaybackState(playbackStateBuilder.build())
					registerReceiver(_noisyAudioReceiver, _intentFilter)
					_receiverRegistered = true
				}

				// set lockscreen cover art

				// first try embedded artwork
				val mmr = MediaMetadataRetriever()
				mmr.setDataSource(_track.path)

				val embeddedPic = mmr.embeddedPicture

				if(embeddedPic != null)
				{
					val bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size)
					setArtwork(bitmap)
				}
				else
				{
					// fallback to album art in track's dir

					val dir = File(_track.path).parent

					var foundCover = false
					for (filename in App.ALBUM_ART_FILENAMES)
					{
						val art = File(dir, filename)

						if (art.exists())
						{
							val bitmap = BitmapFactory.decodeFile(art.absolutePath, BitmapFactory.Options())
							setArtwork(bitmap)
							foundCover = true
							break
						}
					}

					// remove art if no cover found
					if (!foundCover)
						setArtwork(null)
				}
			}

			override fun onPause()
			{
				_player.pause()
				updateNotification()
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

	private fun setArtwork(art: Bitmap?)
	{
		val metadata = MediaMetadataCompat.Builder()
			.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
			.build()

		_mediaSession.setMetadata(metadata)
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
			val wasPlaying = _player.isPlaying
			_player.reset()
			_player.setDataSource(_track.path)
			_player.prepare()
			if (forcePlay || wasPlaying) _sessionCallback.onPlay()
			updateNotification()
		}
		catch(_: IOException)
		{
			Toast.makeText(applicationContext, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
		}
	}

	private fun playAndUpdateNotification()
	{
		_sessionCallback.onPlay()
		updateNotification()
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

	fun end(saveTrack: Boolean)
	{
		if(_binder.isBinderAlive)
			_binder.listeners.onEnd()

		saveTrackPosition()

		if (saveTrack)
		{
			// SAVE MEEEEEEE (can't wake up)
			with(PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()) {
				putInt(App.PREFERENCE_CURRENTTRACK, App.currentTrack)
				putString(App.PREFERENCE_SAVEDTRACK_PATH, App.savedTrackPath)
				putInt(App.PREFERENCE_SAVEDTRACK_TIME, App.savedTrackTime)
				apply()
			}
		}

		if (App.audioSessionId != AudioManager.ERROR)
		{
			// send broadcast to equalizer thing to close audio session
			val eqIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
			eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, App.audioSessionId)
			eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, applicationContext.packageName)
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

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel()
	{
		val chan = NotificationChannel(App.NOTIFICATION_CHANNEL_ID, "Media notification", NotificationManager.IMPORTANCE_LOW)
		chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		service.createNotificationChannel(chan)
	}
}
