package sancho.gnarlymusicplayer
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import android.content.ComponentName
import sancho.gnarlymusicplayer.MediaPlaybackService.LocalBinder
import android.os.IBinder
import android.content.ServiceConnection
import android.preference.PreferenceManager
import android.widget.SeekBar
import sancho.gnarlymusicplayer.App.Companion.ACTION_START_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.App.Companion.BUNDLE_LASTSELECTEDTRACK
import sancho.gnarlymusicplayer.App.Companion.COLOR_NAMES
import sancho.gnarlymusicplayer.App.Companion.COLOR_RESOURCES
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_ACCENTCOLOR
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_BOOKMARKS
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_CURRENTTRACK
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_LASTDIR
import sancho.gnarlymusicplayer.App.Companion.PREFERENCE_QUEUE
import sancho.gnarlymusicplayer.App.Companion.REQUEST_READ_STORAGE
import sancho.gnarlymusicplayer.App.Companion.SUPPORTED_FILE_EXTENSIONS
import sancho.gnarlymusicplayer.App.Companion.app_filesAndDirsComparator
import sancho.gnarlymusicplayer.App.Companion.app_currentTrack
import sancho.gnarlymusicplayer.App.Companion.app_filesComparator
import sancho.gnarlymusicplayer.App.Companion.app_mediaPlaybackServiceStarted
import sancho.gnarlymusicplayer.App.Companion.app_queue
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter

class MainActivity : AppCompatActivity()
{
	private lateinit var _mountedDevices: MutableList<File>

	private val _dirList = mutableListOf<File>()
	private var _prevDirList = mutableListOf<File>()
	private var _currentDir: File? = null
	private var _lastDir: File? = null // from shared preferences

	private var _prevExplorerScrollPositions = Stack<Int>()
	private lateinit var _explorerAdapter: ExplorerAdapter

	private lateinit var _queueAdapter: QueueAdapter
	private var _queueChanged = false
	private var _lastSelectedTrack: Int = RecyclerView.NO_POSITION

	private lateinit var _bookmarks: MutableList<Track>
	private var _bookmarksChanged = false

	private var _accentColorIdx: Int = 0

	private var _actionSearch: MenuItem? = null

	private var _searchResultsOpen = false

