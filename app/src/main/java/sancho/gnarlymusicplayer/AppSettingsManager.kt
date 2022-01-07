package sancho.gnarlymusicplayer

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import sancho.gnarlymusicplayer.models.QueueItem
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File

object AppSettingsManager
{
	private const val DEFAULT_ACCENTCOLOR = "lime"

	const val PREFERENCE_VOLUME_STEP_IDX = "sancho.gnarlymusicplayer.preference.volume.currentidx"
	const val PREFERENCE_VOLUME_SYSTEM_TO_SET = "sancho.gnarlymusicplayer.preference.volume.setsystem"

	var savedTrackPath: String = ""
	var savedTrackTime: Int = 0
	var lastDir: File? = null
	private var accentColorKey: String = DEFAULT_ACCENTCOLOR

	var ignoreAf = false
	var bluetoothCrackingWorkaround = false
	var noPauseMediaSess = false

	var volumeStepsTotal: Int = 30
	var volumeInappEnabled: Boolean = false
	var volumeStepIdx: Int = 15
	var volumeSystemSet: Boolean = false
	var volumeSystemLevel: Int = 7

	private val STYLE_MAP = mapOf(
		"base" to R.style.AppThemeBase,
		"lime" to R.style.AppThemeLime,
		"green" to R.style.AppThemeGreen,
		"blu" to R.style.AppThemeBlu,
		"cyan" to R.style.AppThemeCyan,
		"red" to R.style.AppThemeRed,
		"orang" to R.style.AppThemeOrang,
		"cherri" to R.style.AppThemeCherri,
		"purpl" to R.style.AppThemePurpl,
		"pink" to R.style.AppThemePink,
		"yello" to R.style.AppThemeYello,
		"lavender" to R.style.AppThemeLavender,
		"grey" to R.style.AppThemeGrey
	)

	private val QUEUEITEM_COLLECTION_TYPE = object : TypeToken<Collection<QueueItem>>() {}.type

	private const val PREFERENCE_BOOKMARKS = "sancho.gnarlymusicplayer.preference.bookmarks"
	private const val PREFERENCE_QUEUE = "sancho.gnarlymusicplayer.preference.queue"
	private const val PREFERENCE_LASTDIR = "sancho.gnarlymusicplayer.preference.lastdir"
	private const val PREFERENCE_CURRENTTRACK = "sancho.gnarlymusicplayer.preference.currenttrack"
	private const val PREFERENCE_SAVEDTRACK_PATH = "sancho.gnarlymusicplayer.preference.savedtrack.path"
	private const val PREFERENCE_SAVEDTRACK_TIME = "sancho.gnarlymusicplayer.preference.savedtrack.time"

	fun getStyleFromPreference(): Int
	{
		return STYLE_MAP[accentColorKey] ?: R.style.AppThemeLime
	}

	fun restoreAndGetStyleFromPrefs(context: Context): Int
	{
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
		accentColorKey = sharedPref.getString(context.getString(R.string.pref_accentcolor), DEFAULT_ACCENTCOLOR) ?: DEFAULT_ACCENTCOLOR
		return getStyleFromPreference()
	}

	fun restoreBookmarks(context: Context): MutableList<QueueItem>
	{
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
		val gson = Gson()

		val bookmarksPref = sharedPref.getString(PREFERENCE_BOOKMARKS, "[]")
		return gson.fromJson(bookmarksPref, QUEUEITEM_COLLECTION_TYPE)
	}

