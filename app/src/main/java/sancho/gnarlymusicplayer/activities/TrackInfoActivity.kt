package sancho.gnarlymusicplayer.activities

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.toolbar
import kotlinx.android.synthetic.main.activity_track_info.*
import kotlinx.android.synthetic.main.track_details_row.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.TagExtractor
import wseemann.media.FFmpegMediaMetadataRetriever

class TrackInfoActivity : AppCompatActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		setTheme(AppSettingsManager.restoreAndGetStyleFromPrefs(this))

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_track_info)

		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true) // enable "up" action bar action

		val trackPath = intent.getStringExtra(EXTRA_TRACK_DETAIL_PATH)

		if (trackPath == null)
		{
			Toast.makeText(this, getString(R.string.no_track), Toast.LENGTH_SHORT).show()
			finish()
			return
		}

		GlobalScope.launch(Dispatchers.IO) {
			val meta = getTrackInfos(trackPath)
			GlobalScope.launch(Dispatchers.Main) {
				if (meta == null)
					Toast.makeText(applicationContext, getString(R.string.no_track), Toast.LENGTH_SHORT).show()
				else
					updateTableView(meta.first, meta.second)
			}
		}
	}

	private fun getTrackInfos(trackPath: String): Pair<List<Pair<String, String>>, Bitmap?>?
	{
		val mediaInfo = FFmpegMediaMetadataRetriever()
		try
		{
			mediaInfo.setDataSource(trackPath)
		}
		catch (_: RuntimeException) // invalid file
		{
			return null
		}
		catch (_: IllegalArgumentException) // invalid file path
		{
			return null
		}

		val metaDict = TagExtractor.lowercaseTagNames(mediaInfo.metadata.all)

		val durationMS = metaDict[FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION]
		if (durationMS != null)
		{
			val durationS = durationMS.toInt() / 1000
			metaDict[FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION] = "%d:%02d".format(durationS / 60, durationS % 60)
		}

		val tagList = mutableListOf<Pair<String, String>>()

		if (TagExtractor.smartTagExtract(metaDict, "Codec") == "opus")
		{
			// workaround for opus :/ cover is ok, tags not

			val lameMediaInfo = MediaMetadataRetriever()
			try
			{
				lameMediaInfo.setDataSource(trackPath)
			}
			catch (_: RuntimeException) // invalid file
			{
				return null
			}
			catch (_: IllegalArgumentException) // invalid file path
			{
				return null
			}

			for (tag in TagExtractor.MMR_TAGS)
			{
				val tagValue = lameMediaInfo.extractMetadata(tag.value)
				if (tagValue != null)
					tagList.add(Pair(tag.key, tagValue))
			}

			lameMediaInfo.release()
		}

		for (tag in TagExtractor.FFMPEG_TAGS)
		{
			val value = TagExtractor.smartTagExtract(metaDict, tag.key)
			if (value != null)
			{
				tagList.add(Pair(tag.key, value))
			}
		}

		tagList.add(Pair("Path", trackPath))

		val cover = TagExtractor.getTrackBitmap(trackPath, mediaInfo)

		if (mediaInfo.embeddedPicture != null)
			tagList.add(Pair("Cover art source", "Tag"))
		else if (cover != null)
			tagList.add(Pair("Cover art source", "Directory"))

		mediaInfo.release()

		return Pair(tagList, cover)
	}

	private fun updateTableView(metaDict: List<Pair<String, String>>, cover: Bitmap?)
	{
		if (cover != null)
			img_cover.setImageBitmap(cover)
		else
			img_cover.visibility = View.GONE

		for (row in metaDict)
		{
			val view = LayoutInflater.from(this).inflate(R.layout.track_details_row, table_main, false)
			view.text_key.text = row.first
			view.text_value.text = row.second
			table_main.addView(view)
		}

		progress_circle.visibility = View.GONE
		meta_layout.visibility = View.VISIBLE
	}
}
