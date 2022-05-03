package sancho.gnarlymusicplayer.settings

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Toaster
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class BackupSettingsFragment : NestedSettingsFragment()
{
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		setPreferencesFromResource(R.xml.backup_preferences, rootKey)

		findPreference<Preference>("backupqueue")?.setOnPreferenceClickListener {
			val externalStorageVolumes = ContextCompat.getExternalFilesDirs(requireContext(), null)
			val saveFile = File(externalStorageVolumes[0], "queue.m3u8")

			var toWrite = ""
			for (item in PlaybackQueue.queue)
				toWrite += item.path + '\n'

			try
			{
				val os: OutputStream = FileOutputStream(saveFile)
				os.write(toWrite.toByteArray())
				os.close()
				Toaster.show(requireContext(), "Saved to ${saveFile.absolutePath}", Toast.LENGTH_LONG)
			}
			catch (e: IOException)
			{
				Toaster.show(requireContext(), getString(R.string.file_save_error))
			}

			true
		}
	}
}
