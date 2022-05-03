package sancho.gnarlymusicplayer.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.R

class WorkaroundsSettingsFragment : NestedSettingsFragment()
{
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		setPreferencesFromResource(R.xml.workarounds_preferences, rootKey)

		findPreference<CheckBoxPreference>(getString(R.string.pref_ignoreaf))?.setOnPreferenceChangeListener { _, newValue ->
			AppSettingsManager.ignoreAf = (newValue as Boolean) == true

			true
		}

		findPreference<CheckBoxPreference>(getString(R.string.pref_btcrackworkaround))?.setOnPreferenceChangeListener { _, newValue ->
			AppSettingsManager.bluetoothCrackingWorkaround = (newValue as Boolean) == true

			true
		}

		findPreference<CheckBoxPreference>(getString(R.string.pref_dontpausemediasess))?.setOnPreferenceChangeListener { _, newValue ->
			AppSettingsManager.noPauseMediaSess = (newValue as Boolean) == true

			true
		}
	}
}
