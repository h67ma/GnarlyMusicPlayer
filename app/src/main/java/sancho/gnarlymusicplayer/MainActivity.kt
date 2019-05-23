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
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter

var currentTrack: Int = RecyclerView.NO_POSITION
lateinit var queue: MutableList<Track>

class MainActivity : AppCompatActivity()
{
	private lateinit var _mountedDevices: MutableList<File>

	private val _dirList = mutableListOf<File>()
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

	private var _spamToast: Toast? = null

	private var _optionsMenu: Menu? = null
	private var _addToTop: Boolean = false

	private var _service: MediaPlaybackService? = null
	private val _serviceConn = object : ServiceConnection
	{
		override fun onServiceConnected(className: ComponentName, service: IBinder)
		{
			_service = (service as LocalBinder).getService(object : BoundServiceListeners{
				override fun updateQueueRecycler(oldPos: Int)
				{
					_queueAdapter.notifyItemChanged(oldPos)
					_queueAdapter.notifyItemChanged(currentTrack)
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

		requestReadPermishon() // check for permissions and initial update of file list
	}

	override fun onResume()
	{
		super.onResume()

		if(mediaPlaybackServiceStarted)
			bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)

		if (currentTrack != _lastSelectedTrack)
		{
			_queueAdapter.notifyItemChanged(_lastSelectedTrack)
			_queueAdapter.notifyItemChanged(currentTrack)
		}
	}

	private fun restoreFromPrefs()
	{
		val gson = Gson()
		val sharedPref = getPreferences(Context.MODE_PRIVATE)

		val bookmarksPref = sharedPref.getString(PREFERENCE_BOOKMARKS, "[]")
		val collectionType = object : TypeToken<Collection<Track>>() {}.type
		_bookmarks = gson.fromJson(bookmarksPref, collectionType)

		val queuePref = sharedPref.getString(PREFERENCE_QUEUE, "[]")
		queue = gson.fromJson(queuePref, collectionType)

		val lastDir = File(sharedPref.getString(PREFERENCE_LASTDIR, ""))
		if (lastDir.exists() && lastDir.isDirectory) _lastDir = lastDir

		_accentColorIdx = sharedPref.getInt(PREFERENCE_ACCENTCOLOR, 0)

		if (currentTrack == RecyclerView.NO_POSITION) // only on first time
			currentTrack = sharedPref.getInt(PREFERENCE_CURRENTTRACK, 0)
	}

	override fun onSaveInstanceState(outState: Bundle?)
	{
		super.onSaveInstanceState(outState)
		outState?.putInt(BUNDLE_LASTSELECTEDTRACK, _lastSelectedTrack)
	}

	private fun setupFileList()
	{
		library_list_view.layoutManager = LinearLayoutManager(this)

		_explorerAdapter = ExplorerAdapter(this, _dirList) { file, pos ->
			if (file.exists())
			{
				if (file.isDirectory)
				{
					updateDirectoryView(file, false)
					_prevExplorerScrollPositions.push(pos)
				}
				else
				{
					if (!_addToTop)
					{
						queue.add(Track(file.absolutePath, file.name))
						_queueAdapter.notifyItemInserted(queue.size - 1)
					}
					else
					{
						queue.add(0, Track(file.absolutePath, file.name))
						_queueAdapter.notifyItemInserted(0)
						if (currentTrack >= 0) currentTrack++
					}
					_queueChanged = true

					_spamToast?.cancel()
					_spamToast = Toast.makeText(this, getString(R.string.added_to_queue, file.name), Toast.LENGTH_SHORT)
					_spamToast?.show()
				}
			}
			else
				Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
		}
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
		_queueAdapter = QueueAdapter(this, queue) { position ->
			// play track

			val oldPos = currentTrack
			_queueAdapter.notifyItemChanged(oldPos)
			currentTrack = position
			_queueAdapter.notifyItemChanged(currentTrack)

			if (!mediaPlaybackServiceStarted || _service == null)
			{
				val intent = Intent(this, MediaPlaybackService::class.java) // excuse me, WHAT IN THE GODDAMN
				intent.action = ACTION_START_PLAYBACK_SERVICE
				startService(intent)

				bindService(Intent(this, MediaPlaybackService::class.java), _serviceConn, Context.BIND_AUTO_CREATE)
			}
			else
			{
				if (oldPos == position)
					_service?.playPause()
				else
					_service?.playTrack(true)
			}
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

				if (fromPosition == currentTrack)
				{
					currentTrack = toPosition
				}
				else if (toPosition == currentTrack)
				{
					if (fromPosition < currentTrack)
						currentTrack--
					else if (fromPosition > currentTrack)
						currentTrack++
				}

				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val position = viewHolder.adapterPosition
				queue.removeAt(position)
				_queueChanged = true
				_queueAdapter.notifyItemRemoved(position)

				if (position < currentTrack)
				{
					currentTrack--
				}
				else if (position == currentTrack)
				{
					// we've removed currently selected track
					// select next track and notify service (it'll know if it needs to be played)
					when
					{
						position < queue.size ->
						{
							// removed track wasn't last - select next track in queue
							// no need to change currentTrack
							if (mediaPlaybackServiceStarted && _service != null)
								_service?.playTrack(false)

							_queueAdapter.notifyItemChanged(currentTrack)
						}
						queue.size > 0 ->
						{
							// removed track was last - select first track in queue (if any tracks exist)
							currentTrack = 0

							if (mediaPlaybackServiceStarted && _service != null)
							_service?.playTrack(false)

							_queueAdapter.notifyItemChanged(currentTrack)
						}
						else ->
						{
							// no other track available
							if (mediaPlaybackServiceStarted && _service != null)
								_service?.end()

							currentTrack = RecyclerView.NO_POSITION
						}
					}
				}
			}
		})
		_queueAdapter.touchHelper = touchHelper
		touchHelper.attachToRecyclerView(queue_list_view)
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
		_optionsMenu = menu
		menuInflater.inflate(R.menu.main, menu)

		// don't show "clear queue" header
		menu.findItem(R.id.menu_clearqueue)?.subMenu?.clearHeader()

		// search thing
		val mSearch = menu.findItem(R.id.action_search)

		val mSearchView = mSearch.actionView as SearchView
		mSearchView.queryHint = getString(R.string.search_bar_hint)

		mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener
		{
			override fun onQueryTextSubmit(query: String): Boolean
			{
				// TODO filter
				return true // don't do default action
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
			// TODO R.id.action_savequeuetoplaylist ->
			R.id.action_addtopbottom ->
			{
				_addToTop = !_addToTop
				_optionsMenu?.getItem(1)?.run{
					setIcon(if (_addToTop) R.drawable.to_top else R.drawable.to_bottom)
					setChecked(_addToTop)
				}
			}
			R.id.action_clearprev ->
			{
				if (currentTrack != RecyclerView.NO_POSITION && currentTrack > 0)
				{
					// there are items to clear at the start

					Toast.makeText(this, getString(R.string.cleared_n_tracks, currentTrack), Toast.LENGTH_SHORT).show()

					for (i in 0 until currentTrack)
						queue.removeAt(0)

					currentTrack = 0
					_queueAdapter.notifyDataSetChanged()
					_queueChanged = true
				}
			}
			R.id.action_clearall ->
			{
				if (queue.size > 0)
				{
					Toast.makeText(this, getString(R.string.cleared_n_tracks, queue.size), Toast.LENGTH_SHORT).show()

					if (mediaPlaybackServiceStarted && _service != null)
						_service?.end()

					queue.clear()
					currentTrack = RecyclerView.NO_POSITION
					_queueAdapter.notifyDataSetChanged()
					_queueChanged = true
				}
			}
			R.id.action_clearafter ->
			{
				if (currentTrack != RecyclerView.NO_POSITION && currentTrack < queue.size - 1)
				{
					// there are items to clear at the end

					Toast.makeText(this, getString(R.string.cleared_n_tracks, queue.size - 1 - currentTrack), Toast.LENGTH_SHORT).show()

					for (i in queue.size - 1 downTo currentTrack + 1)
						queue.removeAt(i)

					currentTrack = queue.size - 1
					_queueAdapter.notifyDataSetChanged()
					_queueChanged = true
				}
			}
			R.id.action_setcolor -> selectAccent()
			R.id.action_about -> showAboutDialog(this)
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
				file.isDirectory || isFileExtensionInArray(file, SUPPORTED_FILE_EXTENSIONS)
			}

			if (list != null)
			{
				Arrays.sort(list) { a, b ->
					when
					{
						a.isFile && b.isDirectory -> 1
						a.isDirectory && b.isFile -> -1
						else -> a.name.compareTo(b.name, true)
					}
				}
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
		if(mediaPlaybackServiceStarted && _service != null)
			unbindService(_serviceConn)

		_lastSelectedTrack = currentTrack

		// save to shared prefs
		val sharedPref = getPreferences(Context.MODE_PRIVATE)
		with(sharedPref.edit())
		{
			if(_bookmarksChanged || _queueChanged)
			{
				val gson = Gson()
				if(_bookmarksChanged)
					putString(PREFERENCE_BOOKMARKS, gson.toJson(_bookmarks))

				if(_queueChanged)
					putString(PREFERENCE_QUEUE, gson.toJson(queue))
			}
			putString(PREFERENCE_LASTDIR, _currentDir?.absolutePath) // _currentDir is null -> preference is going to get deleted - no big deal
			putInt(PREFERENCE_ACCENTCOLOR, _accentColorIdx)
			putInt(PREFERENCE_CURRENTTRACK, currentTrack)
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
			_currentDir != null ->
			{
				updateDirectoryView(_currentDir?.parentFile, true)
			}
			else -> super.onBackPressed() // exit app
		}
	}
}
