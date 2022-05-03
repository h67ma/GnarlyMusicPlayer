package sancho.gnarlymusicplayer.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Toaster

class AboutSettingsFragment : NestedSettingsFragment()
{
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		setPreferencesFromResource(R.xml.about_preferences, rootKey)

		findPreference<Preference>("version")?.summary = getAppVersion()

		findPreference<Preference>("version")?.setOnPreferenceClickListener {
			Toaster.show(requireContext(), getString(R.string.ur_not_a_developer))
			true
		}

		setupLinkItem("help", "https://github.com/h67ma/GnarlyMusicPlayer/wiki/Help")
		setupLinkItem("bugreport", "https://github.com/h67ma/GnarlyMusicPlayer/issues/new")
		setupLinkItem("repo", "https://github.com/h67ma/GnarlyMusicPlayer")
	}

	private fun getAppVersion(): String
	{
		return context?.packageManager?.getPackageInfo(context?.packageName ?: "", 0)?.versionName ?: "Unknown"
	}

	private fun setupLinkItem(key: String, url: String)
	{
		findPreference<Preference>(key)?.setOnPreferenceClickListener {
			val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
			startActivity(browserIntent)
			true
		}
	}
}