	private var _service: MediaPlaybackService? = null
	private val _serviceConn = object : ServiceConnection
	{
		override fun onServiceConnected(className: ComponentName, service: IBinder)
		{
			_service = (service as LocalBinder).getService(object : BoundServiceListeners{
				override fun initSeekBar(max: Int)
				{
					initSeekBarAndLbl(max)
				}

				override fun updateSeekbar(pos: Int)
				{
					seek_bar.progress = pos
					seek_curr_time.text = pos.toString()
				}

				override fun updateQueueRecycler(oldPos: Int)
				{
					_queueAdapter.notifyItemChanged(oldPos)
					_queueAdapter.notifyItemChanged(app_currentTrack)
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

		if (_accentColorIdx >= COLOR_RESOURCES.size) _accentColorIdx = 0
		setTheme(COLOR_RESOURCES[_accentColorIdx])

		if(savedInstanceState != null)
			_lastSelectedTrack = savedInstanceState.getInt(BUNDLE_LASTSELECTEDTRACK, RecyclerView.NO_POSITION)

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		title = ""

		getStorageDevices() // prepare list with storage devices

		setupBookmarks()

		setupQueue()

		setupFileList()

		setupSeekBar()

		requestReadPermishon() // check for permissions and initial update of file list
	}

	override fun onResume()
	{
		super.onResume()

		if(app_mediaPlaybackServiceStarted)
			bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)

		if (app_currentTrack != _lastSelectedTrack)
		{
			_queueAdapter.notifyItemChanged(_lastSelectedTrack)
			_queueAdapter.notifyItemChanged(app_currentTrack)
		}
	}

	private fun restoreFromPrefs()
	{
		val gson = Gson()
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

		val bookmarksPref = sharedPref.getString(PREFERENCE_BOOKMARKS, "[]")
		val collectionType = object : TypeToken<Collection<Track>>() {}.type
		_bookmarks = gson.fromJson(bookmarksPref, collectionType)

		val queuePref = sharedPref.getString(PREFERENCE_QUEUE, "[]")
		app_queue = gson.fromJson(queuePref, collectionType)

		val lastDir = File(sharedPref.getString(PREFERENCE_LASTDIR, ""))
		if (lastDir.exists() && lastDir.isDirectory) _lastDir = lastDir

		_accentColorIdx = sharedPref.getInt(PREFERENCE_ACCENTCOLOR, 0)

		if (app_currentTrack == RecyclerView.NO_POSITION) // only on first time
			app_currentTrack = sharedPref.getInt(PREFERENCE_CURRENTTRACK, 0)
	}

	override fun onSaveInstanceState(outState: Bundle?)
	{
		super.onSaveInstanceState(outState)
		outState?.putInt(BUNDLE_LASTSELECTEDTRACK, _lastSelectedTrack)
	}

	private fun setupFileList()
	{
		library_list_view.layoutManager = LinearLayoutManager(this)

		_explorerAdapter = ExplorerAdapter(this, _dirList,
			{ file, pos ->
				if (file.exists())
				{
					if (file.isDirectory)
					{
						// navigate to directory

						updateDirectoryView(file, false)
						_prevExplorerScrollPositions.push(pos)
						_searchResultsOpen = false // in case the dir was from search results
					}
					else
					{
						addToQueue(Track(file.absolutePath, file.name))
					}
				}
				else
					Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
			},
			{ file ->
				if (file.exists())
				{
					if (file.isDirectory)
					{
						// add all tracks in dir (not recursive)
						val files = file.listFiles{fileFromDir ->
							fileFromDir.name.isFileExtensionInArray(SUPPORTED_FILE_EXTENSIONS)
						}
						if (files != null)
						{
							Arrays.sort(files, app_filesComparator)
							addToQueue(files.map { track ->
								Track(track.absolutePath, track.name)
							})

							Toast.makeText(this, getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
						}
						else
							Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
					}
					else
					{
						addToQueue(Track(file.absolutePath, file.name))
						playTrack(app_queue.size - 1)
					}
				}
				else
					Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()

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
				_prevExplorerScrollPositions.clear()
				updateDirectoryView(dir, false)
				drawer_layout.closeDrawer(GravityCompat.END)
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
				adapter.onItemAdded(Track(path, label))
				_bookmarksChanged = true
				adapter.notifyItemInserted(_bookmarks.size - 1)
			}
			else
				Toast.makeText(applicationContext, getString(R.string.cant_add_root_dir), Toast.LENGTH_SHORT).show()
		}

		bookmark_root.setOnClickListener {
			_prevExplorerScrollPositions.clear()
			updateDirectoryView(null, false)
			drawer_layout.closeDrawer(GravityCompat.END)
		}
	}

	private fun setupQueue()
	{
		queue_list_view.layoutManager = LinearLayoutManager(this)
		_queueAdapter = QueueAdapter(this, app_queue) { position ->
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

				if (fromPosition == app_currentTrack)
				{
					app_currentTrack = toPosition
				}
				else if (toPosition == app_currentTrack)
				{
					if (fromPosition < app_currentTrack)
						app_currentTrack--
					else if (fromPosition > app_currentTrack)
						app_currentTrack++
				}

				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val position = viewHolder.adapterPosition
				app_queue.removeAt(position)
				_queueChanged = true
				_queueAdapter.notifyItemRemoved(position)

				if (position < app_currentTrack)
				{
					app_currentTrack--
				}
				else if (position == app_currentTrack)
				{
					// we've removed currently selected track
					// select next track and notify service (it'll know if it needs to be played)
					when
					{
						position < app_queue.size ->
						{
							// removed track wasn't last - select next track in queue
							// no need to change currentTrack
							if (app_mediaPlaybackServiceStarted && _service != null)
								_service?.setTrack(false)

							_queueAdapter.notifyItemChanged(app_currentTrack)
						}
						app_queue.size > 0 ->
						{
							// removed track was last - select first track in queue (if any tracks exist)
							app_currentTrack = 0

							if (app_mediaPlaybackServiceStarted && _service != null)
							_service?.setTrack(false)

							_queueAdapter.notifyItemChanged(app_currentTrack)
						}
						else ->
						{
							// no other track available
							if (app_mediaPlaybackServiceStarted && _service != null)
								_service?.end(false)

							app_currentTrack = RecyclerView.NO_POSITION
						}
					}
				}
			}
		})
		_queueAdapter.touchHelper = touchHelper
		touchHelper.attachToRecyclerView(queue_list_view)
	}

	private fun setupSeekBar()
	{
		seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener
		{
			override fun onStartTrackingTouch(seekbar: SeekBar?)
			{
				// TODO disallow programatically changing progress
			}

			override fun onStopTrackingTouch(seekbar: SeekBar?)
			{
				// TODO allow programatically changing progress
			}

			override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean)
			{
				seek_curr_time.text = progress.toMinuteSecondString()
			}
		})
	}

