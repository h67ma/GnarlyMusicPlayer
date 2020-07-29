package sancho.gnarlymusicplayer

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import sancho.gnarlymusicplayer.models.QueueItem
import sancho.gnarlymusicplayer.models.Track
import java.io.File

fun setTrackMeta(queueItem: QueueItem, track: Track)
{
	// first for the obvious
	track.path = queueItem.path

	// title and artist
	val mediaInfo = MediaMetadataRetriever()
	mediaInfo.setDataSource(queueItem.path)

	track.title = mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: queueItem.name
	track.artist = mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""

	// cover
	// first try embedded artwork
	val mmr = MediaMetadataRetriever()
	mmr.setDataSource(queueItem.path)

	val embeddedPic = mmr.embeddedPicture

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
