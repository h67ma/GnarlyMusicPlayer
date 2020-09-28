package sancho.gnarlymusicplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.Toast
import sancho.gnarlymusicplayer.models.Track
import java.io.File
import java.lang.RuntimeException

fun showCurrTrackInfo(context: Context)
{
	if (!PlaybackQueue.trackSelected())
	{
		Toast.makeText(context, context.getString(R.string.no_track_selected), Toast.LENGTH_SHORT).show()
		return
	}

	val mediaInfo = MediaMetadataRetriever()
	mediaInfo.setDataSource(PlaybackQueue.getCurrentTrackPath())
	val durationSS = (mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toInt() / 1000
	val kbps = (mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE) ?: "0").toInt() / 1000

	AlertDialog.Builder(context)
		.setTitle(PlaybackQueue.getCurrentTrackName())
		.setMessage(context.getString(R.string.about_track,
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "",
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: "",
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "",
			durationSS / 60,
			durationSS % 60,
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER) ?: "",
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: "",
			kbps,
			mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "",
			PlaybackQueue.getCurrentTrackPath()
		))
		.setPositiveButton(context.getString(R.string.close), null)
		.create()
		.show()
}

fun resetTrackMeta(track: Track)
{
	track.path = ""
	track.title = ""
	track.artist = ""
	track.year = null
	track.cover = null
}

// sets track meta from current track in queue
fun setTrackMeta(track: Track)
{
	val queueItem = PlaybackQueue.getCurrentTrack()

	if (queueItem == null)
	{
		resetTrackMeta(track)
		return
	}

	// first for the obvious
	track.path = queueItem.path

	// title and artist
	val mediaInfo = MediaMetadataRetriever()
	try
	{
		mediaInfo.setDataSource(queueItem.path)
	}
	catch(_: RuntimeException) // invalid file
	{
		resetTrackMeta(track)
		return
	}
	catch(_: IllegalArgumentException) // invalid file path
	{
		resetTrackMeta(track)
		return
	}

	track.title = mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: queueItem.name
	track.artist = mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
	track.year = mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.toIntOrNull() // I won't accept some weird timestamps, only year

	// cover
	// first try embedded artwork
	val embeddedPic = mediaInfo.embeddedPicture

	if(embeddedPic != null)
	{
		track.cover = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size)
	}
	else
	{
		// fallback to album art in track's dir

		val dir = File(queueItem.path).parent

		var foundCover = false
		for (filename in App.ALBUM_ART_FILENAMES)
		{
			val art = File(dir, filename)

			if (art.exists())
			{
				val bitmap = BitmapFactory.decodeFile(art.absolutePath, BitmapFactory.Options())
				track.cover = bitmap
				foundCover = true
				break
			}
		}

		// no cover found
		if (!foundCover)
			track.cover = null
	}
}
