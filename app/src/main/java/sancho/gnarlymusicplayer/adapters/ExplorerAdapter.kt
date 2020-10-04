package sancho.gnarlymusicplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.explorer_group_item.view.*
import kotlinx.android.synthetic.main.explorer_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sancho.gnarlymusicplayer.FileSupportChecker
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesAndDirsComparator
import sancho.gnarlymusicplayer.comparators.ExplorerViewFilesComparator
import sancho.gnarlymusicplayer.comparators.FilesComparator
import sancho.gnarlymusicplayer.models.ExplorerHeader
import sancho.gnarlymusicplayer.models.ExplorerItem
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.QueueItem
import java.io.File
import java.util.*

private const val EXPLORER_NORMAL_ITEM = 0
private const val EXPLORER_GROUP_ITEM = 1

class ExplorerAdapter(
	private val _context: Context,
	private val _dirList: MutableList<ExplorerViewItem>,
	private val _queueAdapter: QueueAdapter,
	private val _restoreListScrollPos: (String?) -> Unit,
	private val _playTrack: (Int) -> Unit,
	private val _setToolbarText: (String) -> Unit,
	private val _setDirListLoading: (Boolean) -> Unit) : RecyclerView.Adapter<ExplorerAdapter.FileHolder>()
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
		holder.bind(_dirList[position], ::singleClick, ::longClick)
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
	{
		fun bind(file: ExplorerViewItem, clickListener: (ExplorerViewItem) -> Unit, longClickListener: (ExplorerViewItem) -> Unit)
		{
			if (file.isHeader)
			{
				itemView.explorer_header_text.text = file.displayName
			}
			else
			{
				itemView.explorer_text.text = file.displayName

				val icon = when
				{
					file.isDirectory -> R.drawable.folder
					FileSupportChecker.isFileSupportedAndPlaylist(file.path) -> R.drawable.playlist
					else -> R.drawable.note
				}

				itemView.explorer_text.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)

				itemView.setOnClickListener { clickListener(file) }
				itemView.setOnLongClickListener {
					longClickListener(file)
					true // callback consumed long press
				}
			}
		}
	}

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
			Toast.makeText(_context, _context.getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
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
			Toast.makeText(_context, _context.getString(R.string.file_doesnt_exist), Toast.LENGTH_SHORT).show()
			return
		}

		if (file.isDirectory)
		{
			// add all tracks in dir (not recursive)
			val files = listDir(file, true)

			if (files != null)
			{
				files.sortWith(FilesComparator())
				addToQueue(files.map { track ->
					QueueItem(track.absolutePath, track.nameWithoutExtension)
				})

				Toast.makeText(_context, _context.getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
			}
			else
				Toast.makeText(_context, _context.getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}
		else if (FileSupportChecker.isFileSupportedAndPlaylist(file.path))
		{
			// add all tracks in playlist (not recursive)
			val files = listPlaylist(file)

			if (files != null)
			{
				addToQueue(files.map { track ->
					QueueItem(track.absolutePath, track.nameWithoutExtension)
				})

				Toast.makeText(_context, _context.getString(R.string.n_tracks_added_to_queue, files.size), Toast.LENGTH_SHORT).show()
			}
			else
				Toast.makeText(_context, _context.getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}
		else
		{
			// audio file
			addToQueue(QueueItem(file.absolutePath, file.nameWithoutExtension))
			_playTrack(PlaybackQueue.lastIdx)
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

			val queryButLower = query.toLowerCase(Locale.getDefault())

			// add results from current dir
			searchResultList.addAll(prevDirList
				.filter { file ->
					file.displayName.toLowerCase(Locale.getDefault()).contains(queryButLower)
				}
				.sortedWith(ExplorerViewFilesComparator())
			)

			// add results from first level dirs (grouped by subdir name)
			for (elem in prevDirList.filter { file -> file.isDirectory }.sortedWith(ExplorerViewFilesComparator()))
			{
				val dir = File(elem.path)

				val results = dir
					.listFiles { file ->
						(file.isDirectory || FileSupportChecker.isFileSupported(file.name))
								&& file.name.toLowerCase(Locale.getDefault()).contains(queryButLower)
					}
					?.map { file -> ExplorerItem(file.path, file.name, file.isDirectory) }
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
					Toast.makeText(_context, _context.getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun updateDirectoryViewShowStorage(): MutableList<ExplorerViewItem>?
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

	private fun updateDirectoryViewPlaylist(newPath: File): List<ExplorerViewItem>?
	{
		val list = listPlaylist(newPath)

		return list?.map{file -> ExplorerItem(file.path, file.name, file.isDirectory)}?.toMutableList()
	}

	private fun listDir(path: File, onlyAudio: Boolean): MutableList<File>?
	{
		return path.listFiles { file ->
			(onlyAudio && !file.isDirectory && FileSupportChecker.isFileSupportedAndAudio(file.name)) ||
					(!onlyAudio && (file.isDirectory || FileSupportChecker.isFileSupported(file.name)))
		}?.toMutableList()
	}

	private fun listPlaylist(path: File): MutableList<File>?
	{
		val list = mutableListOf<File>()

		path.readLines().forEach {
			val track = File(path.parent, it) // relative to playlist's directory
			if (track.exists() && !track.isDirectory && FileSupportChecker.isFileSupportedAndAudio(it))
				list.add(track)
		}

		return list
	}
}
