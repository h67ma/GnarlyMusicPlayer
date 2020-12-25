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
import android.widget.HorizontalScrollView
import android.widget.SeekBar
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
const val EXTRA_TRACK_DETAIL_PATH = "sancho.gnarlymusicplayer.extra.trackdetailpath"

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

	private var _whichMenuIsOpen = WhichMenu.NONE

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

		toolbar_scroller.isSmoothScrollingEnabled = false

		setupServiceConnection()

		setupBookmarks()

		setupQueue()

		setupFileList()

		requestReadPermishon() // check for permissions and initial update of file list
	}

	override fun onWindowFocusChanged(hasFocus: Boolean)
	{
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus)
		{
			toolbarScrollRight() // needs to be done here for when activity is created/switching between activities
		}
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
		toolbarScrollRight()
	}

	private fun toolbarScrollRight()
	{
		toolbar_scroller.scrollTo(HorizontalScrollView.FOCUS_RIGHT, 0)

		// idk why, but this also needs to be done for it to work in *release* build
		toolbar_scroller.post {
			toolbar_scroller.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
		}
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
		_explorerAdapter = ExplorerAdapter(this, _dirList, _queueAdapter, ::restoreListScrollPos, ::setToolbarText, ::setDirListLoading)
		library_list_view.adapter = _explorerAdapter

		library_list_view.setOnCreateContextMenuListener{ menu, _, _ ->
			menuInflater.inflate(R.menu.explorer_item, menu)
			menu?.setHeaderTitle(_dirList[_explorerAdapter.selectedPosition].displayName) // selectedPosition will be set in adapter
			menu?.setGroupDividerEnabled(true)
			_whichMenuIsOpen = WhichMenu.EXPLORER
		}
	}

	private fun setupBookmarks()
	{
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
				Toaster.show(this, getString(R.string.dir_doesnt_exist))
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
					Toaster.show(this, getString(R.string.bookmark_exists))
					return@setOnClickListener
				}

				// also add to bookmark menu
				adapter.onItemAdded(QueueItem(path, label))
				_bookmarksChanged = true
				adapter.notifyItemInserted(_bookmarks.size - 1)
			}
			else
				Toaster.show(this, getString(R.string.cant_add_root_dir))
		}

		bookmark_root.setOnClickListener {
			_explorerAdapter.updateDirectoryView(null)
			drawer_layout.closeDrawer(GravityCompat.END)
		}
	}

	private fun setupQueue()
	{
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
				moveQueueItem(fromPosition, toPosition)

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

		queue_list_view.setOnCreateContextMenuListener{ menu, _, _ ->
			menuInflater.inflate(R.menu.queue_item, menu)
			menu?.setHeaderTitle(PlaybackQueue.getTrackName(_queueAdapter.selectedPosition)) // selectedPosition will be set in adapter
			menu?.setGroupDividerEnabled(true)
			_whichMenuIsOpen = WhichMenu.QUEUE
		}
	}

	override fun onContextItemSelected(menuItem: MenuItem): Boolean
	{
		// menuItem.menuInfo is null, need some way to determine which menu this is.
		// although you can extend RecyclerView and override getContextMenuInfo()
		// to get menu info, this approach is far simpler and cleaner.
		// another approach is to wrap all menu items in group and check group id,
		// but then you loose the ability to add separators in context menu

		if (_whichMenuIsOpen == WhichMenu.QUEUE)
		{
			val selectedIdx = _queueAdapter.selectedPosition // will be set in adapter
			when (menuItem.itemId)
			{
				R.id.queue_goto_parent -> gotoQueueTrackDir(selectedIdx)
				R.id.queue_moveto_after -> moveAfterCurrentTrack(selectedIdx)
				R.id.queue_moveto_top -> moveQueueItem(selectedIdx, 0)
				R.id.queue_moveto_bottom -> moveQueueItem(selectedIdx, PlaybackQueue.lastIdx)
				R.id.queue_clear_above -> clearAbove(selectedIdx)
				R.id.queue_clear_below -> clearBelow(selectedIdx)
				R.id.queue_clear_all -> clearAll()
				R.id.queue_details -> showQueueTrackInfo(selectedIdx)
				else -> return false
			}
			return true
		}
		else if (_whichMenuIsOpen == WhichMenu.EXPLORER)
		{
			val selectedIdx = _explorerAdapter.selectedPosition // will be set in adapter
			val itemPath = _dirList[selectedIdx].path
			when (menuItem.itemId)
			{
				R.id.explorer_details -> showExplorerTrackInfo(itemPath)
				R.id.explorer_goto_parent -> gotoExplorerTrackDir(itemPath)
				R.id.explorer_addto_top -> addToQueueAt(0, itemPath)
				R.id.explorer_addto_after_current -> addToQueueAt(PlaybackQueue.currentIdx + 1, itemPath)
				else -> return false
			}
			return true
		}

		return super.onContextItemSelected(menuItem)
	}

	private fun playTrack(newPosition: Int)
	{
		if (!PlaybackQueue.trackExists(newPosition))
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
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

		val menuElementsToToggle = listOf(
			menu.findItem(R.id.action_seek),
			menu.findItem(R.id.action_settings)
		)

		val searchThing = _actionSearch?.actionView as SearchView
		searchThing.queryHint = getString(R.string.search_bar_hint)
		searchThing.maxWidth = Int.MAX_VALUE

		_actionSearch?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener
		{
			// SearchView.OnCloseListener simply doesn't work. THANKS ANDROID
			override fun onMenuItemActionCollapse(item: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach { elem -> elem.isVisible = true }
				_explorerAdapter.searchClose()
				return true
			}

			override fun onMenuItemActionExpand(item: MenuItem?): Boolean
			{
				menuElementsToToggle.forEach { elem -> elem.isVisible = false }
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
			R.id.action_settings -> launchSettings()
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	//endregion

	//region MENU ACTIONS

	private fun showQueueTrackInfo(idx: Int)
	{
		if (!PlaybackQueue.trackExists(idx))
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
			return
		}

		showTrackInfo(PlaybackQueue.getTrackPath(idx))
	}

	private fun showExplorerTrackInfo(path: String)
	{
		if (!File(path).exists())
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
			return
		}

		showTrackInfo(path)
	}

	private fun showTrackInfo(path: String?)
	{
		val intent = Intent(this, TrackInfoActivity::class.java)
		intent.putExtra(EXTRA_TRACK_DETAIL_PATH, path)
		startActivity(intent)
	}

	private fun gotoQueueTrackDir(idx: Int)
	{
		if (!PlaybackQueue.trackExists(idx))
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
			return
		}

		_explorerAdapter.updateDirectoryView(PlaybackQueue.getTrackParent(idx))
		drawer_layout.closeDrawer(GravityCompat.START)
	}

	private fun gotoExplorerTrackDir(path: String)
	{
		val parent = File(path).parentFile
		if (parent?.exists() != true)
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
			return
		}
		_explorerAdapter.updateDirectoryView(parent)
	}

	private fun addToQueueAt(idx: Int, path: String)
	{
		val file = File(path)

		if (!file.exists())
		{
			Toaster.show(this, getString(R.string.file_doesnt_exist))
			return
		}

		PlaybackQueue.addAt(idx, QueueItem(file.absolutePath, file.nameWithoutExtension))
		_queueAdapter.notifyItemInserted(idx)
		_queueAdapter.notifyItemRangeChanged(idx, PlaybackQueue.lastIdx) // need to update indexes in tail
	}

	private fun showSeekDialog()
	{
		if (!MediaPlaybackService.mediaPlaybackServiceStarted || _service == null)
		{
			Toaster.show(this, getString(R.string.playback_service_not_running))
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
					Toaster.show(this@MainActivity, getString(R.string.playback_service_not_running))
			}
		})

		_seekDialog = AlertDialog.Builder(this)
			.setTitle(getString(R.string.seek_restore_position))
			.setView(seekView)
			.setNegativeButton(R.string.close, null)
			.create()

		_seekDialog?.show()
	}

	private fun moveAfterCurrentTrack(fromPosition: Int)
	{
		// current track and track after that will be ignored
		val toPosition = PlaybackQueue.currentIdx
		if (fromPosition > toPosition + 1)
			moveQueueItem(fromPosition, toPosition + 1)
		else if (fromPosition < toPosition)
			moveQueueItem(fromPosition, toPosition)
	}

	private fun moveQueueItem(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition == toPosition)
			return

		_queueAdapter.onItemMoved(fromPosition, toPosition)
		PlaybackQueue.updateIdxAfterItemMoved(fromPosition, toPosition)
	}

	private fun clearAbove(idx: Int)
	{
		ConfirmDialog.show(this, getString(R.string.q_clear_queue_above, PlaybackQueue.getTrackName(idx)))
		{
			val oldCurrIdx = PlaybackQueue.currentIdx

			val clearedCnt = PlaybackQueue.removeAbove(idx)
			if (clearedCnt > 0)
			{
				_queueAdapter.notifyItemRangeRemoved(0, idx)
				_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx) // could've changed to selected
			}

			Toaster.show(this, getString(R.string.cleared_n_tracks, clearedCnt))

			if (oldCurrIdx - clearedCnt != PlaybackQueue.currentIdx)
			{
				if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
					_service?.setTrack(false)
			}
		}
	}

	private fun clearBelow(idx: Int)
	{
		ConfirmDialog.show(this, getString(R.string.q_clear_queue_below, PlaybackQueue.getTrackName(idx)))
		{
			val oldCurrIdx = PlaybackQueue.currentIdx

			val clearedCnt = PlaybackQueue.removeBelow(idx)
			if (clearedCnt > 0)
			{
				_queueAdapter.notifyItemRangeRemoved(idx + 1, clearedCnt)
				_queueAdapter.notifyItemChanged(PlaybackQueue.currentIdx) // could've changed to selected
			}

			Toaster.show(this, getString(R.string.cleared_n_tracks, clearedCnt))

			if (oldCurrIdx != PlaybackQueue.currentIdx)
			{
				if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
					_service?.setTrack(false)
			}
		}
	}

	private fun clearAll()
	{
		ConfirmDialog.show(this, getString(R.string.q_clear_queue))
		{
			// gotta stop service before removing all
			if (MediaPlaybackService.mediaPlaybackServiceStarted && _service != null)
				_service?.end(false)

			val clearedCnt = PlaybackQueue.removeAll()

			_queueAdapter.notifyItemRangeRemoved(0, clearedCnt)
			Toaster.show(this, getString(R.string.cleared_n_tracks, clearedCnt))
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
