package sancho.gnarlymusicplayer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.media.MediaPlayer
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat

class MediaPlaybackService : Service()
{
	private val _player: MediaPlayer = MediaPlayer()
	private var _notificationManager: NotificationManagerCompat? = null
	private lateinit var _track: Track
	private var _initialized = false
	private lateinit var _notification: NotificationCompat.Builder
	private lateinit var _remoteView: RemoteViews

	override fun onCreate()
	{
		super.onCreate()
		_notificationManager = NotificationManagerCompat.from(applicationContext)

		prepareNotification()
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when
		{
			intent.action == ACTION_START_PLAYBACK_SERVICE ->
			{
				_track = intent.getParcelableExtra(EXTRA_TRACK)

				if(!_initialized)
				{
					// first service call

					_player.isLooping = false

					_player.setDataSource(_track.path)
					_player.prepare()
					_player.start()

					_initialized = true

					updateNotification()
					startForeground(NOTIFICATION_ID, _notification.build())
				}
				else
				{
					// service already running

					_player.reset()
					_player.setDataSource(_track.path)
					_player.prepare()
					_player.start()

					updateNotification()
					_notificationManager?.notify(NOTIFICATION_ID, _notification.build())
				}
			}
			intent.action == ACTION_REPLAY_TRACK ->
			{
				// seekTo(0) doesn't actually return to start of track :()
				_player.reset()
				_player.setDataSource(_track.path)
				_player.prepare()
				_player.start()

				updateNotification()
				_notificationManager?.notify(NOTIFICATION_ID, _notification.build())
			}
			intent.action == ACTION_PREV_TRACK ->
			{
				// TODO
			}
			intent.action == ACTION_PLAYPAUSE ->
			{
				if(_player.isPlaying)
					_player.pause()
				else
					_player.start()

				updateNotification()
				_notificationManager?.notify(NOTIFICATION_ID, _notification.build())
			}
			intent.action == ACTION_NEXT_TRACK ->
			{
				// TODO
			}
			intent.action == ACTION_STOP_PLAYBACK_SERVICE ->
			{
				_player.stop()
				_player.release()

				stopForeground(true)
				stopSelf()
			}
		}

		return START_STICKY
	}

	private fun prepareNotification()
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

		_remoteView = RemoteViews(packageName, R.layout.notification)
		_remoteView.setOnClickPendingIntent(R.id.action_reset_btn, preplayIntent)
		_remoteView.setOnClickPendingIntent(R.id.action_prev_btn, ppreviousIntent)
		_remoteView.setOnClickPendingIntent(R.id.action_playpause_btn, pplayIntent)
		_remoteView.setOnClickPendingIntent(R.id.action_next_btn, pnextIntent)
		_remoteView.setOnClickPendingIntent(R.id.action_close_btn, pcloseIntent)

		_notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(R.drawable.play)
			.setContentIntent(pcontentIntent)
			.setOngoing(true)
			.setCustomContentView(_remoteView)
	}

	private fun updateNotification()
	{
		_remoteView.setTextViewText(R.id.track_title, _track.name)
		_remoteView.setImageViewResource(R.id.action_playpause_btn, if (_player.isPlaying) R.drawable.pause else R.drawable.play)
	}

	override fun onBind(intent: Intent): IBinder?
	{
		return null
	}
}
