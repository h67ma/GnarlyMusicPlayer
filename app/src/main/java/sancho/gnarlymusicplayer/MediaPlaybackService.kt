package sancho.gnarlymusicplayer

import android.app.Notification
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
	private var _track: Track? = null
	private var _initialized = false

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		when
		{
			intent.action == ACTION_START_PLAYBACK_SERVICE ->
			{
				_track = intent.getParcelableExtra(EXTRA_TRACK_PATH)

				val path = _track?.path
				val name = _track?.name

				if(path != null && name != null)
				{
					_player.setDataSource(path)
					_player.prepare()
					_player.start()

					if(!_initialized)
					{
						// first service call

						_notificationManager = NotificationManagerCompat.from(applicationContext)

						_player.isLooping = false

						_initialized = true

						val notification = createNotification(name, false)

						startForeground(NOTIFICATION_ID, notification)
					}
					else
					{
						// service already running

						val notification = createNotification(name, false)

						_notificationManager?.notify(NOTIFICATION_ID, notification)
					}
				}
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

	private fun createNotification(title: String, paused: Boolean): Notification
	{
		/*val notificationIntent = Intent(this, MainActivity::class.java)
					notificationIntent.action = ACTION_MAIN
					notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
					val pendingIntent = PendingIntent.getActivity(
						this, 0, notificationIntent, 0
					)*/
		val pcontentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)

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

		return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setContentTitle(title)
			.setContentText(title)
			.setSmallIcon(R.drawable.play)
			.setContentIntent(pcontentIntent).setOngoing(true)
			.addAction(R.drawable.prev, "Previous", ppreviousIntent)
			.addAction(if (paused) R.drawable.play else R.drawable.pause, "Play/pause", pplayIntent)
			.addAction(R.drawable.next, "Next", pnextIntent)
			.addAction(R.drawable.close, "Close", pcloseIntent).setStyle(
				androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(1, 2, 3)
				// TODO .setMediaSession(mediaSession.getSessionToken())
			).build()
	}

	override fun onBind(intent: Intent): IBinder?
	{
		return null
	}
}
