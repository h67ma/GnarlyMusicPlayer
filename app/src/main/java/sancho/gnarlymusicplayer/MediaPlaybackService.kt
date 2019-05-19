package sancho.gnarlymusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.IOException

var mediaPlaybackServiceStarted = false

class MediaPlaybackService : Service()
{
	private lateinit var _player: MediaPlayer
	private lateinit var _notification: NotificationCompat.Builder
	private lateinit var _remoteViewSmall: RemoteViews
	private lateinit var _remoteViewBig: RemoteViews
	private val _binder = LocalBinder()
	private val _track: Track
		get() = if (currentTrack < queue.size) queue[currentTrack] else Track("error", "error")

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
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when
		{
			intent.action == ACTION_START_PLAYBACK_SERVICE ->
			{
				if(!mediaPlaybackServiceStarted)
				{
					// first service call

					_player = MediaPlayer()
					_player.isLooping = false

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

					mediaPlaybackServiceStarted = true
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
				val oldPos = currentTrack
				currentTrack--
				if (currentTrack < 0) currentTrack = queue.size - 1
				_binder.listeners.updateQueueRecycler(oldPos)
				playTrack(false)
			}
			intent.action == ACTION_PLAYPAUSE ->
			{
				playPause()
			}
			intent.action == ACTION_NEXT_TRACK ->
			{
				val oldPos = currentTrack
				currentTrack = (currentTrack + 1) % queue.size
				_binder.listeners.updateQueueRecycler(oldPos)
				playTrack(false)
			}
			intent.action == ACTION_STOP_PLAYBACK_SERVICE ->
			{
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
		_remoteViewSmall.setOnClickPendingIntent(R.id.action_reset_btn, preplayIntent)
		_remoteViewSmall.setOnClickPendingIntent(R.id.action_prev_btn, ppreviousIntent)
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
		_player.stop()
		_player.release()

		mediaPlaybackServiceStarted = false
		stopForeground(true)
		stopSelf()
	}
}
