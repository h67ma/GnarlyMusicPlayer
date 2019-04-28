package sancho.gnarlymusicplayer
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : Activity()
{
	companion object
	{
		private const val REQUEST_READ_STORAGE = 42

		// from https://developer.android.com/guide/topics/media/media-formats
		private val SUPPORTED_FILE_EXTENSIONS = arrayOf(
			"3gp",
			"mp4",
			"m4a",
			"aac",
			"ts",
			"flac",
			"gsm",
			"mid",
			"xmf",
			"mxmf",
			"rtttl",
			"rtx",
			"ota",
			"imy",
			"mp3",
			"mkv",
			"wav",
			"ogg",
			"m3u",
			"m3u8"
		)
	}

	private val _dirList = mutableListOf<File>()
	private var _currentDir : File? = null
	private var _currentDepth : Int = 0
	private var _explorerAdapter : ExplorerAdapter? = null
	private var _backPressedOnce : Boolean = false

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		_explorerAdapter = ExplorerAdapter(this, _dirList)
		libraryListView.adapter = _explorerAdapter
		libraryListView.setOnItemClickListener{parent, _, position, _ ->
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
			title = "Storage"
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
			title = _currentDir?.absolutePath
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
		if(_currentDir != null && _currentDepth != 0)
		{
			_backPressedOnce = false
			_currentDir = _currentDir?.parentFile
			_currentDepth--
			updateDirectoryView()
		}
		else
		{
			if(!_backPressedOnce)
			{
				_backPressedOnce = true
				Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
			}
			else
				super.onBackPressed() // exit app
		}
	}
}
