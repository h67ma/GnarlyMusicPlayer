package sancho.gnarlymusicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import sancho.gnarlymusicplayer.models.Track
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File
import java.util.*

object TagExtractor
{
	val NICE_TAG_NAMES = linkedMapOf(
		"Title" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_TITLE),
		"Artist" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_ARTIST),
		"Album" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM),
		"Date" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_DATE),
		"Genre" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_GENRE),
		"Album artist" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM_ARTIST, "albumartist", "album artist"),
		"Composer" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_COMPOSER),
		"Track number" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_TRACK, "tracknumber", "track number", "track_number"),
		"Total tracks" to listOf("totaltracks", "total tracks", "total_tracks"),
		"Disc number" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_DISC, "discnumber", "disc number", "disc_number"),
		"Total discs" to listOf("totaldiscs", "total discs", "total_discs"),
		"Duration" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION),
		"Codec" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_AUDIO_CODEC, "audiocodec", "audio codec"),
		"BPM" to listOf("bpm", "tpbm"),
		"Comment" to listOf(FFmpegMediaMetadataRetriever.METADATA_KEY_COMMENT)
	)

	private val ALBUM_ART_FILENAMES = arrayOf(
		"Folder.png",
		"Folder.jpg",
		"Folder.jpeg",
		"Folder.jfif",
		"Artist.png",
		"Artist.jpg",
		"Artist.jpeg",
		"Artist.jfif"
	)

	private fun resetTrackMeta(track: Track)
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
		val mediaInfo = FFmpegMediaMetadataRetriever()
		try
		{
			mediaInfo.setDataSource(queueItem.path)
		}
		catch (_: RuntimeException) // invalid file
		{
			resetTrackMeta(track)
			return
		}
		catch (_: IllegalArgumentException) // invalid file path
		{
			resetTrackMeta(track)
			return
		}

		val tagDict = lowercaseTagNames(mediaInfo.metadata.all)

		track.title = smartTagExtract(tagDict, "Title") ?: queueItem.name
		track.artist = smartTagExtract(tagDict, "Artist") ?: ""
		track.year = smartTagExtract(tagDict, "Date")?.toIntOrNull() // I won't accept some weird timestamps, only year

		track.cover = getTrackBitmap(queueItem.path, mediaInfo)
	}

	fun lowercaseTagNames(tagDict: Map<String, String>): MutableMap<String, String>
	{
		val lowercasedDict = mutableMapOf<String, String>()
		for (item in tagDict)
		{
			lowercasedDict[item.key.toLowerCase(Locale.getDefault())] = item.value
		}
		return lowercasedDict
	}

	fun smartTagExtract(tagDict: Map<String, String>, niceTagName: String): String?
	{
		if (niceTagName in NICE_TAG_NAMES)
		{
			for (possibleTagName in NICE_TAG_NAMES[niceTagName]!!) // first time when I had to use !!. I don't know what is your problem kotlin.
			{
				if (possibleTagName in tagDict)
					return tagDict[possibleTagName]
			}
		}
		return null
	}

	fun getTrackBitmap(trackPath: String, mediaInfo: FFmpegMediaMetadataRetriever): Bitmap?
	{
		// first try embedded artwork
		val embeddedPic = mediaInfo.embeddedPicture

		if (embeddedPic != null)
		{
			return BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size)
		}
		else
		{
			// fallback to album art in track's dir

			val dir = File(trackPath).parent

			for (filename in ALBUM_ART_FILENAMES)
			{
				val art = File(dir, filename)

				if (art.exists())
				{
					return BitmapFactory.decodeFile(art.absolutePath, BitmapFactory.Options())
				}
			}

			// no cover found
			return null
		}
	}
}
