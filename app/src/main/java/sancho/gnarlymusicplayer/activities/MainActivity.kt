package sancho.gnarlymusicplayer.activities

import android.Manifest
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import sancho.gnarlymusicplayer.*
import sancho.gnarlymusicplayer.adapters.BookmarksAdapter
import sancho.gnarlymusicplayer.adapters.ExplorerAdapter
import sancho.gnarlymusicplayer.adapters.QueueAdapter
import sancho.gnarlymusicplayer.databinding.ActivityMainBinding
import sancho.gnarlymusicplayer.models.ExplorerItem
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.QueueItem
import sancho.gnarlymusicplayer.playbackservice.ACTION_START_PLAYBACK_SERVICE
import sancho.gnarlymusicplayer.playbackservice.BoundServiceListeners
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File


private const val REQUEST_READ_STORAGE = 42
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

	private var _seekDialog: BottomSheetDialog? = null

	private var _service: MediaPlaybackService? = null
	private lateinit var _serviceConn: ServiceConnection

	private var _seekMenuItem: MenuItem? = null

	private lateinit var _activityKappaLauncher: ActivityResultLauncher<Intent>

	private lateinit var _binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?)
	{
		AppSettingsManager.restoreFromPrefs(this)
		_bookmarks = AppSettingsManager.restoreBookmarks(this)
		_bookmarksChanged = false

		setTheme(AppSettingsManager.getStyleFromPreference())

		if(savedInstanceState != null)
			_lastSelectedTrack = savedInstanceState.getInt(BUNDLE_LASTSELECTEDTRACK, RecyclerView.NO_POSITION)

		super.onCreate(savedInstanceState)
		_binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(_binding.root)
		setSupportActionBar(_binding.toolbar)
		title = ""

		_binding.toolbarScroller.isSmoothScrollingEnabled = false

		setupServiceConnection()

		setupBookmarks()

		setupQueue()

		setupFileList()

		setupActivityLauncher()

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

		// if initially starting the activity, _seekMenuItem will be null and nothing will happen
		// however if returning to activity, onCreateOptionsMenu won't get called to set visibility,
		// so it needs to be done here
		setSeekBtnVisibility(MediaPlaybackService.mediaPlaybackServiceStarted)

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

	private fun setupActivityLauncher()
	{
		// this is only used for the settings activity
		_activityKappaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			/* to get intent data (but which intent would that be?):
			result ->
			val data: Intent? = result.data
			can also check if (result.resultCode == Activity.RESULT_OK) but it's not ok :()
			*/

			recreate() // must call onCreate after changing theme
		}
	}

	private fun setToolbarText(text: String)
	{
		_binding.toolbarTitle.text = text
		toolbarScrollRight()
	}

	private fun toolbarScrollRight()
	{
		_binding.toolbarScroller.scrollTo(HorizontalScrollView.FOCUS_RIGHT, 0)

		// idk why, but this also needs to be done for it to work in *release* build
		_binding.toolbarScroller.post {
			_binding.toolbarScroller.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
		}
	}

	private fun setDirListLoading(loading: Boolean)
	{
		if (loading)
		{
			_binding.progressHorizontal.visibility = View.VISIBLE
			_binding.libraryListView.visibility = View.INVISIBLE
		}
		else
		{
			_binding.progressHorizontal.visibility = View.INVISIBLE
			_binding.libraryListView.visibility = View.VISIBLE
		}
	}

	private fun setupExplorerCtxMenu(dialog: BottomSheetDialogCtxMenu, displayName: String, path: String)
	{
		dialog.setHeaderText(R.id.sheet_explorer_item_name, displayName)
		dialog.setBottomSheetItemOnClick(R.id.sheet_explorer_details) { showExplorerTrackInfo(path) }
		dialog.setBottomSheetItemOnClick(R.id.sheet_explorer_goto_parent_dir) { gotoExplorerTrackDir(path) }
		dialog.setBottomSheetItemOnClick(R.id.sheet_explorer_insert_at_top) { addToQueueAt(0, path) }
		dialog.setBottomSheetItemOnClick(R.id.sheet_explorer_insert_after_current) { addToQueueAt(PlaybackQueue.currentIdx + 1, path) }
	}

	private fun setupFileList()
	{
		_explorerAdapter = ExplorerAdapter(this, _dirList, _queueAdapter, ::restoreListScrollPos, ::setToolbarText, ::setDirListLoading, ::setupExplorerCtxMenu)
		_binding.libraryListView.adapter = _explorerAdapter
	}

	private fun setupBookmarks()
	{
		val adapter = BookmarksAdapter(this, _bookmarks) { bookmark ->

			if (bookmark.path == _explorerAdapter.currentExplorerPath?.absolutePath)
			{
				_binding.drawerLayout.closeDrawer(GravityCompat.END)
				return@BookmarksAdapter // already open
			}

			val item = File(bookmark.path)
			if (item.exists())
			{
				_explorerAdapter.updateDirectoryView(item)

				_binding.drawerLayout.closeDrawer(GravityCompat.END)
				_actionSearch?.collapseActionView() // collapse searchbar thing
			}
			else
				Toaster.show(this, getString(R.string.dir_doesnt_exist))
		}
		_binding.bookmarkListView.adapter = adapter

		val touchHelper = ItemTouchHelper(object : DragListTouchHelperCallback(this)
		{
			override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int
			{
				return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT)
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
		touchHelper.attachToRecyclerView(_binding.bookmarkListView)

		_binding.bookmarkAddBtn.setOnClickListener {
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

		_binding.bookmarkRoot.setOnClickListener {
			_explorerAdapter.updateDirectoryView(null)
			_binding.drawerLayout.closeDrawer(GravityCompat.END)
		}
	}

	private fun setupQueueCtxMenu(dialog: BottomSheetDialogCtxMenu, displayName: String, selectedIdx: Int)
	{
		dialog.setHeaderText(R.id.sheet_queue_item_name, displayName)
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_details) { showQueueTrackInfo(selectedIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_goto_parent_dir) { gotoQueueTrackDir(selectedIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_move_to_top) { moveQueueItem(selectedIdx, 0)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_move_after_current) { moveAfterCurrentTrack(selectedIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_move_to_bottom) { moveQueueItem(selectedIdx, PlaybackQueue.lastIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_move_current_here) { moveCurrentHere(selectedIdx) }
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_clear_above) { clearAbove(selectedIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_clear_below) { clearBelow(selectedIdx)}
		dialog.setBottomSheetItemOnClick(R.id.sheet_queue_clear_all) { clearAll()}
	}

	private fun setupQueue()
	{
		_queueAdapter = QueueAdapter(this, ::playTrack, ::setupQueueCtxMenu)
		_binding.queueListView.adapter = _queueAdapter

		val touchHelper = ItemTouchHelper(object : DragListTouchHelperCallback(this)
		{
			override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int
			{
				return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT)
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
		touchHelper.attachToRecyclerView(_binding.queueListView)
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

			if (_actionSearch?.isActionViewExpanded != true)
				setSeekBtnVisibility(true) // if search thing is open, icon should not be shown
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

						setSeekBtnVisibility(false)
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
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
	}

	// call with null to scroll to top
	private fun restoreListScrollPos(oldPath: String?)
	{
		if (oldPath != null)
		{
			// find previous dir (child) in current dir list, scroll to it
			val pos = _dirList.indexOf(ExplorerItem(oldPath, "", true))
			(_binding.libraryListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 200)
		}
		else
			(_binding.libraryListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
	}

	//region MENU

	// adds items to toolbar
	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)

		// search thing
		_actionSearch = menu.findItem(R.id.action_search)

		_seekMenuItem = menu.findItem(R.id.action_seek) // for manipulation outside onCreateOptionsMenu
		setSeekBtnVisibility(MediaPlaybackService.mediaPlaybackServiceStarted)

		val searchThing = _actionSearch?.actionView as SearchView
		searchThing.queryHint = getString(R.string.search_bar_hint)
		searchThing.maxWidth = Int.MAX_VALUE

		_actionSearch?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener
		{
			// SearchView.OnCloseListener simply doesn't work. THANKS ANDROID
			override fun onMenuItemActionCollapse(item: MenuItem?): Boolean
			{
				menu.findItem(R.id.action_settings).isVisible = true
				setSeekBtnVisibility(MediaPlaybackService.mediaPlaybackServiceStarted)
				_explorerAdapter.searchClose()
				return true
			}

			override fun onMenuItemActionExpand(item: MenuItem?): Boolean
			{
				menu.findItem(R.id.action_settings).isVisible = false
				setSeekBtnVisibility(false)
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

	private fun setSeekBtnVisibility(visible: Boolean)
	{
		_seekMenuItem?.isVisible = visible
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
		_binding.drawerLayout.closeDrawer(GravityCompat.START)
		_actionSearch?.collapseActionView() // collapse searchbar thing
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
		_actionSearch?.collapseActionView() // collapse searchbar thing
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

		_seekDialog = BottomSheetDialogSeek(this, _service, layoutInflater)
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

		PlaybackQueue.moveItem(fromPosition, toPosition)
		_queueAdapter.notifyItemMoved(fromPosition, toPosition)
	}

	private fun moveCurrentHere(toPosition: Int)
	{
		val fromPosition = PlaybackQueue.currentIdx

		if (fromPosition == toPosition)
			return // user selected currently playing item

		PlaybackQueue.moveItem(fromPosition, toPosition)
		_queueAdapter.notifyItemMoved(fromPosition, toPosition)
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
		val intent = Intent(this, SettingsActivity::class.java)
		_activityKappaLauncher.launch(intent)
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

		if (_actionSearch?.isActionViewExpanded == true)
			_actionSearch?.collapseActionView() // collapse searchbar thing

		AppSettingsManager.saveToPrefs(this, _bookmarksChanged, _explorerAdapter.currentExplorerPath?.absolutePath, _bookmarks)

		super.onPause()
	}

	override fun onBackPressed()
	{
		@Suppress("CascadeIf")
		if (_binding.drawerLayout.isDrawerOpen(GravityCompat.START))
			_binding.drawerLayout.closeDrawer(GravityCompat.START)
		else if (_binding.drawerLayout.isDrawerOpen(GravityCompat.END))
			_binding.drawerLayout.closeDrawer(GravityCompat.END)
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
