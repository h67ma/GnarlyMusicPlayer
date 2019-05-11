package sancho.gnarlymusicplayer

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.media.MediaPlayer
import androidx.core.app.NotificationManagerCompat

class MediaPlaybackService : Service()
{
	private val _player: MediaPlayer = MediaPlayer()
	private var _notificationManager: NotificationManagerCompat? = null
	private lateinit var _track: Track
	private var _initialized = false
	private lateinit var _notification: NotificationCompat.Builder

	override fun onCreate()
	{
		super.onCreate()
		_notificationManager = NotificationManagerCompat.from(applicationContext)

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

		_notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(R.drawable.play)
			.setContentIntent(pcontentIntent)
			.setOngoing(true)
			.addAction(R.drawable.replay, "Replay", preplayIntent)
			.addAction(R.drawable.prev, "Previous", ppreviousIntent)
			.addAction(R.drawable.play, "Play/pause", pplayIntent)
			.addAction(R.drawable.next, "Next", pnextIntent)
			.addAction(R.drawable.close, "Close", pcloseIntent)
			.setStyle(
				androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(2, 3, 4)
				// TODO .setMediaSession(mediaSession.getSessionToken())
			)
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

	@SuppressLint("RestrictedApi") // is this ok?
	private fun updateNotification()
	{
		_notification.setContentTitle(_track.name)
		// _notification.setContentText(_track.path)
		_notification.mActions[2].icon = if (_player.isPlaying) R.drawable.pause else R.drawable.play
	}

	override fun onBind(intent: Intent): IBinder?
	{
		return null
	}
}
