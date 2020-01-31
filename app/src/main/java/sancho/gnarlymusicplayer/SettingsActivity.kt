package sancho.gnarlymusicplayer

import android.app.AlertDialog
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
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

			findPreference<Preference>("version")?.setOnPreferenceClickListener { _ ->
				Toast.makeText(context, getString(R.string.ur_not_a_developer), Toast.LENGTH_SHORT).show()
				true
			}

			// relaunch parent activity after changing style
			findPreference<androidx.preference.ListPreference>("accentcolor")?.setOnPreferenceChangeListener { _, _ ->
				activity?.recreate()
				true
			}

			findPreference<Preference>("help")?.setOnPreferenceClickListener { _ ->
				AlertDialog.Builder(context)
					.setTitle(getString(R.string.about))
					.setMessage(getString(R.string.about_message))
					.setPositiveButton(getString(R.string.close), null)
					.create()
					.show()
				true
			}

			findPreference<Preference>("repo")?.setOnPreferenceClickListener { _ ->
				val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/szycikm/GnarlyMusicPlayer"))
				startActivity(browserIntent)
				true
			}

			findPreference<Preference>("eq")?.setOnPreferenceClickListener { _ ->
				val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)

				val pm = activity?.packageManager
				if (pm != null && eqIntent.resolveActivity(pm) != null)
				{
					// don't pass value if not set - eq app will do some magic and save settings for next time
					if (App.audioSessionId != AudioManager.ERROR)
						eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, App.audioSessionId)

					eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context?.packageName)
					eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
					startActivityForResult(eqIntent, App.INTENT_LAUNCH_EQ)
				}
				else
					Toast.makeText(context, getString(R.string.no_eq_found), Toast.LENGTH_SHORT).show()

				true
			}
		}

		private fun getAppVersion(): String
		{
			return activity?.packageManager?.getPackageInfo(activity?.packageName, 0)?.versionName ?: "Unknown"
		}
	}
}
