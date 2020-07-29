package sancho.gnarlymusicplayer.activities
import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_seek.view.*
import sancho.gnarlymusicplayer.*
import sancho.gnarlymusicplayer.MediaPlaybackService.LocalBinder
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter
import sancho.gnarlymusicplayer.models.ExplorerHeader
import sancho.gnarlymusicplayer.models.ExplorerItem
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.QueueItem
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity()
{
	private lateinit var _mountedDevices: MutableList<ExplorerViewItem>

	private val _dirList = mutableListOf<ExplorerViewItem>()
	private var _prevDirList = mutableListOf<ExplorerViewItem>() // store "real" dir listing, for re-searching ability. because _dirList contains search results
	private var _currentDir: File? = null
	private var _lastDir: File? = null // from shared preferences

	private lateinit var _explorerAdapter: ExplorerAdapter

	private lateinit var _queueAdapter: QueueAdapter
	private var _queueChanged = false
	private var _lastSelectedTrack: Int = RecyclerView.NO_POSITION

	private lateinit var _bookmarks: MutableList<QueueItem>
	private var _bookmarksChanged = false

	private var _accentColorKey: String = App.DEFAULT_ACCENTCOLOR

	private var _actionSearch: MenuItem? = null

	private var _searchResultsOpen = false

	private var _seekDialog: AlertDialog? = null

	private var _service: MediaPlaybackService? = null
	private val _serviceConn = object : ServiceConnection
	{
		override fun onServiceConnected(className: ComponentName, service: IBinder)
		{
			_service = (service as LocalBinder).getService(object : BoundServiceListeners{
				override fun onTrackChanged(oldPos: Int)
				{
					_queueAdapter.notifyItemChanged(oldPos)
					_queueAdapter.notifyItemChanged(App.currentTrack)

					if (_seekDialog?.isShowing == true)
						_seekDialog?.dismiss()
				}

				override fun onEnd()
				{
					if (_seekDialog?.isShowing == true)
						_seekDialog?.dismiss()
				}
			})
		}

		override fun onServiceDisconnected(className: ComponentName)
		{
			_service = null
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		restoreFromPrefs()
		_bookmarksChanged = false
		_queueChanged = false

		setTheme(getStyleFromPreference(_accentColorKey))

		if(savedInstanceState != null)
			_lastSelectedTrack = savedInstanceState.getInt(App.BUNDLE_LASTSELECTEDTRACK, RecyclerView.NO_POSITION)

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		title = ""

		getStorageDevices() // prepare list with storage devices

		setupBookmarks()

		setupQueue()

		setupFileList()

		requestReadPermishon() // check for permissions and initial update of file list
	}

	override fun onResume()
	{
		super.onResume()

		if(App.mediaPlaybackServiceStarted)
			bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)

		if (App.currentTrack != _lastSelectedTrack)
		{
			_queueAdapter.notifyItemChanged(_lastSelectedTrack)
			_queueAdapter.notifyItemChanged(App.currentTrack)
		}
	}

	private fun restoreFromPrefs()
	{
		val gson = Gson()
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

		val bookmarksPref = sharedPref.getString(App.PREFERENCE_BOOKMARKS, "[]")
		val collectionType = object : TypeToken<Collection<QueueItem>>() {}.type
		_bookmarks = gson.fromJson(bookmarksPref, collectionType)

		val queuePref = sharedPref.getString(App.PREFERENCE_QUEUE, "[]")
		App.queue = gson.fromJson(queuePref, collectionType)

		val lastDir = File(sharedPref.getString(App.PREFERENCE_LASTDIR, "") ?: "") // again: what's your problem kotlin? isn't ONE default value enough for you?
		if (lastDir.exists() && lastDir.isDirectory) _lastDir = lastDir

		_accentColorKey = sharedPref.getString(getString(R.string.pref_accentcolor), App.DEFAULT_ACCENTCOLOR) ?: App.DEFAULT_ACCENTCOLOR // what's your problem kotlin?

		App.volumeStepsTotal = sharedPref.getInt(getString(R.string.pref_totalsteps), 30)
		App.volumeInappEnabled = sharedPref.getBoolean(getString(R.string.pref_inappenabled), false)
		App.volumeSystemSet = sharedPref.getBoolean(getString(R.string.pref_lockvolume), false)
		App.volumeSystemLevel = sharedPref.getInt(App.PREFERENCE_VOLUME_SYSTEM_TO_SET, 7)

		// settings that playback service can change
		// don't load from preferences if playback service is running - will overwrite its settings
		if (!App.mediaPlaybackServiceStarted)
		{
			App.currentTrack = sharedPref.getInt(App.PREFERENCE_CURRENTTRACK, 0)
			App.savedTrackPath = sharedPref.getString(App.PREFERENCE_SAVEDTRACK_PATH, "") ?: "" // what's your problem kotlin?
			App.savedTrackTime = sharedPref.getInt(App.PREFERENCE_SAVEDTRACK_TIME, 0)

			if (App.volumeInappEnabled)
				App.volumeStepIdx = sharedPref.getInt(App.PREFERENCE_VOLUME_STEP_IDX, 15)
		}
	}

	override fun onSaveInstanceState(outState: Bundle)
	{
		super.onSaveInstanceState(outState)
		outState.putInt(App.BUNDLE_LASTSELECTEDTRACK, _lastSelectedTrack)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
	{
		super.onActivityResult(requestCode, resultCode, data)

		if (requestCode == App.INTENT_LAUNCH_FOR_RESULT_SETTINGS)
			recreate() // must call onCreate after changing theme
	}

	private fun setupFileList()
	{
		library_list_view.layoutManager = LinearLayoutManager(this)

		_explorerAdapter = ExplorerAdapter(this, _dirList,
			{ item ->
				val file = File(item.path)
				if (file.exists())
				{
					if (file.isDirectory)
					{
						// navigate to directory

						updateDirectoryView(file)
						_searchResultsOpen = false // in case the dir was from search results
					}
					else
					{
						addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
					}
				}
				else
					Toast.makeText(applicationContext, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
			},
			{ item ->
				val file = File(item.path)
				if (file.exists())
				{
					if (file.isDirectory)
					{
						// add all tracks in dir (not recursive)
						val files = file.listFiles{fileFromDir ->
							isFileExtensionInArray(fileFromDir.name, App.SUPPORTED_FILE_EXTENSIONS)
						}
						if (files != null)
						{
							Arrays.sort(files, App.filesComparator)
							addToQueue(files.map { track ->
								QueueItem(track.absolutePath, track.nameWithoutExtension)
							})

							Toast.makeText(this, getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
						}
						else
							Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
					}
					else
					{
						addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
						playTrack(App.queue.size - 1)
					}
				}
				else
					Toast.makeText(applicationContext, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()

				true
			}
		)
		library_list_view.adapter = _explorerAdapter
	}

	private fun setupBookmarks()
	{
		bookmark_list_view.layoutManager = LinearLayoutManager(this)
		val adapter = BookmarksAdapter(this, _bookmarks) { bookmark ->

			if (bookmark.path == _currentDir?.absolutePath)
			{
				drawer_layout.closeDrawer(GravityCompat.END)
				return@BookmarksAdapter // already open
			}

			val dir = File(bookmark.path)
			if (dir.exists())
			{
				updateDirectoryView(dir)
				drawer_layout.closeDrawer(GravityCompat.END)
				_actionSearch?.collapseActionView() // collapse searchbar thing
			}
			else
				Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
		}
		bookmark_list_view.adapter = adapter

		val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback()
		{
			override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int
			{
				return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT)
			}

			override fun isLongPressDragEnabled(): Boolean
			{
				return false
			}

			override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
			{
				adapter.onItemMoved(viewHolder.adapterPosition, target.adapterPosition)
				_bookmarksChanged = true
				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val position = viewHolder.adapterPosition
				adapter.onItemRemoved(position)
				_bookmarksChanged = true
				adapter.notifyItemRemoved(position)
			}
		})
		adapter.touchHelper = touchHelper
		touchHelper.attachToRecyclerView(bookmark_list_view)

		bookmark_add_btn.setOnClickListener {
			val path = _currentDir?.absolutePath
			val label = _currentDir?.name
			if (path != null && label != null)
			{
				// check if bookmark already exists
				if (_bookmarks.any { item -> item.path == path })
				{
					Toast.makeText(applicationContext, getString(R.string.bookmark_exists), Toast.LENGTH_SHORT).show()
					return@setOnClickListener
				}

				// also add to bookmark menu
				adapter.onItemAdded(QueueItem(path, label))
				_bookmarksChanged = true
				adapter.notifyItemInserted(_bookmarks.size - 1)
			}
			else
				Toast.makeText(applicationContext, getString(R.string.cant_add_root_dir), Toast.LENGTH_SHORT).show()
		}

		bookmark_root.setOnClickListener {
			updateDirectoryView(null)
			drawer_layout.closeDrawer(GravityCompat.END)
		}
	}

	private fun setupQueue()
	{
		queue_list_view.layoutManager = LinearLayoutManager(this)
		_queueAdapter = QueueAdapter(this, App.queue) { position ->
			playTrack(position)
		}
		queue_list_view.adapter = _queueAdapter

		val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback()
		{
			override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int
			{
				return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT)
			}

			override fun isLongPressDragEnabled(): Boolean
			{
				return false
			}

			override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
			{
				val fromPosition = viewHolder.adapterPosition
				val toPosition = target.adapterPosition
				_queueAdapter.onItemMoved(fromPosition, toPosition)

				_queueChanged = true

				if (fromPosition == App.currentTrack)
				{
					App.currentTrack = toPosition
				}
				else if (toPosition == App.currentTrack)
				{
					if (fromPosition < App.currentTrack)
						App.currentTrack--
					else if (fromPosition > App.currentTrack)
						App.currentTrack++
				}

				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val position = viewHolder.adapterPosition
				App.queue.removeAt(position)
				_queueChanged = true
				_queueAdapter.notifyItemRemoved(position)

				if (position < App.currentTrack)
				{
					App.currentTrack--
				}
				else if (position == App.currentTrack)
				{
					// we've removed currently selected track
					// select next track and notify service (it'll know if it needs to be played)
					when
					{
						position < App.queue.size ->
						{
							// removed track wasn't last - select next track in queue
							// no need to change currentTrack
							if (App.mediaPlaybackServiceStarted && _service != null)
								_service?.setTrack(false)

							_queueAdapter.notifyItemChanged(App.currentTrack)
						}
						App.queue.size > 0 ->
						{
							// removed track was last - select first track in queue (if any tracks exist)
							App.currentTrack = 0

							if (App.mediaPlaybackServiceStarted && _service != null)
							_service?.setTrack(false)

							_queueAdapter.notifyItemChanged(App.currentTrack)
						}
						else ->
						{
							// no other track available
							if (App.mediaPlaybackServiceStarted && _service != null)
								_service?.end(false)

							App.currentTrack = RecyclerView.NO_POSITION
						}
					}
				}
			}
		})
		_queueAdapter.touchHelper = touchHelper
		touchHelper.attachToRecyclerView(queue_list_view)
	}

	private fun addToQueue(queueItem: QueueItem)
	{
		App.queue.add(queueItem)
		_queueAdapter.notifyItemInserted(App.queue.size - 1)
		_queueChanged = true
	}

	private fun addToQueue(trackList: List<QueueItem>)
	{
		App.queue.addAll(trackList)
		_queueAdapter.notifyItemRangeInserted(App.queue.size - trackList.size, trackList.size)
		_queueChanged = true
	}

	private fun playTrack(newPosition: Int)
	{
		if (!File(App.queue[newPosition].path).exists())
		{
			Toast.makeText(this, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
			return
		}

		if (App.mediaPlaybackServiceStarted && _service != null)
			_service?.saveTrackPosition()

		val oldPos = App.currentTrack
		_queueAdapter.notifyItemChanged(oldPos)
		App.currentTrack = newPosition
		_queueAdapter.notifyItemChanged(App.currentTrack)

		if (!App.mediaPlaybackServiceStarted || _service == null)
		{
			val intent = Intent(this, MediaPlaybackService::class.java) // excuse me, WHAT IN THE GODDAMN. why not ..-::class::..kt..*java*::-.. while we're at it?
			intent.action = App.ACTION_START_PLAYBACK_SERVICE
			startService(intent)

			bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)
		}
		else
		{
			if (oldPos == newPosition)
				_service?.playPause()
			else
				_service?.setTrack(true)
		}
	}

	private fun getStorageDevices()
	{
		_mountedDevices = mutableListOf()
		val externalStorageFiles = getExternalFilesDirs(null)
		for(f in externalStorageFiles)
		{
			val device = f.parentFile.parentFile.parentFile.parentFile // srsl?
			_mountedDevices.add(ExplorerItem(device.path, device.name, device.isDirectory))
		}
	}

	private fun requestReadPermishon()
	{
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			updateDirectoryView(_lastDir)
		}
		else
		{
			requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), App.REQUEST_READ_STORAGE)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if(requestCode == App.REQUEST_READ_STORAGE)
		{
			if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			{
				updateDirectoryView(_lastDir)
			}
			else
			{
				requestReadPermishon()
			}
		}
	}

	// assuming the read permission is granted
	private fun updateDirectoryView(newDir: File?, oldPath: String? = null)
	{
		_currentDir = newDir
		if(newDir == null || !newDir.exists() || !newDir.isDirectory)
		{
			// list storage devices
			toolbar_title.text = getString(R.string.root_dir_name)
			_dirList.clear()
			_dirList.addAll(_mountedDevices)
			_explorerAdapter.notifyDataSetChanged()
		}
		else
		{
			// list current dir
			toolbar_title.text = newDir.absolutePath
			val list = newDir.listFiles{file ->
				file.isDirectory || isFileExtensionInArray(file.name, App.SUPPORTED_FILE_EXTENSIONS)
			}

			if (list != null)
			{
				val viewList = list.map{file -> ExplorerItem(file.path, file.name, file.isDirectory)}.toMutableList()
				viewList.sortWith(App.explorerViewFilesAndDirsComparator)
				_dirList.clear()
				_dirList.addAll(viewList)
				_explorerAdapter.notifyDataSetChanged()
			}
			else
				Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}

		if (oldPath != null)
		{
			// find previous dir (child) in current dir list, scroll to it
			val pos = _dirList.indexOf(ExplorerItem(oldPath, "", true))
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 200)
		}
		else
		{
			// scroll to top
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
		}
	}

	//region MENU

	// adds items to toolbar
	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)

		// search thing
		_actionSearch = menu.findItem(R.id.action_search)
		val actionClearMenu = menu.findItem(R.id.action_menu_clear)

		val menuElementsToToggle = listOf(
			menu.findItem(R.id.action_seek),
			menu.findItem(R.id.action_currenttrack_info),
			menu.findItem(R.id.action_goto_folder),
			actionClearMenu,
			menu.findItem(R.id.action_clearprev),
			menu.findItem(R.id.action_clearall),
			menu.findItem(R.id.action_clearafter),
			menu.findItem(R.id.action_settings)
		)

		actionClearMenu.subMenu.clearHeader() // don't show header

		val searchThing = _actionSearch?.actionView as SearchView
		searchThing.queryHint = getString(R.string.search_bar_hint)
		searchThing.maxWidth = Int.MAX_VALUE

		_actionSearch?.setOnActionExpandListener(object: MenuItem.OnActionExpandListener
		{
			// SearchView.OnCloseListener simply doesn't work. THANKS ANDROID
			override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach{elem -> elem.isVisible = true}

				updateDirectoryView(_currentDir)
				_searchResultsOpen = false
				return true
			}

			override fun onMenuItemActionExpand(p0: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach{elem -> elem.isVisible = false}

				return true
			}
		})

		searchThing.setOnQueryTextListener(object : SearchView.OnQueryTextListener
		{
			override fun onQueryTextSubmit(query: String): Boolean
			{
				if (!_searchResultsOpen)
				{
					_prevDirList.clear()
					_prevDirList.addAll(_dirList)
				}

				val searchResultList = mutableListOf<ExplorerViewItem>()

				val queryButLower = query.toLowerCase(Locale.getDefault())

				// add results from current dir
				searchResultList.addAll(_prevDirList
					.filter { file ->
						file.displayName.toLowerCase(Locale.getDefault()).contains(queryButLower)
					}
					.sortedWith(App.explorerViewFilesComparator)
				)

				// add results from first level dirs (grouped by subdir name)
				for (elem in _prevDirList.filter{file -> file.isDirectory}.sortedWith(App.explorerViewFilesComparator))
				{
					val dir = File(elem.path)

					val results = dir
						.listFiles{file ->
							(file.isDirectory || isFileExtensionInArray(file.name, App.SUPPORTED_FILE_EXTENSIONS))
									&& file.name.toLowerCase(Locale.getDefault()).contains(queryButLower)
						}
						.map{file -> ExplorerItem(file.path, file.name, file.isDirectory) }
						.sortedWith(App.explorerViewFilesComparator)

					if (results.isNotEmpty())
					{
						// add subdir header
						searchResultList.add(ExplorerHeader(elem.displayName))

						// add results in this dir
						searchResultList.addAll(results)
					}
				}

				_dirList.clear()
				_dirList.addAll(searchResultList)
				_explorerAdapter.notifyDataSetChanged()
				_searchResultsOpen = true

				return false // do "default action" (dunno what it is but it hides keyboard)
			}

			override fun onQueryTextChange(newText: String): Boolean
			{
				return true // don't do default action
			}
		})

		return super.onCreateOptionsMenu(menu)
	}

	// toolbar item clicks
	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			R.id.action_seek -> showSeekDialog()
			R.id.action_currenttrack_info -> showCurrTrackInfo()
			R.id.action_goto_folder -> gotoCurrentTrackDir()
			R.id.action_clearprev -> clearPrev()
			R.id.action_clearall -> clearAll()
			R.id.action_clearafter -> clearAfter()
			R.id.action_settings -> launchSettings()
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	//endregion

	//region MENU ACTIONS

	private fun showCurrTrackInfo()
	{
		if (App.queue.size < 1 || App.currentTrack == RecyclerView.NO_POSITION)
		{
			Toast.makeText(this, getString(R.string.no_track_selected), Toast.LENGTH_SHORT).show()
			return
		}

		val mediaInfo = MediaMetadataRetriever()
		mediaInfo.setDataSource(App.queue[App.currentTrack].path)
		val durationSS = (mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toInt() / 1000
		val kbps = (mediaInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE) ?: "0").toInt() / 1000

		AlertDialog.Builder(this)
			.setTitle(App.queue[App.currentTrack].name)
			.setMessage(getString(R.string.about_track,
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
				App.queue[App.currentTrack].path
			))
			.setPositiveButton(getString(R.string.close), null)
			.create()
			.show()
	}

	private fun gotoCurrentTrackDir()
	{
		if (App.queue.size < 1 || App.currentTrack == RecyclerView.NO_POSITION)
		{
			Toast.makeText(this, getString(R.string.no_track_selected), Toast.LENGTH_SHORT).show()
			return
		}

		val dir = File(App.queue[App.currentTrack].path)
		if (dir.parentFile?.exists() == true)
		{
			updateDirectoryView(dir.parentFile)
		}
		else
			Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
	}

	private fun showSeekDialog()
	{
		if (!App.mediaPlaybackServiceStarted || _service == null)
		{
			Toast.makeText(this, getString(R.string.playback_service_not_running), Toast.LENGTH_SHORT).show()
			return
		}

		val seekView = View.inflate(this, R.layout.dialog_seek, null)

		// set current/total time
		val currentTime = _service?.getCurrentTime() ?: 0
		val totalTime = _service?.getTotalTime() ?: 0
		val totalTimeMinutes = totalTime / 60
		val totalTimeSeconds = totalTime % 60
		seekView.seek_seekbar.max = totalTime
		seekView.seek_seekbar.progress = currentTime
		seekView.seek_currenttotaltime.text = getString(
			R.string.seek_total_time,
			currentTime / 60,
			currentTime % 60,
			totalTimeMinutes,
			totalTimeSeconds
		)

		val savedTrack = File(App.savedTrackPath)
		if (savedTrack.exists() && App.queue[App.currentTrack].path == App.savedTrackPath)
		{
			seekView.seek_loadbtn.visibility = View.VISIBLE
			seekView.seek_loadbtn.setOnClickListener{
				seekView.seek_seekbar.progress = App.savedTrackTime
				_service?.seekAndPlay(seekView.seek_seekbar.progress)
			}
		}

		seekView.seek_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
			override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean)
			{
				seekView.seek_currenttotaltime.text = getString(
					R.string.seek_total_time,
					progress / 60,
					progress % 60,
					totalTimeMinutes,
					totalTimeSeconds
				)
			}

			override fun onStartTrackingTouch(seekbar: SeekBar?) {}

			override fun onStopTrackingTouch(seekbar: SeekBar?)
			{
				if (App.mediaPlaybackServiceStarted && _service != null)
				{
					_service?.seekAndPlay(seekView.seek_seekbar.progress)
				}
				else
					Toast.makeText(applicationContext, getString(R.string.playback_service_not_running), Toast.LENGTH_SHORT).show()
			}
		})

		_seekDialog = AlertDialog.Builder(this)
			.setTitle(getString(R.string.seek_restore_position))
			.setView(seekView)
			.setNegativeButton(R.string.close, null)
			.create()

		_seekDialog?.show()
	}

	private fun clearPrev()
	{
		if (App.currentTrack != RecyclerView.NO_POSITION && App.currentTrack > 0)
		{
			// there are items to clear at the start

			Toast.makeText(this, getString(R.string.cleared_n_tracks, App.currentTrack), Toast.LENGTH_SHORT).show()

			for (i in 0 until App.currentTrack)
				App.queue.removeAt(0)

			val removedCnt = App.currentTrack
			App.currentTrack = 0
			_queueAdapter.notifyItemRangeRemoved(0, removedCnt)
			_queueChanged = true
		}
	}

	private fun clearAll()
	{
		if (App.queue.size > 0)
		{
			Toast.makeText(this, getString(R.string.cleared_n_tracks, App.queue.size), Toast.LENGTH_SHORT).show()

			if (App.mediaPlaybackServiceStarted && _service != null)
				_service?.end(false)

			val removedCnt = App.queue.size
			App.queue.clear()

			App.currentTrack = RecyclerView.NO_POSITION
			_queueAdapter.notifyItemRangeRemoved(0, removedCnt)
			_queueChanged = true
		}
	}

	private fun clearAfter()
	{
		if (App.currentTrack != RecyclerView.NO_POSITION && App.currentTrack < App.queue.size - 1)
		{
			// there are items to clear at the end

			Toast.makeText(this, getString(R.string.cleared_n_tracks, App.queue.size - 1 - App.currentTrack), Toast.LENGTH_SHORT).show()

			val removedCnt = App.queue.size - App.currentTrack
			val removedFromIdx = App.currentTrack + 1
			for (i in App.queue.size - 1 downTo App.currentTrack + 1)
				App.queue.removeAt(i)

			App.currentTrack = App.queue.size - 1
			_queueAdapter.notifyItemRangeRemoved(removedFromIdx, removedCnt)
			_queueChanged = true
		}
	}

	private fun launchSettings()
	{
		startActivityForResult(Intent(this, SettingsActivity::class.java), App.INTENT_LAUNCH_FOR_RESULT_SETTINGS)
	}

	//endregion

	override fun onPause()
	{
		// unbind service
		if(App.mediaPlaybackServiceStarted && _service != null)
			unbindService(_serviceConn)

		_lastSelectedTrack = App.currentTrack

		_actionSearch?.collapseActionView() // collapse searchbar thing

		// save to shared prefs
		with(PreferenceManager.getDefaultSharedPreferences(this).edit())
		{
			if(_bookmarksChanged || _queueChanged)
			{
				val gson = Gson()
				if(_bookmarksChanged)
					putString(App.PREFERENCE_BOOKMARKS, gson.toJson(_bookmarks))

				if(_queueChanged)
					putString(App.PREFERENCE_QUEUE, gson.toJson(App.queue))
			}
			putString(App.PREFERENCE_LASTDIR, _currentDir?.absolutePath) // _currentDir is null -> preference is going to get deleted - no big deal
			putInt(App.PREFERENCE_CURRENTTRACK, App.currentTrack)
			apply()
		}

		super.onPause()
	}

	override fun onBackPressed()
	{
		when
		{
			drawer_layout.isDrawerOpen(GravityCompat.START) -> drawer_layout.closeDrawer(GravityCompat.START)
			drawer_layout.isDrawerOpen(GravityCompat.END) -> drawer_layout.closeDrawer(GravityCompat.END)
			_searchResultsOpen ->
			{
				updateDirectoryView(_currentDir)
				_searchResultsOpen = false
			}
			_currentDir != null -> updateDirectoryView(_currentDir?.parentFile, _currentDir?.path)
			else -> super.onBackPressed() // exit app
		}
	}
}
