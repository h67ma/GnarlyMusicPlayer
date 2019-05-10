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
	private lateinit var _track: Track
	private var _initialized = false

	override fun onCreate()
	{
		super.onCreate()
		_notificationManager = NotificationManagerCompat.from(applicationContext)
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

					val notification = createNotification()

					startForeground(NOTIFICATION_ID, notification)
				}
				else
				{
					// service already running

					_player.reset()
					_player.setDataSource(_track.path)
					_player.prepare()
					_player.start()

					val notification = createNotification()

					_notificationManager?.notify(NOTIFICATION_ID, notification)
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

				val notification = createNotification()

				_notificationManager?.notify(NOTIFICATION_ID, notification)
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

	private fun createNotification(): Notification
	{
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
			.setContentTitle(_track.name)
			//.setContentText(title)
			.setSmallIcon(R.drawable.play)
			.setContentIntent(pcontentIntent).setOngoing(true)
			.addAction(R.drawable.prev, "Previous", ppreviousIntent)
			.addAction(if (_player.isPlaying) R.drawable.pause else R.drawable.play, "Play/pause", pplayIntent)
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
