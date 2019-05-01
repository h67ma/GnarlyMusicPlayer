package sancho.gnarlymusicplayer
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson

class MainActivity : AppCompatActivity()
{
	private val _dirList = mutableListOf<File>()
	private var _currentDir : File? = null
	private lateinit var _explorerAdapter : ExplorerAdapter
	private lateinit var _mountedDevices: MutableList<File>
	private lateinit var _bookmarks: MutableList<Bookmark>
	private var _bookmarksChanged = false
	private var _queueChanged = false

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		setSupportActionBar(toolbar)

		title = ""

		restoreFromPrefs() // TODO this in here or in onStart/onResume? https://developer.android.com/guide/components/activities/activity-lifecycle

		getStorageDevices() // prepare list with storage devices

		setupBookmarks()

		setupFileList()

		requestReadPermishon() // check for permissions and initial update of file list
	}

	private fun restoreFromPrefs()
	{
		val gson = Gson()
		val sharedPref = getPreferences(Context.MODE_PRIVATE)

		val bookmarks = sharedPref.getString(PREFERENCE_BOOKMARKS, "[]")
		val collectionType = object : TypeToken<Collection<Bookmark>>() {}.type
		_bookmarks = gson.fromJson(bookmarks, collectionType)

		//val queue = sharedPref.getString(PREFERENCE_QUEUE, "[]")
	}

	private fun setupFileList()
	{
		_explorerAdapter = ExplorerAdapter(this, _dirList)
		library_list_view.adapter = _explorerAdapter
		library_list_view.setOnItemClickListener{parent, _, position, _ ->
			val selected = parent.adapter.getItem(position) as File
			if(selected.isDirectory)
			{
				updateDirectoryView(selected)
			}
		}
	}

	private fun setupBookmarks()
	{
		bookmark_list_view.layoutManager = LinearLayoutManager(this)
		val bookmarksAdapter = BookmarksAdapter(this, _bookmarks) { bookmark ->
			val dir = File(bookmark.path)
			if(dir.exists())
			{
				updateDirectoryView(dir)
				drawer_layout.closeDrawer(GravityCompat.END)
			}
			else
				Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
		}
		bookmark_list_view.adapter = bookmarksAdapter

		bookmark_add_btn.setOnClickListener{
			val path = _currentDir?.absolutePath
			val label = _currentDir?.name
			if(path != null && label != null)
			{
				// check if bookmark already exists
				if(_bookmarks.any{item -> item.path == path})
				{
					Toast.makeText(applicationContext, getString(R.string.bookmark_exists), Toast.LENGTH_SHORT).show()
					return@setOnClickListener
				}

				val bookmark = Bookmark(path, label)

				// also add to bookmark menu
				bookmarksAdapter.onItemAdded(bookmark)
				_bookmarksChanged = true
				bookmarksAdapter.notifyItemInserted(_bookmarks.size - 1)
			}
			else
				Toast.makeText(applicationContext, getString(R.string.cant_add_root_dir), Toast.LENGTH_SHORT).show()
		}

		bookmark_root.setOnClickListener{
			updateDirectoryView(null)
			drawer_layout.closeDrawer(GravityCompat.END)
		}

		ItemTouchHelper(object : ItemTouchHelper.Callback()
		{
			override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int
			{
				return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT)
			}

			override fun isLongPressDragEnabled(): Boolean
			{
				return true
			}

			override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
			{
				bookmarksAdapter.onItemMoved(viewHolder.adapterPosition, target.adapterPosition)
				_bookmarksChanged = true
				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int)
			{
				val position = viewHolder.adapterPosition
				bookmarksAdapter.onItemRemoved(position)
				_bookmarksChanged = true
				bookmarksAdapter.notifyItemRemoved(position)
			}
		}).attachToRecyclerView(bookmark_list_view)
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
			updateDirectoryView(null)
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
		return when (item.itemId) {
			R.id.action_settings -> true
			else -> super.onOptionsItemSelected(item)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if(requestCode == REQUEST_READ_STORAGE)
		{
			if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			{
				updateDirectoryView(null)
			}
			else
			{
				requestReadPermishon()
			}
		}
	}

	// assuming the read permission is granted
	private fun updateDirectoryView(newDir: File?)
	{
		_currentDir = newDir
		if(newDir == null || !newDir.isDirectory)
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
						else -> a.compareTo(b)
					}
				}
				_dirList.clear()
				_dirList.addAll(list)
				_explorerAdapter.notifyDataSetChanged()
			}
			else
				Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}
	}

	override fun onPause()
	{
		if(_bookmarksChanged || _queueChanged)
		{
			val sharedPref = getPreferences(Context.MODE_PRIVATE)

			with(sharedPref.edit())
			{
				val gson = Gson()
				if(_bookmarksChanged)
					putString(PREFERENCE_BOOKMARKS, gson.toJson(_bookmarks))

				/*if(_queueChanged)
					putString(PREFERENCE_QUEUE, "TODO")*/

				apply()
			}
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
				updateDirectoryView(_currentDir?.parentFile)
			}
			else -> super.onBackPressed() // exit app
		}
	}
}