	private fun addToQueue(track: Track)
	{
		app_queue.add(track)
		_queueAdapter.notifyItemInserted(app_queue.size - 1)
		_queueChanged = true
	}

	private fun addToQueue(trackList: List<Track>)
	{
		app_queue.addAll(trackList)
		_queueAdapter.notifyItemRangeInserted(app_queue.size - trackList.size, trackList.size)
		_queueChanged = true
	}

	private fun playTrack(newPosition: Int)
	{
		val oldPos = app_currentTrack
		_queueAdapter.notifyItemChanged(oldPos)
		app_currentTrack = newPosition
		_queueAdapter.notifyItemChanged(app_currentTrack)

		if (!app_mediaPlaybackServiceStarted || _service == null)
		{
			val intent = Intent(this, MediaPlaybackService::class.java) // excuse me, WHAT IN THE GODDAMN
			intent.action = ACTION_START_PLAYBACK_SERVICE
			startService(intent)

			bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)

			initSeekBarAndLbl(_service?.getTotalDuration() ?: 0)
		}
		else
		{
			if (oldPos == newPosition)
				_service?.playPause()
			else
				_service?.setTrack(true)
		}
	}

	private fun initSeekBarAndLbl(max: Int)
	{
		seek_bar.max = max
		seek_total_time.text = max.toMinuteSecondString()
	}

	private fun getStorageDevices()
	{
		_mountedDevices = mutableListOf()
		val externalStorageFiles = getExternalFilesDirs(null)
		for(f in externalStorageFiles)
		{
			val device = f.parentFile.parentFile.parentFile.parentFile // srsl?
			_mountedDevices.add(device)
		}
	}

	private fun requestReadPermishon()
	{
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			updateDirectoryView(_lastDir, false)
		}
		else
		{
			requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_STORAGE)
		}
	}

	// adds items to toolbar
	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)

		// search thing
		_actionSearch = menu.findItem(R.id.action_search)
		val actionClearPrev = menu.findItem(R.id.action_clearprev)
		val actionClearAll = menu.findItem(R.id.action_clearall)
		val actionClearAfter = menu.findItem(R.id.action_clearafter)
		val actionSetColor = menu.findItem(R.id.action_setcolor)
		val actionAbout = menu.findItem(R.id.action_about)

		val searchThing = _actionSearch?.actionView as SearchView
		searchThing.queryHint = getString(R.string.search_bar_hint)
		searchThing.maxWidth = Int.MAX_VALUE

		_actionSearch?.setOnActionExpandListener(object: MenuItem.OnActionExpandListener
		{
			// SearchView.OnCloseListener simply doesn't work. THANKS ANDROID
			override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean
			{
				actionClearPrev.isVisible = true
				actionClearAll.isVisible = true
				actionClearAfter.isVisible = true
				actionSetColor.isVisible = true
				actionAbout.isVisible = true
				updateDirectoryView(_currentDir, true)
				_searchResultsOpen = false
				return true
			}

			override fun onMenuItemActionExpand(p0: MenuItem?): Boolean
			{
				actionClearPrev.isVisible = false
				actionClearAll.isVisible = false
				actionClearAfter.isVisible = false
				actionSetColor.isVisible = false
				actionAbout.isVisible = false
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

				val queryButLower = query.toLowerCase()
				val list = _prevDirList.filter { file ->
					file.name.toLowerCase().contains(queryButLower)
				}.toMutableList()

				for (dir in _prevDirList.filter{file -> file.isDirectory})
				{
					list.addAll(
						dir.listFiles{file ->
							(file.isDirectory || file.name.isFileExtensionInArray(SUPPORTED_FILE_EXTENSIONS))
							&& file.name.toLowerCase().contains(queryButLower)
						}
					)
				}

				list.sortWith(app_filesAndDirsComparator)
				_dirList.clear()
				_dirList.addAll(list)
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
			R.id.action_clearprev ->
			{
				if (app_currentTrack != RecyclerView.NO_POSITION && app_currentTrack > 0)
				{
					// there are items to clear at the start

					Toast.makeText(this, getString(R.string.cleared_n_tracks, app_currentTrack), Toast.LENGTH_SHORT).show()

					for (i in 0 until app_currentTrack)
						app_queue.removeAt(0)

					val removedCnt = app_currentTrack
					app_currentTrack = 0
					_queueAdapter.notifyItemRangeRemoved(0, removedCnt)
					_queueChanged = true
				}
			}
			R.id.action_clearall ->
			{
				if (app_queue.size > 0)
				{
					Toast.makeText(this, getString(R.string.cleared_n_tracks, app_queue.size), Toast.LENGTH_SHORT).show()

					if (app_mediaPlaybackServiceStarted && _service != null)
						_service?.end(false)

					val removedCnt = app_queue.size
					app_queue.clear()

					app_currentTrack = RecyclerView.NO_POSITION
					_queueAdapter.notifyItemRangeRemoved(0, removedCnt)
					_queueChanged = true
				}
			}
			R.id.action_clearafter ->
			{
				if (app_currentTrack != RecyclerView.NO_POSITION && app_currentTrack < app_queue.size - 1)
				{
					// there are items to clear at the end

					Toast.makeText(this, getString(R.string.cleared_n_tracks, app_queue.size - 1 - app_currentTrack), Toast.LENGTH_SHORT).show()

					val removedCnt = app_queue.size - app_currentTrack
					val removedFromIdx = app_currentTrack + 1
					for (i in app_queue.size - 1 downTo app_currentTrack + 1)
						app_queue.removeAt(i)

					app_currentTrack = app_queue.size - 1
					_queueAdapter.notifyItemRangeRemoved(removedFromIdx, removedCnt)
					_queueChanged = true
				}
			}
			R.id.action_setcolor -> selectAccent()
			R.id.action_about ->
			{
				AlertDialog.Builder(this)
					.setTitle(getString(R.string.about))
					.setMessage(getString(R.string.about_message))
					.setPositiveButton(getString(R.string.ok), null)
					.create()
					.show()
			}
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if(requestCode == REQUEST_READ_STORAGE)
		{
			if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			{
				updateDirectoryView(_lastDir, false)
			}
			else
			{
				requestReadPermishon()
			}
		}
	}

	// assuming the read permission is granted
	private fun updateDirectoryView(newDir: File?, restoreScroll: Boolean)
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
				file.isDirectory || file.name.isFileExtensionInArray(SUPPORTED_FILE_EXTENSIONS)
			}

			if (list != null)
			{
				Arrays.sort(list, app_filesAndDirsComparator)
				_dirList.clear()
				_dirList.addAll(list)
				_explorerAdapter.notifyDataSetChanged()
			}
			else
				Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}
		if (restoreScroll && !_prevExplorerScrollPositions.empty())
		{
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(_prevExplorerScrollPositions.pop(), 200)
		}
		else
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
	}

	private fun selectAccent()
	{
		AlertDialog.Builder(this)
			.setTitle(getString(R.string.select_accent))
			.setItems(COLOR_NAMES){_, which ->
				_accentColorIdx = which
				recreate()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show()
	}

	override fun onPause()
	{
		// unbind service
		if(app_mediaPlaybackServiceStarted && _service != null)
			unbindService(_serviceConn)

		_lastSelectedTrack = app_currentTrack

		// collapse searchbar thing
		_actionSearch?.collapseActionView()

		// save to shared prefs
		with(PreferenceManager.getDefaultSharedPreferences(this).edit())
		{
			if(_bookmarksChanged || _queueChanged)
			{
				val gson = Gson()
				if(_bookmarksChanged)
					putString(PREFERENCE_BOOKMARKS, gson.toJson(_bookmarks))

				if(_queueChanged)
					putString(PREFERENCE_QUEUE, gson.toJson(app_queue))
			}
			putString(PREFERENCE_LASTDIR, _currentDir?.absolutePath) // _currentDir is null -> preference is going to get deleted - no big deal
			putInt(PREFERENCE_ACCENTCOLOR, _accentColorIdx)
			putInt(PREFERENCE_CURRENTTRACK, app_currentTrack)
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
				updateDirectoryView(_currentDir, false)
				_searchResultsOpen = false
			}
			_currentDir != null -> updateDirectoryView(_currentDir?.parentFile, true)
			else -> super.onBackPressed() // exit app
		}
	}
}
