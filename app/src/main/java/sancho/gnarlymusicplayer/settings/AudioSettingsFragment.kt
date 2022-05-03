package sancho.gnarlymusicplayer.settings

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import sancho.gnarlymusicplayer.AppSettingsManager
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Toaster
import sancho.gnarlymusicplayer.playbackservice.ACTION_UPDATE_MAX_VOLUME
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService

class AudioSettingsFragment : NestedSettingsFragment()
{
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		setPreferencesFromResource(R.xml.audio_preferences, rootKey)

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

				// from documentation:
				// "The calling application must use the Activity.startActivityForResult(Intent, int) method"
				// meanwhile it's deprecated :)
				// note: startActivity works too on some versions of Android (and is not deprecated)
				startActivityForResult(eqIntent, 1337)
			}
			else
				Toaster.show(requireContext(), getString(R.string.no_eq_found))

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
			with (PreferenceManager.getDefaultSharedPreferences(requireContext()).edit())
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

		val manager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		val maxVol = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
		val startVolPref = findPreference<SeekBarPreference>(getString(R.string.pref_lockvolume_start))

		startVolPref?.max = maxVol
		startVolPref?.setDefaultValue(maxVol) // why won't this work?
		startVolPref?.setOnPreferenceChangeListener { _, newValue ->
			AppSettingsManager.volumeSystemLevel = newValue as Int

			true
		}
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
}
