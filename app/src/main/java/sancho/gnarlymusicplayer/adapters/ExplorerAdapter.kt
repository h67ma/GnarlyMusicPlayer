package sancho.gnarlymusicplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sancho.gnarlymusicplayer.*
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesAndDirsComparator
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesComparator
import sancho.gnarlymusicplayer.comparators.FilesComparator
import sancho.gnarlymusicplayer.models.*
import java.io.File

private const val EXPLORER_NORMAL_ITEM = 0
private const val EXPLORER_GROUP_ITEM = 1

class ExplorerAdapter(
	private val _context: Context,
	private val _dirList: MutableList<ExplorerViewItem>,
	private val _queueAdapter: QueueAdapter,
	private val _restoreListScrollPos: (String?) -> Unit,
	private val _setToolbarText: (String) -> Unit,
	private val _setDirListLoading: (Boolean) -> Unit,
	private val _setupExplorerCtxMenu: (BottomSheetDialogCtxMenu, String, String) -> Unit) : RecyclerView.Adapter<ExplorerAdapter.FileHolder>()
{
	private val _mountedDevices: MutableList<ExplorerViewItem> = mutableListOf()
	var currentExplorerPath: File? = null
	var searchResultsOpen = false

	private var _fileLoaderJob: Job? = null

	init
	{
		val externalStorageFiles = _context.getExternalFilesDirs(null)
		for(f in externalStorageFiles)
		{
			val device = f?.parentFile?.parentFile?.parentFile?.parentFile // this is so bad
			_mountedDevices.add(ExplorerItem(device?.path ?: "", device?.name ?: "", device?.isDirectory ?: false))
		}
	}

	override fun onBindViewHolder(holder: FileHolder, position: Int)
	{
		val file = _dirList[position]

		if (file.isHeader)
		{
			holder.itemView.findViewById<TextView>(R.id.explorer_header_text).text = file.displayName
		}
		else
		{
			holder.itemView.findViewById<TextView>(R.id.explorer_text).text = file.displayName

			val icon = when
			{
				file.isDirectory -> R.drawable.folder
				file.isError -> R.drawable.warning
				FileSupportChecker.isFileSupportedAndPlaylist(file.path) -> R.drawable.playlist
				else -> R.drawable.note
			}

			holder.itemView.findViewById<TextView>(R.id.explorer_text).setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)

			holder.itemView.setOnClickListener { singleClick(file) }
			holder.itemView.setOnLongClickListener {
				// don't worry about header items, they are unclickable (famous last words)
				if (file.isDirectory || FileSupportChecker.isFileSupportedAndPlaylist(file.path))
				{
					longClick(file) // directory or playlist
				}
				else
				{
					val dialog = BottomSheetDialogCtxMenu(_context, R.layout.bottom_sheet_explorer)
					_setupExplorerCtxMenu(dialog, file.displayName, file.path)
					dialog.show()
				}

				true // consumed long press
			}
		}
	}

	override fun getItemCount() = _dirList.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder
	{
		return when (viewType)
		{
			EXPLORER_NORMAL_ITEM -> FileHolder(LayoutInflater.from(_context).inflate(R.layout.explorer_item, parent, false))
			else -> FileHolder(LayoutInflater.from(_context).inflate(R.layout.explorer_group_item, parent, false))
		}
	}

	class FileHolder(view: View) : RecyclerView.ViewHolder(view)

	override fun getItemViewType(pos: Int): Int
	{
		if (_dirList[pos].isHeader)
			return EXPLORER_GROUP_ITEM

		return EXPLORER_NORMAL_ITEM
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

	private fun singleClick(item: ExplorerViewItem)
	{
		val file = File(item.path)

		if (!file.exists())
		{
			Toaster.show(_context, _context.getString(R.string.file_doesnt_exist))
			return
		}

		if (file.isDirectory || FileSupportChecker.isFileSupportedAndPlaylist(file.path))
		{
			// navigate to directory or open playlist
			updateDirectoryView(file)
			_restoreListScrollPos(null)
			searchResultsOpen = false // in case the dir was from search results
		}
		else
		{
			// audio file
			addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
		}
	}

	private fun longClick(item: ExplorerViewItem)
	{
		val file = File(item.path)

		if (!file.exists())
		{
			Toaster.show(_context, _context.getString(R.string.dir_doesnt_exist))
			return
		}

		if (file.isDirectory)
		{
			// add all tracks in dir (not recursive)
			val files = listDir(file, true)

			if (files != null)
			{
				files.sortWith(FilesComparator())
				addToQueue(files.map { QueueItem(it.absolutePath, it.nameWithoutExtension) })

				Toaster.show(_context, _context.getString(R.string.n_tracks_added_to_queue, files.size))
			}
			else
				Toaster.show(_context, _context.getString(R.string.file_list_error))
		}
		else if (FileSupportChecker.isFileSupportedAndPlaylist(file.path))
		{
			// add all tracks in playlist (not recursive)
			val files = listPlaylist(file)

			files.removeAll { !it.exists() } // we're not interested in paths to non-existent files

			addToQueue(files.map { QueueItem(it.absolutePath, it.nameWithoutExtension) })

			Toaster.show(_context, _context.getString(R.string.n_tracks_added_to_queue, files.size))
		}
	}

	fun searchOpen(query: String, prevDirList: MutableList<ExplorerViewItem>)
	{
		_setDirListLoading(true)

		if (!searchResultsOpen)
		{
			prevDirList.clear()
			prevDirList.addAll(_dirList)
		}

		_fileLoaderJob?.cancel()

		_fileLoaderJob = GlobalScope.launch(Dispatchers.IO)
		{
			val searchResultList = mutableListOf<ExplorerViewItem>()

			val queryButLower = query.lowercase()

			// add results from current dir
			searchResultList.addAll(prevDirList
				.filter { it.displayName.lowercase().contains(queryButLower) }
				.sortedWith(ExplorerViewFilesComparator())
			)

			// add results from first level dirs (grouped by subdir name)
			for (elem in prevDirList.filter { it.isDirectory }.sortedWith(ExplorerViewFilesComparator()))
			{
				val dir = File(elem.path)

				val results = dir
					.listFiles { file ->
						(file.isDirectory || FileSupportChecker.isFileSupported(file.name))
								&& file.name.lowercase().contains(queryButLower)
					}
					?.map { ExplorerItem(it.path, it.name, it.isDirectory) }
					?.sortedWith(ExplorerViewFilesComparator())

				if (results?.isNotEmpty() == true)
				{
					// add subdir header
					searchResultList.add(ExplorerHeader(elem.displayName))

					// add results in this dir
					searchResultList.addAll(results)
				}
			}

			GlobalScope.launch(Dispatchers.Main)
			{
				_dirList.clear()
				_dirList.addAll(searchResultList)
				notifyDataSetChanged()
				searchResultsOpen = true
				_setDirListLoading(false)
			}
		}
	}

	fun searchClose()
	{
		updateDirectoryView(currentExplorerPath)
		searchResultsOpen = false
	}

	fun updateDirectoryView(newPath: File?, oldPath: String? = null)
	{
		currentExplorerPath = newPath
		_setDirListLoading(true)
		_setToolbarText(if (newPath == null) _context.getString(R.string.storage_devices) else newPath.absolutePath)

		_fileLoaderJob?.cancel()

		_fileLoaderJob = GlobalScope.launch(Dispatchers.IO)
		{
			val futureList: List<ExplorerViewItem>? =
				if (newPath == null || !newPath.exists())
					updateDirectoryViewShowStorage()
				else if (newPath.isDirectory)
					updateDirectoryViewDir(newPath)
				else
					updateDirectoryViewPlaylist(newPath)

			GlobalScope.launch(Dispatchers.Main)
			{
				if (futureList != null)
				{
					_dirList.clear() // must modify recyclerview list on main thread
					_dirList.addAll(futureList)
					notifyDataSetChanged()
					_restoreListScrollPos(oldPath)
					_setDirListLoading(false)
				}
				else
					Toaster.show(_context, _context.getString(R.string.file_list_error))
			}
		}
	}

	private fun updateDirectoryViewShowStorage(): MutableList<ExplorerViewItem>
	{
		return _mountedDevices
	}

	private fun updateDirectoryViewDir(newPath: File): List<ExplorerViewItem>?
	{
		val list = listDir(newPath, false)

		val viewList = list?.map{file -> ExplorerItem(file.path, file.name, file.isDirectory) }?.toMutableList()
		viewList?.sortWith(ExplorerViewFilesAndDirsComparator())

		return viewList
	}

	private fun updateDirectoryViewPlaylist(newPath: File): List<ExplorerViewItem>
	{
		val list = mutableListOf<ExplorerViewItem>()

		val files = listPlaylist(newPath)

		files.forEach {
			if (it.exists())
				list.add(ExplorerItem(it.path, it.name, it.isDirectory))
			else
				list.add(ExplorerErrorItem(it.path, it.name, it.isDirectory))
		}

		return list.toMutableList()
	}

	private fun listDir(path: File, onlyAudio: Boolean): MutableList<File>?
	{
		return path.listFiles { file ->
			(onlyAudio && !file.isDirectory && FileSupportChecker.isFileSupportedAndAudio(file.name)) ||
					(!onlyAudio && (file.isDirectory || FileSupportChecker.isFileSupported(file.name)))
		}?.toMutableList()
	}

	private fun listPlaylist(path: File): MutableList<File>
	{
		val list = mutableListOf<File>()

		path.readLines().forEach {
			val track = File(path.parent, it).canonicalFile // resolve path (.. and stuff)
			if (!track.isDirectory && FileSupportChecker.isFileSupportedAndAudio(it))
				list.add(track)
		}

		return list
	}
}
