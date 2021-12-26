package sancho.gnarlymusicplayer.playbackservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.activities.MainActivity
import sancho.gnarlymusicplayer.models.Track

const val NOTIFICATION_ID = 420 // what else did you expect?
private const val NOTIFICATION_CHANNEL_ID = "sancho.gnarlymusicplayer.notificationthing"

class MediaNotificationMaker(private val _context: Context, private val _session: MediaSessionCompat)
{
	private val _builder: NotificationCompat.Builder

	private val _replayIntent: PendingIntent
	private val _prevIntent: PendingIntent
	private val _playPauseIntent: PendingIntent
	private val _nextIntent: PendingIntent
	private val _closeIntent: PendingIntent

	init
	{
		val pcontentIntent = PendingIntent.getActivity(_context, 0, Intent(_context, MainActivity::class.java), 0)

		_replayIntent = makePendingIntent(ACTION_REPLAY_TRACK)
		_prevIntent = makePendingIntent(ACTION_PREV_TRACK)
		_playPauseIntent = makePendingIntent(ACTION_PLAYPAUSE)
		_nextIntent = makePendingIntent(ACTION_NEXT_TRACK)
		_closeIntent = makePendingIntent(ACTION_STOP_PLAYBACK_SERVICE)

		val style = object : androidx.media.app.NotificationCompat.MediaStyle() {}
		style.setMediaSession(_session.sessionToken)
			.setShowActionsInCompactView(2, 3, 4)
			.setCancelButtonIntent(_closeIntent)

		_builder = NotificationCompat.Builder(_context, NOTIFICATION_CHANNEL_ID)
			.setContentIntent(pcontentIntent)
			.setOngoing(true)
			.setStyle(style)
			.setShowWhen(false)

		setActions(R.drawable.pause)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			createNotificationChannel()
	}

	fun makeNotification(playing: Boolean, track: Track): Notification
	{
		updateNotificationStatus(playing)

		_builder.setContentTitle(track.title)
			.setContentText(track.artist)
			.setSubText(track.year) // aka header (next to app name)
			.setLargeIcon(track.cover) // won't hurt if it's null

		// also update cover in media session (for lockscreen)
		_session.setMetadata(MediaMetadataCompat.Builder()
			.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.cover) // won't hurt if it's null
			.build())

		return _builder.build()
	}

	fun updateNotification(playing: Boolean)
	{
		updateNotificationStatus(playing)

		with(NotificationManagerCompat.from(_context))
		{
			notify(NOTIFICATION_ID, _builder.build())
		}
	}

	fun updateNotification(playing: Boolean, track: Track)
	{
		with(NotificationManagerCompat.from(_context))
		{
			notify(NOTIFICATION_ID, makeNotification(playing, track))
		}
	}

	private fun makePendingIntent(action: String): PendingIntent
	{
		val intent = Intent(_context, MediaPlaybackService::class.java)
		intent.action = action
		return PendingIntent.getService(_context, 0, intent, 0)
	}

	private fun setActions(playPauseResource: Int)
	{
		_builder
			.clearActions()
			.addAction(R.drawable.replay, _context.getString(R.string.reset), _replayIntent)
			.addAction(R.drawable.prev, _context.getString(R.string.previous), _prevIntent)
			.addAction(playPauseResource, _context.getString(R.string.play_pause), _playPauseIntent)
			.addAction(R.drawable.next, _context.getString(R.string.next), _nextIntent)
			.addAction(R.drawable.close, _context.getString(R.string.close), _closeIntent)
	}

	private fun updateNotificationStatus(playing: Boolean)
	{
		setActions(if (playing) R.drawable.pause else R.drawable.play)
		_builder.setSmallIcon(if (playing) R.drawable.play else R.drawable.pause)
		// .setOngoing(playing) // doesn't work as notification was started with startForeground, would have to stop service to make it dismissible
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel()
	{
		val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, _context.getString(R.string.media_notification), NotificationManager.IMPORTANCE_LOW)
		chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		val service = _context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		service.createNotificationChannel(chan)
	}
}
