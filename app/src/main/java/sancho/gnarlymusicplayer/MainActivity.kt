package sancho.gnarlymusicplayer
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
import android.preference.PreferenceManager
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_seek.view.*
import sancho.gnarlymusicplayer.MediaPlaybackService.LocalBinder
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity()
{
	private lateinit var _mountedDevices: MutableList<File>

	private val _dirList = mutableListOf<File>()
	private var _prevDirList = mutableListOf<File>() // store "real" dir listing, for re-searching ability
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

		if (_accentColorIdx >= App.COLOR_RESOURCES.size) _accentColorIdx = 0
		setTheme(App.COLOR_RESOURCES[_accentColorIdx])

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
		val collectionType = object : TypeToken<Collection<Track>>() {}.type
		_bookmarks = gson.fromJson(bookmarksPref, collectionType)

		val queuePref = sharedPref.getString(App.PREFERENCE_QUEUE, "[]")
		App.queue = gson.fromJson(queuePref, collectionType)

		val lastDir = File(sharedPref.getString(App.PREFERENCE_LASTDIR, ""))
		if (lastDir.exists() && lastDir.isDirectory) _lastDir = lastDir

		_accentColorIdx = sharedPref.getInt(App.PREFERENCE_ACCENTCOLOR, 0)

		if (App.currentTrack == RecyclerView.NO_POSITION) // only on first time
			App.currentTrack = sharedPref.getInt(App.PREFERENCE_CURRENTTRACK, 0)

		@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // what's your problem kotlin?
		App.savedTrackPath = sharedPref.getString(App.PREFERENCE_SAVEDTRACK_PATH, "")
		App.savedTrackTime = sharedPref.getInt(App.PREFERENCE_SAVEDTRACK_TIME, 0)
	}

	override fun onSaveInstanceState(outState: Bundle?)
	{
		super.onSaveInstanceState(outState)
		outState?.putInt(App.BUNDLE_LASTSELECTEDTRACK, _lastSelectedTrack)
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
						addToQueue(Track(file.absolutePath, file.nameWithoutExtension))
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
							fileFromDir.name.isFileExtensionInArray(App.SUPPORTED_FILE_EXTENSIONS)
						}
						if (files != null)
						{
							Arrays.sort(files, App.filesComparator)
							addToQueue(files.map { track ->
								Track(track.absolutePath, track.nameWithoutExtension)
							})

							Toast.makeText(this, getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
						}
						else
							Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
					}
					else
					{
						addToQueue(Track(file.absolutePath, file.nameWithoutExtension))
						playTrack(App.queue.size - 1)
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

	private fun addToQueue(track: Track)
	{
		App.queue.add(track)
		_queueAdapter.notifyItemInserted(App.queue.size - 1)
		_queueChanged = true
	}

	private fun addToQueue(trackList: List<Track>)
	{
		App.queue.addAll(trackList)
		_queueAdapter.notifyItemRangeInserted(App.queue.size - trackList.size, trackList.size)
		_queueChanged = true
	}

	private fun playTrack(newPosition: Int)
	{
		if (App.mediaPlaybackServiceStarted && _service != null)
			_service?.saveTrackPosition()

		val oldPos = App.currentTrack
		_queueAdapter.notifyItemChanged(oldPos)
		App.currentTrack = newPosition
		_queueAdapter.notifyItemChanged(App.currentTrack)

		if (!App.mediaPlaybackServiceStarted || _service == null)
		{
			val intent = Intent(this, MediaPlaybackService::class.java) // excuse me, WHAT IN THE GODDAMN
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
			requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), App.REQUEST_READ_STORAGE)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if(requestCode == App.REQUEST_READ_STORAGE)
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
				file.isDirectory || file.name.isFileExtensionInArray(App.SUPPORTED_FILE_EXTENSIONS)
			}

			if (list != null)
			{
				Arrays.sort(list, App.filesAndDirsComparator)
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

	//region MENU

	// adds items to toolbar
	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)

		// search thing
		_actionSearch = menu.findItem(R.id.action_search)
		val actionCurrTrackInfo = menu.findItem(R.id.action_currenttrack_info)
		val actionSeek = menu.findItem(R.id.action_seek)
		val actionClearMenu = menu.findItem(R.id.action_menu_clear)
		val actionClearPrev = menu.findItem(R.id.action_clearprev)
		val actionClearAll = menu.findItem(R.id.action_clearall)
		val actionClearAfter = menu.findItem(R.id.action_clearafter)
		val actionSetColor = menu.findItem(R.id.action_setcolor)
		val actionAbout = menu.findItem(R.id.action_about)

		actionClearMenu.subMenu.clearHeader() // don't show header

		val searchThing = _actionSearch?.actionView as SearchView
		searchThing.queryHint = getString(R.string.search_bar_hint)
		searchThing.maxWidth = Int.MAX_VALUE

		_actionSearch?.setOnActionExpandListener(object: MenuItem.OnActionExpandListener
		{
			// SearchView.OnCloseListener simply doesn't work. THANKS ANDROID
			override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean
			{
				actionCurrTrackInfo.isVisible = true
				actionSeek.isVisible = true
				actionClearMenu.isVisible = true
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
				actionCurrTrackInfo.isVisible = false
				actionSeek.isVisible = false
				actionClearMenu.isVisible = false
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

				val queryButLower = query.toLowerCase(Locale.getDefault())

				// add results from current dir
				val list = _prevDirList.filter { file ->
					file.name.toLowerCase(Locale.getDefault()).contains(queryButLower)
				}.toMutableList()

				// add results from first level dirs
				for (dir in _prevDirList.filter{file -> file.isDirectory})
				{
					list.addAll(
						dir.listFiles{file ->
							(file.isDirectory || file.name.isFileExtensionInArray(App.SUPPORTED_FILE_EXTENSIONS))
							&& file.name.toLowerCase(Locale.getDefault()).contains(queryButLower)
						}
					)
				}

				list.sortWith(App.filesAndDirsComparator)
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
			R.id.action_currenttrack_info -> showCurrTrackInfo()
			R.id.action_seek -> showSeekDialog()
			R.id.action_clearprev -> clearPrev()
			R.id.action_clearall -> clearAll()
			R.id.action_clearafter -> clearAfter()
			R.id.action_setcolor -> selectAccent()
			R.id.action_about -> showAboutDialog()
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
			.setPositiveButton(getString(R.string.ok), null)
			.create()
			.show()
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

			override fun onStopTrackingTouch(seekbar: SeekBar?) {}
		})

		_seekDialog = AlertDialog.Builder(this)
			.setTitle(getString(R.string.seek_restore_position))
			.setView(seekView)
			.setPositiveButton(getString(R.string.seek)) { _, _ ->
				if (App.mediaPlaybackServiceStarted && _service != null)
				{
					try
					{
						_service?.seekAndPlay(seekView.seek_seekbar.progress)
					}
					catch(ex: NumberFormatException)
					{
						Toast.makeText(this, ex.message, Toast.LENGTH_SHORT).show()
					}
				}
				else
					Toast.makeText(this, getString(R.string.playback_service_not_running), Toast.LENGTH_SHORT).show()
			}
			.setNegativeButton(android.R.string.cancel, null)
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

	private fun selectAccent()
	{
		AlertDialog.Builder(this)
			.setTitle(getString(R.string.select_accent))
			.setItems(App.COLOR_NAMES){_, which ->
				_accentColorIdx = which
				recreate()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show()
	}

	private fun showAboutDialog()
	{
		AlertDialog.Builder(this)
			.setTitle(getString(R.string.about))
			.setMessage(getString(R.string.about_message))
			.setPositiveButton(getString(R.string.ok), null)
			.create()
			.show()
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
			putInt(App.PREFERENCE_ACCENTCOLOR, _accentColorIdx)
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
				updateDirectoryView(_currentDir, false)
				_searchResultsOpen = false
			}
			_currentDir != null -> updateDirectoryView(_currentDir?.parentFile, true)
			else -> super.onBackPressed() // exit app
		}
	}
}
