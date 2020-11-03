package sancho.gnarlymusicplayer.activities

import android.graphics.Bitmap
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
			metaDict[FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION] = "${(durationS / 60)}:${(durationS % 60)}"
		}

		val tagList = mutableListOf<Pair<String, String>>()

		for (tag in TagExtractor.NICE_TAG_NAMES)
		{
			val value = TagExtractor.smartTagExtract(metaDict, tag.key)
			if (value != null)
			{
				tagList.add(Pair(tag.key, value))
			}
		}

		val cover = TagExtractor.getTrackBitmap(trackPath, mediaInfo)

		mediaInfo.release()

		return Pair(tagList, cover)
	}

	private fun updateTableView(metaDict: List<Pair<String, String>>, cover: Bitmap?)
	{
		img_cover.setImageBitmap(cover)

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
