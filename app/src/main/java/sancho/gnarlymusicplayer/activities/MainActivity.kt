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
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.TagExtractor
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter
import sancho.gnarlymusicplayer.models.ExplorerItem
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.QueueItem
import sancho.gnarlymusicplayer.playbackservice.ACTION_START_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.playbackservice.BoundServiceListeners
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File

private const val REQUEST_READ_STORAGE = 42
private const val INTENT_LAUNCH_FOR_RESULT_SETTINGS = 1613
private const val BUNDLE_LASTSELECTEDTRACK = "sancho.gnarlymusicplayer.bundle.lastselectedtrack"

class MainActivity : AppCompatActivity()
{
	private val _dirList = mutableListOf<ExplorerViewItem>()
	private var _prevDirList = mutableListOf<ExplorerViewItem>() // store "real" dir listing, for re-searching ability. because _dirList contains search results

	private lateinit var _explorerAdapter: ExplorerAdapter

	private lateinit var _queueAdapter: QueueAdapter
	private var _lastSelectedTrack: Int = RecyclerView.NO_POSITION

	private lateinit var _bookmarks: MutableList<QueueItem>
	private var _bookmarksChanged = false

	private var _actionSearch: MenuItem? = null

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

	private fun setToolbarText(text: String)
	{
		toolbar_title.text = text
	}

	private fun setDirListLoading(loading: Boolean)
	{
		if (loading)
		{
			progress_horizontal.visibility = View.VISIBLE
			library_list_view.visibility = View.INVISIBLE
		}
		else
		{
			progress_horizontal.visibility = View.INVISIBLE
			library_list_view.visibility = View.VISIBLE
		}
	}

	private fun setupFileList()
	{
		library_list_view.layoutManager = LinearLayoutManager(this)

		_explorerAdapter = ExplorerAdapter(this, _dirList, _queueAdapter, ::restoreListScrollPos, ::playTrack, ::setToolbarText, ::setDirListLoading)
		library_list_view.adapter = _explorerAdapter
	}

	private fun setupBookmarks()
	{
		bookmark_list_view.layoutManager = LinearLayoutManager(this)
		val adapter = BookmarksAdapter(this, _bookmarks) { bookmark ->

			if (bookmark.path == _explorerAdapter.currentExplorerPath?.absolutePath)
			{
				drawer_layout.closeDrawer(GravityCompat.END)
				return@BookmarksAdapter // already open
			}

			val item = File(bookmark.path)
			if (item.exists())
			{
				_explorerAdapter.updateDirectoryView(item)

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
			val path = _explorerAdapter.currentExplorerPath?.absolutePath
			val label = _explorerAdapter.currentExplorerPath?.name
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
			_explorerAdapter.updateDirectoryView(null)
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

	private fun requestReadPermishon()
	{
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			_explorerAdapter.updateDirectoryView(AppSettingsManager.lastDir)
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
				_explorerAdapter.updateDirectoryView(AppSettingsManager.lastDir)
			}
			else
			{
				requestReadPermishon()
			}
		}
	}

	// call with null to scroll to top
	private fun restoreListScrollPos(oldPath: String?)
	{
		if (oldPath != null)
		{
			// find previous dir (child) in current dir list, scroll to it
			val pos = _dirList.indexOf(ExplorerItem(oldPath, "", true))
			(library_list_view.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 200)
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
			override fun onMenuItemActionCollapse(item: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach{elem -> elem.isVisible = true}
				_explorerAdapter.searchClose()
				return true
			}

			override fun onMenuItemActionExpand(item: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach{elem -> elem.isVisible = false}
				return true
			}
		})

		searchThing.setOnQueryTextListener(object : SearchView.OnQueryTextListener
		{
			override fun onQueryTextSubmit(query: String): Boolean
			{
				_explorerAdapter.searchOpen(query, _prevDirList)
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
			_explorerAdapter.updateDirectoryView(dir)
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

		AppSettingsManager.saveToPrefs(this, _bookmarksChanged, _explorerAdapter.currentExplorerPath?.absolutePath, _bookmarks)

		super.onPause()
	}

	override fun onBackPressed()
	{
		if (drawer_layout.isDrawerOpen(GravityCompat.START))
			drawer_layout.closeDrawer(GravityCompat.START)
		else if (drawer_layout.isDrawerOpen(GravityCompat.END))
			drawer_layout.closeDrawer(GravityCompat.END)
		else if (_explorerAdapter.searchResultsOpen)
		{
			_explorerAdapter.updateDirectoryView(_explorerAdapter.currentExplorerPath)
			_explorerAdapter.searchResultsOpen = false
		}
		else if (_explorerAdapter.currentExplorerPath != null)
		{
			val oldPath = _explorerAdapter.currentExplorerPath?.path
			_explorerAdapter.updateDirectoryView(_explorerAdapter.currentExplorerPath?.parentFile, oldPath)
		}
		else
			super.onBackPressed() // exit app
	}
}
