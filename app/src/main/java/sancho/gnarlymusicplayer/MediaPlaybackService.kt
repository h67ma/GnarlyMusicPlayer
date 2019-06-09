package sancho.gnarlymusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.preference.PreferenceManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sancho.gnarlymusicplayer.App.Companion.ACTION_NEXT_TRACK
import sancho.gnarlymusicplayer.App.Companion.ACTION_PLAYPAUSE
import sancho.gnarlymusicplayer.App.Companion.ACTION_PREV_TRACK
import sancho.gnarlymusicplayer.App.Companion.ACTION_REPLAY_TRACK
import sancho.gnarlymusicplayer.App.Companion.ACTION_START_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.App.Companion.ACTION_STOP_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.App.Companion.NOTIFICATION_CHANNEL_ID
import sancho.gnarlymusicplayer.App.Companion.NOTIFICATION_ID
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_CURRENTTRACK
import sancho.gnarlymusicplayer.App.Companion.app_currentTrack
import sancho.gnarlymusicplayer.App.Companion.app_mediaPlaybackServiceStarted
import sancho.gnarlymusicplayer.App.Companion.app_queue
import java.io.IOException

class MediaPlaybackService : Service()
{
	private lateinit var _player: MediaPlayer
	private lateinit var _notification: NotificationCompat.Builder
	private lateinit var _remoteViewSmall: RemoteViews
	private lateinit var _remoteViewBig: RemoteViews
	private val _binder = LocalBinder()
	private val _track: Track
		get() = if (app_currentTrack < app_queue.size) app_queue[app_currentTrack] else Track("error", "error")

	private lateinit var _mediaSession: MediaSessionCompat
	private lateinit var _playbackStateBuilder: PlaybackStateCompat.Builder
	private lateinit var _sessionCallback: MediaSessionCompat.Callback

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

	override fun onCreate()
	{
		super.onCreate()

		prepareNotifications()

		_mediaSession = MediaSessionCompat(applicationContext, "shirley")

		_sessionCallback = object: MediaSessionCompat.Callback()
		{
			override fun onSkipToNext()
			{
				nextTrack(false)
				super.onSkipToNext()
			}

			override fun onSkipToPrevious()
			{
				prevTrack()
				super.onSkipToPrevious()
			}
		}
		_mediaSession.setCallback(_sessionCallback)

		_mediaSession.isActive = true

		_playbackStateBuilder = PlaybackStateCompat.Builder()
			.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) // or??? are you fckn kidding me???????
			.setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
		_mediaSession.setPlaybackState(_playbackStateBuilder.build())
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when
		{
			intent.action == ACTION_START_PLAYBACK_SERVICE ->
			{
				if(!app_mediaPlaybackServiceStarted)
				{
					// first service call

					_player = MediaPlayer()
					_player.isLooping = false
					_player.setOnCompletionListener { nextTrack(true) }
					_player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

					try
					{
						_player.setDataSource(_track.path)
						_player.prepare()
						_player.start()
					}
					catch (_: IOException)
					{
						Toast.makeText(applicationContext, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
					}

					startForeground(NOTIFICATION_ID, makeNotification())

					app_mediaPlaybackServiceStarted = true
				}
				else
				{
					// service already running
					playTrack(true)
				}
			}
			intent.action == ACTION_REPLAY_TRACK ->
			{
				// seekTo(0) doesn't actually return to start of track :()
				playTrack(true)
			}
			intent.action == ACTION_PREV_TRACK ->
			{
				_sessionCallback.onSkipToPrevious()
			}
			intent.action == ACTION_PLAYPAUSE ->
			{
				playPause()
			}
			intent.action == ACTION_NEXT_TRACK ->
			{
				_sessionCallback.onSkipToNext()
			}
			intent.action == ACTION_STOP_PLAYBACK_SERVICE ->
			{
				// save current track
				// SAVE MEEEEEEE (can't wake up)
				with(PreferenceManager.getDefaultSharedPreferences(applicationContext).edit())
				{
					putInt(PREFERENCE_CURRENTTRACK, app_currentTrack)
					apply()
				}

				end()
			}
		}

		return START_STICKY
	}

	private fun prepareNotifications()
	{
		val pcontentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)

		val replayIntent = Intent(this, MediaPlaybackService::class.java)
		replayIntent.action = ACTION_REPLAY_TRACK
		val preplayIntent = PendingIntent.getService(this, 0, replayIntent, 0)

		val previousIntent = Intent(this, MediaPlaybackService::class.java)
		previousIntent.action = ACTION_PREV_TRACK
		val ppreviousIntent = PendingIntent.getService(this, 0, previousIntent, 0)

		val playIntent = Intent(this, MediaPlaybackService::class.java)
		playIntent.action = ACTION_PLAYPAUSE
		val pplayIntent = PendingIntent.getService(this, 0, playIntent, 0)

		val nextIntent = Intent(this, MediaPlaybackService::class.java)
		nextIntent.action = ACTION_NEXT_TRACK
		val pnextIntent = PendingIntent.getService(this, 0, nextIntent, 0)

		val closeIntent = Intent(this, MediaPlaybackService::class.java)
		closeIntent.action = ACTION_STOP_PLAYBACK_SERVICE
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

		_notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
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

	override fun onBind(intent: Intent): IBinder?
	{
		return _binder
	}

	private fun prevTrack()
	{
		val oldPos = app_currentTrack
		app_currentTrack--
		if (app_currentTrack < 0) app_currentTrack = app_queue.size - 1

		if(_binder.isBinderAlive)
			_binder.listeners.updateQueueRecycler(oldPos)

		playTrack(false)
	}

	private fun nextTrack(forcePlay: Boolean)
	{
		val oldPos = app_currentTrack
		app_currentTrack = (app_currentTrack + 1) % app_queue.size

		if(_binder.isBinderAlive)
			_binder.listeners.updateQueueRecycler(oldPos)

		playTrack(forcePlay)
	}

	fun playTrack(forcePlay: Boolean)
	{
		try
		{
			val wasPlaying = _player.isPlaying
			_player.reset()
			_player.setDataSource(_track.path)
			_player.prepare()
			if (forcePlay || wasPlaying) _player.start()
		}
		catch(_: IOException)
		{
			Toast.makeText(applicationContext, getString(R.string.cant_play_track), Toast.LENGTH_SHORT).show()
		}

		with(NotificationManagerCompat.from(applicationContext)) {
			notify(NOTIFICATION_ID, makeNotification())
		}
	}

	fun playPause()
	{
		if (_player.isPlaying)
			_player.pause()
		else
			_player.start()

		with(NotificationManagerCompat.from(applicationContext)) {
			notify(NOTIFICATION_ID, makeNotification())
		}
	}

	fun end()
	{
		_player.reset()
		_player.release()

		app_mediaPlaybackServiceStarted = false
		stopForeground(true)
		stopSelf()
	}
}
