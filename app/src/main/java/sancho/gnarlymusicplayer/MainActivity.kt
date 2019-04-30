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
	private var _currentDepth : Int = 0
	private var _explorerAdapter : ExplorerAdapter? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		title = ""

		setSupportActionBar(toolbar)

		// navigation arrow left
		/*val toggle = ActionBarDrawerToggle(
			this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
		)
		drawer_layout.addDrawerListener(toggle)
		toggle.syncState()*/

		_explorerAdapter = ExplorerAdapter(this, _dirList)
		library_list_view.adapter = _explorerAdapter
		library_list_view.setOnItemClickListener{parent, _, position, _ ->
			val selected = parent.adapter.getItem(position) as File
			if(selected.isDirectory)
			{
				_currentDir = selected
				_currentDepth++
				updateDirectoryView()
			}
		}

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
				Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
				requestReadPermishon()
			}
		}
	}

	// assuming the read permission is granted
	private fun updateDirectoryView()
	{
		if(_currentDir == null || _currentDepth == 0)
		{
			// list storage devices
			toolbar_title.text = "Storage"
			val externalStorageFiles = getExternalFilesDirs(null)
			_dirList.clear()
			for(f in externalStorageFiles)
			{
				val device = f.parentFile.parentFile.parentFile.parentFile // srsl?
				_dirList.add(device)
			}
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
				Toast.makeText(this, "Can't list files", Toast.LENGTH_SHORT).show()
		}
	}

	override fun onBackPressed()
	{
		if(drawer_layout.isDrawerOpen(GravityCompat.START))
		{
			drawer_layout.closeDrawer(GravityCompat.START)
		}
		else if(drawer_layout.isDrawerOpen(GravityCompat.END))
		{
			drawer_layout.closeDrawer(GravityCompat.END)
		}
		else if(_currentDir != null && _currentDepth != 0)
		{
			_currentDir = _currentDir?.parentFile
			_currentDepth--
			updateDirectoryView()
		}
		else
		{
			super.onBackPressed() // exit app
		}
	}
}
