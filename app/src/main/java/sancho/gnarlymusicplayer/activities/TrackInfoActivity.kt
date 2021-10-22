package sancho.gnarlymusicplayer.activities

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.TagExtractor
import sancho.gnarlymusicplayer.Toaster
import sancho.gnarlymusicplayer.databinding.ActivityTrackInfoBinding
import wseemann.media.FFmpegMediaMetadataRetriever

class TrackInfoActivity : AppCompatActivity()
{
	private lateinit var _trackPath: String
	private var _raw = false
	private var _loadingJob: Job? = null
	private lateinit var _binding: ActivityTrackInfoBinding

	override fun onCreate(savedInstanceState: Bundle?)
	{
		setTheme(AppSettingsManager.restoreAndGetStyleFromPrefs(this))

		super.onCreate(savedInstanceState)
		_binding = ActivityTrackInfoBinding.inflate(layoutInflater)
		setContentView(_binding.root)

		setSupportActionBar(_binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true) // enable "up" action bar action

		val trackPath = intent.getStringExtra(EXTRA_TRACK_DETAIL_PATH)

		if (trackPath == null)
		{
			Toaster.show(this, getString(R.string.no_track))
			finish()
			return
		}

		_trackPath = trackPath

		asyncGetTrackInfos(::getTrackInfos)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.track_details, menu)
		if (_raw)
			menu.findItem(R.id.action_raw).title = getString(R.string.nice)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			R.id.action_raw -> switchMode()
			android.R.id.home -> super.onBackPressed()
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	private fun switchMode()
	{
		// clear table and show loading state (like on initial activity launch)
		_binding.metaLayout.visibility = View.GONE
		_binding.progressCircle.visibility = View.VISIBLE
		_binding.metaLayout.fullScroll(ScrollView.FOCUS_UP) // have to do this before removing elements for some reason
		_binding.tableMain.removeAllViews()

		_raw = !_raw
		invalidateOptionsMenu() // update navbar btn
		if (_raw)
		{
			title = getString(R.string.track_info_raw)
			asyncGetTrackInfos(::getRawTrackInfos)
		}
		else
		{
			title = getString(R.string.track_info)
			asyncGetTrackInfos(::getTrackInfos)
		}
	}

	private fun asyncGetTrackInfos(f: () -> Pair<List<Pair<String, String>>, Bitmap?>?)
	{
		if (_loadingJob?.isActive == true)
			_loadingJob?.cancel()

		_loadingJob = GlobalScope.launch(Dispatchers.IO) {
			val meta = f()
			GlobalScope.launch(Dispatchers.Main) {
				if (meta == null)
					Toaster.show(this@TrackInfoActivity, getString(R.string.no_track))
				else
					updateTableView(meta.first, meta.second)
			}
		}
	}

	private fun getRawTrackInfos(): Pair<List<Pair<String, String>>, Bitmap?>?
	{
		val mediaInfo = FFmpegMediaMetadataRetriever()
		try
		{
			mediaInfo.setDataSource(_trackPath)
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
		mediaInfo.release()

		val tagList = mutableListOf<Pair<String, String>>()

		for (tag in metaDict)
		{
			tagList.add(Pair(tag.key, tag.value))
		}

		tagList.sortBy { elem -> elem.first }

		return Pair(tagList, null)
	}

	private fun getTrackInfos(): Pair<List<Pair<String, String>>, Bitmap?>?
	{
		val mediaInfo = FFmpegMediaMetadataRetriever()
		try
		{
			mediaInfo.setDataSource(_trackPath)
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

		val sizeBytes = metaDict[FFmpegMediaMetadataRetriever.METADATA_KEY_FILESIZE]
		if (sizeBytes != null)
		{
			val niceSize = sizeBytes.toFloat() / 1000000
			metaDict[FFmpegMediaMetadataRetriever.METADATA_KEY_FILESIZE] = "%.2fMB".format(niceSize)
		}

		val tagList = mutableListOf<Pair<String, String>>()

		if (TagExtractor.smartTagExtract(metaDict, "Codec") == "opus")
		{
			// workaround for opus :/ cover is ok, tags not

			val lameMediaInfo = MediaMetadataRetriever()
			try
			{
				lameMediaInfo.setDataSource(_trackPath)
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

		tagList.add(Pair("Path", _trackPath))

		val cover = TagExtractor.getTrackBitmap(_trackPath, mediaInfo.embeddedPicture)

		if (("video_width" in metaDict && "video_height" in metaDict))
			tagList.add(Pair("Cover size", "%sx%s".format(metaDict["video_width"], metaDict["video_height"])))
		else if (cover != null)
			tagList.add(Pair("Cover size", "%dx%d".format(cover.width, cover.height)))

		if (mediaInfo.embeddedPicture != null)
			tagList.add(Pair("Cover src", "Tag"))
		else if (cover != null)
			tagList.add(Pair("Cover src", "Directory"))

		mediaInfo.release()

		return Pair(tagList, cover)
	}

	private fun updateTableView(metaDict: List<Pair<String, String>>, cover: Bitmap?)
	{
		if (cover != null)
		{
			_binding.imgCover.setImageBitmap(cover)
			_binding.imgCover.visibility = View.VISIBLE
		}
		else
			_binding.imgCover.visibility = View.GONE

		for (row in metaDict)
		{
			val view = LayoutInflater.from(this).inflate(R.layout.track_details_row, _binding.tableMain, false)
			view.findViewById<TextView>(R.id.text_key).text = row.first
			view.findViewById<TextView>(R.id.text_value).text = row.second
			_binding.tableMain.addView(view)
		}

		_binding.progressCircle.visibility = View.GONE
		_binding.metaLayout.visibility = View.VISIBLE
	}

	override fun onPause()
	{
		_loadingJob?.cancel()
		super.onPause()
	}
}
