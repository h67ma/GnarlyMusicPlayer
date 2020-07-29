package sancho.gnarlymusicplayer

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
import sancho.gnarlymusicplayer.activities.MainActivity
import sancho.gnarlymusicplayer.models.Track

class NotificationMaker(private val _context: Context, private val _session: MediaSessionCompat)
{
	private val _builder: NotificationCompat.Builder
	private val _playPauseAction: NotificationCompat.Action

	init
	{
		val pcontentIntent = PendingIntent.getActivity(_context, 0, Intent(_context, MainActivity::class.java), 0)

		val replayIntent = makePendingIntent(App.ACTION_REPLAY_TRACK)
		val prevIntent = makePendingIntent(App.ACTION_PREV_TRACK)
		val playPauseIntent = makePendingIntent(App.ACTION_PLAYPAUSE)
		val nextIntent = makePendingIntent(App.ACTION_NEXT_TRACK)
		val closeIntent = makePendingIntent(App.ACTION_STOP_PLAYBACK_SERVICE)

		_playPauseAction = NotificationCompat.Action(R.drawable.pause, _context.getString(R.string.play_pause), playPauseIntent)

		val style = object : androidx.media.app.NotificationCompat.MediaStyle() {}
		style.setMediaSession(_session.sessionToken)
			.setShowActionsInCompactView(1, 2, 3)
			.setCancelButtonIntent(closeIntent)

		_builder = NotificationCompat.Builder(_context, App.NOTIFICATION_CHANNEL_ID)
			.setContentIntent(pcontentIntent)
			.setOngoing(true)
			.setStyle(style)
			.addAction(R.drawable.replay, _context.getString(R.string.reset), replayIntent)
			.addAction(R.drawable.prev, _context.getString(R.string.previous), prevIntent)
			.addAction(_playPauseAction)
			.addAction(R.drawable.next, _context.getString(R.string.next), nextIntent)
			.addAction(R.drawable.close, _context.getString(R.string.close), closeIntent)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			createNotificationChannel()
	}

	fun makeNotification(playing: Boolean, track: Track): Notification
	{
		updateNotificationStatus(playing)

		_builder.setContentTitle(track.title)
			.setContentText(track.artist)
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
			notify(App.NOTIFICATION_ID, _builder.build())
		}
	}

	fun updateNotification(playing: Boolean, track: Track)
	{
		with(NotificationManagerCompat.from(_context))
		{
			notify(App.NOTIFICATION_ID, makeNotification(playing, track))
		}
	}

	private fun makePendingIntent(action: String): PendingIntent
	{
		val intent = Intent(_context, MediaPlaybackService::class.java)
		intent.action = action
		return PendingIntent.getService(_context, 0, intent, 0)
	}

	private fun updateNotificationStatus(playing: Boolean)
	{
		_playPauseAction.icon = if (playing) R.drawable.pause else R.drawable.play
		_builder.addAction(_playPauseAction)
			.setSmallIcon(if (playing) R.drawable.play else R.drawable.pause)
			// .setOngoing(playing) // doesn't work as notification was started with startForeground, would have to stop service to make it dismissible
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel()
	{
		val chan = NotificationChannel(App.NOTIFICATION_CHANNEL_ID, _context.getString(R.string.media_notification), NotificationManager.IMPORTANCE_LOW)
		chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		val service = _context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		service.createNotificationChannel(chan)
	}
}