	fun restoreFromPrefs(context: Context)
	{
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
		val gson = Gson()

		// if it changed, it means that service modified the queue, so it's already initialized and overwriting it would be *bad*
		if (!PlaybackQueue.hasChanged)
		{
			val queuePref = sharedPref.getString(PREFERENCE_QUEUE, "[]")
			PlaybackQueue.queue = gson.fromJson(queuePref, QUEUEITEM_COLLECTION_TYPE)
		}

		val lastDirPref = File(sharedPref.getString(PREFERENCE_LASTDIR, "") ?: "")
		if (lastDirPref.exists() && (lastDirPref.isDirectory || FileSupportChecker.isFileSupportedAndPlaylist(lastDirPref.absolutePath)))
			lastDir = lastDirPref

		accentColorKey = sharedPref.getString(context.getString(R.string.pref_accentcolor), DEFAULT_ACCENTCOLOR) ?: DEFAULT_ACCENTCOLOR

		PlaybackQueue.autoClean = sharedPref.getBoolean(context.getString(R.string.pref_autoclean), false)

		ignoreAf = sharedPref.getBoolean(context.getString(R.string.pref_ignoreaf), false)
		bluetoothCrackingWorkaround = sharedPref.getBoolean(context.getString(R.string.pref_btcrackworkaround), false)
		noPauseMediaSess = sharedPref.getBoolean(context.getString(R.string.pref_dontpausemediasess), false)

		volumeStepsTotal = sharedPref.getInt(context.getString(R.string.pref_totalsteps), 30)
		volumeInappEnabled = sharedPref.getBoolean(context.getString(R.string.pref_inappenabled), false)
		volumeSystemSet = sharedPref.getBoolean(context.getString(R.string.pref_lockvolume), false)
		volumeSystemLevel = sharedPref.getInt(PREFERENCE_VOLUME_SYSTEM_TO_SET, 7)

		// settings that playback service can change
		// don't load from preferences if playback service is running - will overwrite its settings
		if (!MediaPlaybackService.mediaPlaybackServiceStarted)
		{
			PlaybackQueue.currentIdx = sharedPref.getInt(PREFERENCE_CURRENTTRACK, 0)
			savedTrackPath = sharedPref.getString(PREFERENCE_SAVEDTRACK_PATH, "") ?: ""
			savedTrackTime = sharedPref.getInt(PREFERENCE_SAVEDTRACK_TIME, 0)

			if (volumeInappEnabled)
				volumeStepIdx = sharedPref.getInt(PREFERENCE_VOLUME_STEP_IDX, 15)
		}
	}

	fun saveToPrefs(context: Context, bookmarksChanged: Boolean, lastDirPath: String?, bookmarks: MutableList<QueueItem>)
	{
		with(PreferenceManager.getDefaultSharedPreferences(context).edit())
		{
			if(bookmarksChanged || PlaybackQueue.hasChanged)
			{
				val gson = Gson()
				if(bookmarksChanged)
					putString(PREFERENCE_BOOKMARKS, gson.toJson(bookmarks))

				if(PlaybackQueue.hasChanged)
				{
					putString(PREFERENCE_QUEUE, gson.toJson(PlaybackQueue.queue))
					PlaybackQueue.hasChanged = false
				}
			}
			putString(PREFERENCE_LASTDIR, lastDirPath) // path is null -> preference is going to get deleted - no big deal
			putInt(PREFERENCE_CURRENTTRACK, PlaybackQueue.currentIdx)
			apply()
		}
	}

	fun saveToPrefs(context: Context, saveTrack: Boolean)
	{
		with(PreferenceManager.getDefaultSharedPreferences(context).edit())
		{
			if (saveTrack)
			{
				// not needed when all tracks have been cleared
				putInt(PREFERENCE_CURRENTTRACK, PlaybackQueue.currentIdx)
				putString(PREFERENCE_SAVEDTRACK_PATH, savedTrackPath)
				putInt(PREFERENCE_SAVEDTRACK_TIME, savedTrackTime)
			}

			// save last volume setting
			if (volumeInappEnabled)
				putInt(PREFERENCE_VOLUME_STEP_IDX, volumeStepIdx)

			if (PlaybackQueue.hasChanged)
			{
				val gson = Gson()
				putString(PREFERENCE_QUEUE, gson.toJson(PlaybackQueue.queue))
				PlaybackQueue.hasChanged = false
			}

			apply()
		}
	}
}
