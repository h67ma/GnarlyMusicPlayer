package sancho.gnarlymusicplayer.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		setTheme(AppSettingsManager.restoreAndGetStyleFromPrefs(this))

		super.onCreate(savedInstanceState)
		val binding = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true) // enable "up" action bar action

		// load root prefs
		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings, SettingsFragment())
			.commit()
	}

	override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
		val args = pref.extras
		val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)
		fragment.arguments = args
		title = pref.title

		// replace the existing fragment with the new fragment
		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings, fragment)
			.addToBackStack(null)
			.commit()
		return true
	}

	class SettingsFragment : PreferenceFragmentCompat()
	{
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
		{
			setPreferencesFromResource(R.xml.root_preferences, rootKey)
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
