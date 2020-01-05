package sancho.gnarlymusicplayer

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.android.synthetic.main.activity_settings.*


class SettingsActivity : AppCompatActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
		val accentColorKey = sharedPref.getString("accentcolor", "green") ?: "green" // what's your problem kotlin?
		setTheme(getStyleFromPreference(accentColorKey))

		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true) // enable "up" action bar action

		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings, SettingsFragment())
			.commit()
	}

	class SettingsFragment : PreferenceFragmentCompat()
	{
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
		{
			setPreferencesFromResource(R.xml.root_preferences, rootKey)

			findPreference<Preference>("version")?.summary = getAppVersion()

			// relaunch parent activity after changing style
			findPreference<androidx.preference.ListPreference>("accentcolor")?.setOnPreferenceChangeListener{_, _ ->
				activity?.recreate()
				true
			}
		}

		private fun getAppVersion(): String
		{
			return activity?.packageManager?.getPackageInfo(activity?.packageName, 0)?.versionName ?: "Unknown"
		}
	}
}
