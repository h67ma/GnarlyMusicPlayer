package sancho.gnarlymusicplayer
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity()
{
	private val _dirList = mutableListOf<File>()
	private var _currentDir : File? = null
	private var _explorerAdapter : ExplorerAdapter? = null
	private var _mountedDevices = mutableListOf<File>()

	override fun onCreate(savedInstanceState: Bundle?)
	{
		// init gui stuff

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		setSupportActionBar(toolbar)

		// navigation arrow left
		/*val toggle = ActionBarDrawerToggle(
			this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
		)
		drawer_layout.addDrawerListener(toggle)
		toggle.syncState()*/

		title = ""

		// prepare list with storage devices
		getStorageDevices()

		// bookmarks

		val db = BookmarksDbHelper(applicationContext)

		val bookmarkList = db.getBookmarks()
		val bookmarkAdapter = BookmarksAdapter(this, bookmarkList)
		bookmark_list_view.adapter = bookmarkAdapter

		bookmark_add_btn.setOnClickListener{
			val path = _currentDir?.absolutePath
			val label = _currentDir?.name
			if(path != null && label != null)
			{
				// check if bookmark already exists
				if(bookmarkList.any{item -> item.path == path})
				{
					Toast.makeText(applicationContext, getString(R.string.bookmark_exists), Toast.LENGTH_SHORT).show()
					return@setOnClickListener
				}

				val bookmark = Bookmark(path, label)

				// insert to db
				db.insertBookmark(path, label)

				// also add to bookmark menu
				bookmarkList.add(bookmark)
				bookmarkAdapter.notifyDataSetChanged()
			}
			else
				Toast.makeText(applicationContext, getString(R.string.cant_add_root_dir), Toast.LENGTH_SHORT).show()
		}

		bookmark_root.setOnClickListener{
			_currentDir = null
			updateDirectoryView()
			drawer_layout.closeDrawer(GravityCompat.END)
		}

		bookmark_list_view.setOnItemClickListener { parent, _, position, _ ->
			val selected = parent.adapter.getItem(position) as Bookmark
			val dir = File(selected.path)
			if(dir.exists())
			{
				_currentDir = dir
				updateDirectoryView()
				drawer_layout.closeDrawer(GravityCompat.END)
			}
			else
				Toast.makeText(applicationContext, getString(R.string.dir_doesnt_exist), Toast.LENGTH_SHORT).show()
		}

		// file list

		_explorerAdapter = ExplorerAdapter(this, _dirList)
		library_list_view.adapter = _explorerAdapter
		library_list_view.setOnItemClickListener{parent, _, position, _ ->
			val selected = parent.adapter.getItem(position) as File
			if(selected.isDirectory)
			{
				_currentDir = selected
				updateDirectoryView()
			}
		}

		// check for permissions and initial update of file list
		requestReadPermishon()
	}

	// adds items to the action bar
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

	private fun requestReadPermishon()
	{
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			updateDirectoryView()
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
				updateDirectoryView()
			}
			else
			{
				Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show()
				requestReadPermishon()
			}
		}
	}

	// assuming the read permission is granted
	private fun updateDirectoryView()
	{
		if(_currentDir == null)
		{
			// list storage devices
			toolbar_title.text = getString(R.string.root_dir_name)
			_dirList.clear()
			_dirList.addAll(_mountedDevices)
			_explorerAdapter?.notifyDataSetChanged()
		}
		else
		{
			// list current dir
			toolbar_title.text = _currentDir?.absolutePath
			val list = _currentDir?.listFiles{file ->
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
				_explorerAdapter?.notifyDataSetChanged()
			}
			else
				Toast.makeText(this, getString(R.string.file_list_error), Toast.LENGTH_SHORT).show()
		}
	}

	private fun getStorageDevices()
	{
		_mountedDevices.clear()
		val externalStorageFiles = getExternalFilesDirs(null)
		for(f in externalStorageFiles)
		{
			val device = f.parentFile.parentFile.parentFile.parentFile // srsl?
			_mountedDevices.add(device)
		}
	}

	override fun onBackPressed()
	{
		when
		{
			drawer_layout.isDrawerOpen(GravityCompat.START) -> drawer_layout.closeDrawer(GravityCompat.START)
			drawer_layout.isDrawerOpen(GravityCompat.END) -> drawer_layout.closeDrawer(GravityCompat.END)
			_currentDir != null ->
			{
				_currentDir = _currentDir?.parentFile
				updateDirectoryView()
			}
			else -> super.onBackPressed() // exit app
		}
	}
}
