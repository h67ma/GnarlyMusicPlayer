package sancho.gnarlymusicplayer.activities
import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_seek.view.*
import sancho.gnarlymusicplayer.*
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesAndDirsComparator
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesComparator
import sancho.gnarlymusicplayer.comparators.FilesComparator
import sancho.gnarlymusicplayer.models.ExplorerHeader
import sancho.gnarlymusicplayer.models.ExplorerItem
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.QueueItem
import sancho.gnarlymusicplayer.playbackservice.ACTION_START_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.playbackservice.BoundServiceListeners
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File
import java.util.*

private const val REQUEST_READ_STORAGE = 42
private const val INTENT_LAUNCH_FOR_RESULT_SETTINGS = 1613
private const val BUNDLE_LASTSELECTEDTRACK = "sancho.gnarlymusicplayer.bundle.lastselectedtrack"

class MainActivity : AppCompatActivity()
{
	private lateinit var _mountedDevices: MutableList<ExplorerViewItem>

	private val _dirList = mutableListOf<ExplorerViewItem>()
	private var _prevDirList = mutableListOf<ExplorerViewItem>() // store "real" dir listing, for re-searching ability. because _dirList contains search results
	private var _currentExplorerPath: File? = null

	private lateinit var _explorerAdapter: ExplorerAdapter

	private lateinit var _queueAdapter: QueueAdapter
	private var _lastSelectedTrack: Int = RecyclerView.NO_POSITION

	private lateinit var _bookmarks: MutableList<QueueItem>
	private var _bookmarksChanged = false

	private var _actionSearch: MenuItem? = null

	private var _searchResultsOpen = false

	private var _seekDialog: AlertDialog? = null

	private var _service: MediaPlaybackService? = null
	private lateinit var _serviceConn: ServiceConnection

	override fun onCreate(savedInstanceState: Bundle?)
	{
		AppSettingsManager.restoreFromPrefs(this)
		_bookmarks = AppSettingsManager.restoreBookmarks(this)
		_bookmarksChanged = false

		setTheme(AppSettingsManager.getStyleFromPreference())

		if(savedInstanceState != null)
			_lastSelectedTrack = savedInstanceState.getInt(BUNDLE_LASTSELECTEDTRACK, RecyclerView.NO_POSITION)

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		title = ""

		setupServiceConnection()

		getStorageDevices() // prepare list with storage devices

		setupBookmarks()

		setupQueue()

		setupFileList()

		requestReadPermishon() // check for permissions and initial update of file list
	}

