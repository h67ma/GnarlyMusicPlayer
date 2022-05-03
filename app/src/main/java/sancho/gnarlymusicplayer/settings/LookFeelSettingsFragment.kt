package sancho.gnarlymusicplayer.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R

class LookFeelSettingsFragment : NestedSettingsFragment()
{
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		setPreferencesFromResource(R.xml.look_feel_preferences, rootKey)

		// relaunch parent activity after changing style
		findPreference<ListPreference>(getString(R.string.pref_accentcolor))?.setOnPreferenceChangeListener { _, _ ->
			// if we don't manually pop this, the settings activity will become a bit glitched
			activity?.supportFragmentManager?.popBackStack()
			//activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
			activity?.recreate()
			true
		}

		findPreference<CheckBoxPreference>(getString(R.string.pref_autoclean))?.setOnPreferenceChangeListener { _, newValue ->
			PlaybackQueue.autoClean = (newValue as Boolean) == true

			true
		}
	}
}
