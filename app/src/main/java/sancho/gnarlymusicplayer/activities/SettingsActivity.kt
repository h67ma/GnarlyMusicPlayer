package sancho.gnarlymusicplayer.activities

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.*
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Toaster
import sancho.gnarlymusicplayer.databinding.ActivitySettingsBinding
import sancho.gnarlymusicplayer.playbackservice.ACTION_UPDATE_MAX_VOLUME
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class SettingsActivity : AppCompatActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		setTheme(AppSettingsManager.restoreAndGetStyleFromPrefs(this))

		super.onCreate(savedInstanceState)
		val binding = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true) // enable "up" action bar action

		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings, SettingsFragment())
			.commit()
	}

	class SettingsFragment : PreferenceFragmentCompat()
	{
		private fun setupLinkItem(key: String, url: String)
		{
			findPreference<Preference>(key)?.setOnPreferenceClickListener {
				val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
				startActivity(browserIntent)
				true
			}
		}

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
		{
			setPreferencesFromResource(R.xml.root_preferences, rootKey)

			findPreference<Preference>("version")?.summary = getAppVersion()

			findPreference<Preference>("version")?.setOnPreferenceClickListener {
				Toaster.show(requireContext(), getString(R.string.ur_not_a_developer))
				true
			}

			// relaunch parent activity after changing style
			findPreference<ListPreference>(getString(R.string.pref_accentcolor))?.setOnPreferenceChangeListener { _, _ ->
				activity?.recreate()
				true
			}

			findPreference<CheckBoxPreference>(getString(R.string.pref_autoclean))?.setOnPreferenceChangeListener { _, newValue ->
				PlaybackQueue.autoClean = (newValue as Boolean) == true

				true
			}

			// note: package name is hardcoded in manifest <queries>
			findPreference<Preference>("eq")?.setOnPreferenceClickListener {
				val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)

				val pm = context?.packageManager
				if (pm != null && eqIntent.resolveActivity(pm) != null)
				{
					// don't pass value if not set - eq app will do some magic and save settings for next time
					if (MediaPlaybackService.audioSessionId != AudioManager.ERROR)
						eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, MediaPlaybackService.audioSessionId)

					eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context?.packageName)
					eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

					// documentation recommends using startActivityForResult but it's deprecated
					// looks to be working alrite with startActivity
					startActivity(eqIntent)
				}
				else
					Toaster.show(requireContext(), getString(R.string.no_eq_found))

				true
			}

			findPreference<CheckBoxPreference>(getString(R.string.pref_ignoreaf))?.setOnPreferenceChangeListener { _, newValue ->
				AppSettingsManager.ignoreAf = (newValue as Boolean) == true

				true
			}

			findPreference<CheckBoxPreference>(getString(R.string.pref_btcrackworkaround))?.setOnPreferenceChangeListener { _, newValue ->
				AppSettingsManager.bluetoothCrackingWorkaround = (newValue as Boolean) == true

				true
			}

			findPreference<CheckBoxPreference>(getString(R.string.pref_inappenabled))?.setOnPreferenceChangeListener { _, newValue ->
				AppSettingsManager.volumeInappEnabled = (newValue as Boolean) == true

				updateAudioService()

				true
			}

			findPreference<SeekBarPreference>(getString(R.string.pref_totalsteps))?.setOnPreferenceChangeListener { _, newValue ->
				val newTotal = newValue as Int
				AppSettingsManager.volumeStepIdx = AppSettingsManager.volumeStepIdx * newTotal / AppSettingsManager.volumeStepsTotal // scale to about the same relative level
				AppSettingsManager.volumeStepsTotal = newTotal

				// also save volumestepidx to preference (so MainActivity won't overwrite it if the service is not running)
				with (PreferenceManager.getDefaultSharedPreferences(context).edit())
				{
					putInt(AppSettingsManager.PREFERENCE_VOLUME_STEP_IDX, AppSettingsManager.volumeStepIdx)
					apply()
				}

				updateAudioService()

				true
			}

			findPreference<CheckBoxPreference>(getString(R.string.pref_lockvolume))?.setOnPreferenceChangeListener { _, newValue ->
				AppSettingsManager.volumeSystemSet = (newValue as Boolean) == true

				true
			}

			findPreference<Preference>("setlockvolume")?.setOnPreferenceClickListener {
				val manager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
				val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)

				with (PreferenceManager.getDefaultSharedPreferences(context).edit())
				{
					putInt(AppSettingsManager.PREFERENCE_VOLUME_SYSTEM_TO_SET, current)
					apply()
				}

				Toaster.show(requireContext(), "Will set to $current")

				true
			}

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
					Toaster.show(requireContext(), "Saved to ${saveFile.absolutePath}")
				}
				catch (e: IOException)
				{
					Toaster.show(requireContext(), getString(R.string.file_save_error))
				}

				true
			}

			setupLinkItem("help", "https://github.com/h67ma/GnarlyMusicPlayer/wiki/Help")
			setupLinkItem("bugreport", "https://github.com/h67ma/GnarlyMusicPlayer/issues/new")
			setupLinkItem("repo", "https://github.com/h67ma/GnarlyMusicPlayer")
		}

		private fun updateAudioService()
		{
			if (MediaPlaybackService.mediaPlaybackServiceStarted)
			{
				val intent = Intent(context, MediaPlaybackService::class.java)
				intent.action = ACTION_UPDATE_MAX_VOLUME
				context?.startService(intent)
			}
		}

		private fun getAppVersion(): String
		{
			return context?.packageManager?.getPackageInfo(context?.packageName ?: "", 0)?.versionName ?: "Unknown"
		}
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			android.R.id.home -> super.onBackPressed()
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}
}