	override fun onResume()
	{
		super.onResume()

		_queueAdapter.notifyDataSetChanged() // in case service modified queue and we didn't go through onCreate() (e.g. screen off/on)

		if(MediaPlaybackService.mediaPlaybackServiceStarted)
			bindService()

		if (PlaybackQueue.currentIdx != _lastSelectedTrack)
		{
			_queueAdapter.notifyItemChanged(_lastSelectedTrack)
			_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx)
		}
	}



	override fun onSaveInstanceState(outState: Bundle)
	{
		super.onSaveInstanceState(outState)
		outState.putInt(BUNDLE_LASTSELECTEDTRACK, _lastSelectedTrack)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
	{
		super.onActivityResult(requestCode, resultCode, data)

		if (requestCode == INTENT_LAUNCH_FOR_RESULT_SETTINGS)
			recreate() // must call onCreate after changing theme
	}

	private fun setupFileList()
	{
		library_list_view.layoutManager = LinearLayoutManager(this)

		_explorerAdapter = ExplorerAdapter(this, _dirList,
			{ item ->
				val file = File(item.path)

				if (!file.exists())
				{
					Toast.makeText(applicationContext, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
					return@ExplorerAdapter
				}

				if (file.isDirectory)
				{
					// navigate to directory
					updateDirectoryViewDir(file)
					scrollToTop()
					_searchResultsOpen = false // in case the dir was from search results
				}
				else if (Helpers.isFileSupportedAndPlaylist(file.path))
				{
					// open playlist
					updateDirectoryViewPlaylist(file)
					scrollToTop()
					_searchResultsOpen = false // in case the playlist was from search results
				}
				else
				{
					// audio file
					addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
				}
			},
			{ item ->
				val file = File(item.path)

				if (!file.exists())
				{
					Toast.makeText(applicationContext, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
					return@ExplorerAdapter
				}

				if (file.isDirectory)
				{
					// add all tracks in dir (not recursive)
					val files = Helpers.listDir(file, true)

					if (files != null)
					{
						files.sortWith(FilesComparator())
						addToQueue(files.map { track ->
							QueueItem(track.absolutePath, track.nameWithoutExtension)
						})

						Toast.makeText(this, getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
					}
					else
						Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
				}
				else if (Helpers.isFileSupportedAndPlaylist(file.path))
				{
					// add all tracks in playlist (not recursive)
					val files = Helpers.listPlaylist(file)

					if (files != null)
					{
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
					// audio file
					addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
					playTrack(PlaybackQueue.lastIdx)
				}
			}
		)
		library_list_view.adapter = _explorerAdapter
	}

	private fun setupBookmarks()
	{
		bookmark_list_view.layoutManager = LinearLayoutManager(this)
		val adapter = BookmarksAdapter(this, _bookmarks) { bookmark ->

			if (bookmark.path == _currentExplorerPath?.absolutePath)
			{
				drawer_layout.closeDrawer(GravityCompat.END)
				return@BookmarksAdapter // already open
			}

			val item = File(bookmark.path)
			if (item.exists())
			{
				updateDirectoryView(item)
				scrollToTop()

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
			val path = _currentExplorerPath?.absolutePath
			val label = _currentExplorerPath?.name
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
			updateDirectoryViewShowStorage()
			drawer_layout.closeDrawer(GravityCompat.END)
		}
	}

	private fun setupQueue()
	{
		queue_list_view.layoutManager = LinearLayoutManager(this)
		_queueAdapter = QueueAdapter(this) { position ->
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
				PlaybackQueue.updateIdxAfterItemMoved(fromPosition, toPosition)

				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val removedPos = viewHolder.adapterPosition
				val removedCurrent = removedPos == PlaybackQueue.currentIdx
				_queueAdapter.notifyItemRemoved(removedPos)
				if (PlaybackQueue.removeAt(removedPos))
				{
					// no other track available, stop service
					if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
						_service?.end(false)
				}
				else if (removedCurrent)
				{
					// we've removed currently selected track -> select next track and notify service

					if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
						_service?.setTrack(false)

					_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx)
				}
			}
		})
		_queueAdapter.touchHelper = touchHelper
		touchHelper.attachToRecyclerView(queue_list_view)
	}

	private fun addToQueue(queueItem: QueueItem)
	{
		PlaybackQueue.add(queueItem)
		_queueAdapter.notifyItemInserted(PlaybackQueue.lastIdx)
	}

	private fun addToQueue(trackList: List<QueueItem>)
	{
		PlaybackQueue.add(trackList)
		_queueAdapter.notifyItemRangeInserted(PlaybackQueue.size - trackList.size, trackList.size)
	}

	private fun playTrack(newPosition: Int)
	{
		if (!PlaybackQueue.trackExists(newPosition))
		{
			Toast.makeText(this, getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
			return
		}

		if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
			_service?.saveTrackPosition()

		val oldPos = PlaybackQueue.currentIdx
		_queueAdapter.notifyItemChanged(oldPos)
		PlaybackQueue.currentIdx = newPosition
		_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx)

		if (!MediaPlaybackService.mediaPlaybackServiceStarted || _service == null)
		{
			val intent = Intent(this, MediaPlaybackService::class.java)
			intent.action = ACTION_START_PLAYBACK_SERVICE
			startService(intent)

			bindService()
		}
		else
		{
			if (oldPos == newPosition)
				_service?.playPause()
			else
				_service?.setTrack(true)
		}
	}

	private fun setupServiceConnection()
	{
		_serviceConn = object : ServiceConnection
		{
			override fun onServiceConnected(className: ComponentName, service: IBinder)
			{
				_service = (service as MediaPlaybackService.LocalBinder).getService(object : BoundServiceListeners
				{
					override fun onTrackChanged(oldPos: Int, trackFinished: Boolean)
					{
						if (trackFinished && PlaybackQueue.autoClean)
							_queueAdapter.notifyItemRemoved(oldPos) // track just got autoremoved
						else
							_queueAdapter.notifyItemChanged(oldPos) // unselect track

						_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx) // select new current track

						if (_seekDialog?.isShowing == true)
							_seekDialog?.dismiss()
					}

					override fun onEnd()
					{
						if (_seekDialog?.isShowing == true)
							_seekDialog?.dismiss()

						unbindService()
					}
				})
			}

			override fun onServiceDisconnected(className: ComponentName)
			{
				_service = null
			}
		}
	}

	private fun getStorageDevices()
	{
		_mountedDevices = mutableListOf()
		val externalStorageFiles = getExternalFilesDirs(null)
		for(f in externalStorageFiles)
		{
			val device = f?.parentFile?.parentFile?.parentFile?.parentFile // srsl?
			_mountedDevices.add(ExplorerItem(device?.path ?: "", device?.name ?: "", device?.isDirectory ?: false))
		}
	}

	private fun requestReadPermishon()
	{
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			updateDirectoryView(AppSettingsManager.lastDir)
		}
		else
		{
			requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_STORAGE)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if(requestCode == REQUEST_READ_STORAGE)
		{
			if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			{
				updateDirectoryView(AppSettingsManager.lastDir)
			}
			else
			{
				requestReadPermishon()
			}
		}
	}

	private fun updateDirectoryViewShowStorage()
	{
		_currentExplorerPath = null
		toolbar_title.text = getString(R.string.storage_devices)
		_dirList.clear()
		_dirList.addAll(_mountedDevices)
		_explorerAdapter.notifyDataSetChanged()
	}

	private fun updateDirectoryView(newPath: File?)
	{
		if(newPath == null || !newPath.exists())
		{
			updateDirectoryViewShowStorage()
			return
		}

		if (newPath.isDirectory)
			updateDirectoryViewDir(newPath)
		else
			updateDirectoryViewPlaylist(newPath)
	}

	private fun updateDirectoryViewDir(newPath: File?)
	{
		_currentExplorerPath = newPath

		if(newPath == null || !newPath.exists())
		{
			updateDirectoryViewShowStorage()
			return
		}

		toolbar_title.text = newPath.absolutePath

		val list = Helpers.listDir(newPath, false)

		if (list != null)
		{
			val viewList = list.map{file -> ExplorerItem(file.path, file.name, file.isDirectory)}.toMutableList()
			viewList.sortWith(ExplorerViewFilesAndDirsComparator())
			_dirList.clear()
			_dirList.addAll(viewList)
			_explorerAdapter.notifyDataSetChanged()
		}
		else
			Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
	}

	private fun restoreListScrollPos(oldPath: String?)
	{
		if (oldPath != null)
		{
			// find previous dir (child) in current dir list, scroll to it
			val pos = _dirList.indexOf(ExplorerItem(oldPath, "", true))
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 200)
		}
		else
			scrollToTop()
	}

	private fun scrollToTop()
	{
		(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
	}

	private fun updateDirectoryViewPlaylist(newPath: File?)
	{
		_currentExplorerPath = newPath

		if(newPath == null || !newPath.exists())
		{
			updateDirectoryViewShowStorage()
			return
		}

		toolbar_title.text = newPath.absolutePath

		val list = Helpers.listPlaylist(newPath)

		if (list != null)
		{
			val viewList = list.map{file -> ExplorerItem(file.path, file.name, file.isDirectory)}.toMutableList()
			_dirList.clear()
			_dirList.addAll(viewList)
			_explorerAdapter.notifyDataSetChanged()
		}
		else
			Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
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

				updateDirectoryView(_currentExplorerPath)
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
					.sortedWith(ExplorerViewFilesComparator())
				)

				// add results from first level dirs (grouped by subdir name)
				for (elem in _prevDirList.filter{file -> file.isDirectory}.sortedWith(ExplorerViewFilesComparator()))
				{
					val dir = File(elem.path)

					val results = dir
						.listFiles{file ->
							(file.isDirectory || Helpers.isFileSupported(file.name))
									&& file.name.toLowerCase(Locale.getDefault()).contains(queryButLower)
						}
						?.map{file -> ExplorerItem(file.path, file.name, file.isDirectory) }
						?.sortedWith(ExplorerViewFilesComparator())

					if (results?.isNotEmpty() == true)
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
		TagExtractor.showCurrTrackInfo(this)
	}

	private fun gotoCurrentTrackDir()
	{
		if (!PlaybackQueue.trackSelected())
		{
			Toast.makeText(this, getString(R.string.no_track_selected), Toast.LENGTH_SHORT).show()
			return
		}

		val dir = PlaybackQueue.getCurrentTrackDir()
		if (dir != null)
			updateDirectoryViewDir(dir)
		else
			Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
	}

	private fun showSeekDialog()
	{
		if (!MediaPlaybackService.mediaPlaybackServiceStarted || _service == null)
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

		val savedTrack = File(AppSettingsManager.savedTrackPath)
		if (savedTrack.exists() && PlaybackQueue.getCurrentTrackPath() == AppSettingsManager.savedTrackPath)
		{
			seekView.seek_loadbtn.visibility = View.VISIBLE
			seekView.seek_loadbtn.setOnClickListener{
				seekView.seek_seekbar.progress = AppSettingsManager.savedTrackTime
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
				if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
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
		val clearedCnt = PlaybackQueue.removeBeforeCurrent()
		if (clearedCnt > 0)
		{
			_queueAdapter.notifyItemRangeRemoved(0, clearedCnt)
			Toast.makeText(this, getString(R.string.cleared_n_tracks, clearedCnt), Toast.LENGTH_SHORT).show()
		}
	}

	private fun clearAll()
	{
		if (PlaybackQueue.size > 0)
		{
			// gotta stop service before removing all
			if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
				_service?.end(false)

			val clearedCnt = PlaybackQueue.removeAll()

			_queueAdapter.notifyItemRangeRemoved(0, clearedCnt)
			Toast.makeText(this, getString(R.string.cleared_n_tracks, clearedCnt), Toast.LENGTH_SHORT).show()
		}
	}

	private fun clearAfter()
	{
		val removedFromIdx = PlaybackQueue.currentIdx + 1
		val clearedCnt = PlaybackQueue.removeAfterCurrent()
		if (clearedCnt > 0)
		{
			_queueAdapter.notifyItemRangeRemoved(removedFromIdx, clearedCnt)
			Toast.makeText(this, getString(R.string.cleared_n_tracks, clearedCnt), Toast.LENGTH_SHORT).show()
		}
	}

	private fun launchSettings()
	{
		startActivityForResult(Intent(this, SettingsActivity::class.java), INTENT_LAUNCH_FOR_RESULT_SETTINGS)
	}

	//endregion

	private fun bindService()
	{
		if (!MediaPlaybackService.serviceBound)
		{
			val success = bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)
			if (success)
				MediaPlaybackService.serviceBound = true
		}
	}

	private fun unbindService()
	{
		if(MediaPlaybackService.serviceBound)
		{
			unbindService(_serviceConn)
			_service = null
			MediaPlaybackService.serviceBound = false
		}
	}

	override fun onPause()
	{
		unbindService()

		_lastSelectedTrack = PlaybackQueue.currentIdx

		_actionSearch?.collapseActionView() // collapse searchbar thing

		AppSettingsManager.saveToPrefs(this, _bookmarksChanged, _currentExplorerPath?.absolutePath, _bookmarks)

		super.onPause()
	}

	override fun onBackPressed()
	{
		if (drawer_layout.isDrawerOpen(GravityCompat.START))
			drawer_layout.closeDrawer(GravityCompat.START)
		else if (drawer_layout.isDrawerOpen(GravityCompat.END))
			drawer_layout.closeDrawer(GravityCompat.END)
		else if (_searchResultsOpen)
		{
			updateDirectoryView(_currentExplorerPath)
			scrollToTop()
			_searchResultsOpen = false
		}
		else if (_currentExplorerPath != null)
		{
			val oldPath = _currentExplorerPath?.path
			updateDirectoryViewDir(_currentExplorerPath?.parentFile)
			restoreListScrollPos(oldPath)
		}
		else
			super.onBackPressed() // exit app
	}
}
