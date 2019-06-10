Gnarly Music Player is a lightweight folder music player, focused on quick, intuitive navigation.

# Features #
* No library
* No tags
* No album art
* No sound effects/equalizer
* No playlists
* No ads
* Based on folder structure
* Easly editable playing queue
* Simple search
* Simple bookmarks
* Media buttons support
* Designed for convenience and simplicity
* Customizable accent colour
* Open source

# Demo #
TODO

# Download #
[Signed .apk](https://github.com/szycikm/GnarlyMusicPlayer/releases/latest)

# Description #
This music player is highly inspired by [Folder Music Player](https://play.google.com/store/apps/details?id=com.suphi.foldermusicplayerunlocker) by Suphi (free version no longer available on Play Store). It lacked a few essential (in my opinion) features to become perfect - search, bookmarks and saving scroll position of previous dir. I used it for a few years, because I couldn't find any music player that was even half as convenient to use. Finally I made my own music player and I'm very happy with the result.

In my opinion mobile music player needs just few features, and anything more than that just makes it harder to use - I want to be able to quickly change my playing queue, not watch album art, lyrics, tags, or animations. Most music players on the Play Store are very feature-rich.

This was my first Kotlin project. I'm positively surprised by Kotlin - it has some quirks, like the absence of static keyword, but overall I had much better time working with it than with Java.

I regret nothing.

# Possible future improvements #
* Seek bar
* Fully recursive search && dir add

# Very short Q&A #

**Q:** Why??  
**A:** I wanted a perfect music player for Android. I made this mostly for myself and my weird music app needs. I doubt I'll need to switch to another music player app for a looong time.

**Q:** Why is [feature name] missing?  
**A:** I specifically only implemented features I personally need. Anything else would just disturb me and also require more time to implement.

**Q:** Why search is only one dir level deep?  
**A:** You probably more-less know where is the song you're looking for. It's your offline music library after all. First-level dirs are for a case when, for example, you have many albums by one artist but can't remember which one has a specific song.

**Q:** Why does adding folders not include subdirs?  
**A:** I don't think it's necessary. The way I use a music player (and the way I intend this app to be used) is to keep the queue relatively short (<100 tracks or something like that) and edit it frequently. There's no point in adding half your music library to queue with one tap - the list would be too long and you'd probably only listen to some part of it before changing your mind.
Also I'm lazy.

**Q:** Why is the interface not user-friendly for new users?  
**A:** I know that not every action is clearly labeled (for example the two sliding navs on left and right can be opened only by sliding from left on right, and there's no indication that they exist on main screen), but it's on purpose. Of course, I could've added some kind of button on the action menu, but this kind of thing takes up space for controls that you actually use. This approach might not be very user-friendly for new users, but this app is not meant for that - it's neant for quick operation. Besides, everything is explained in the help dialog.

**Q:** Why is this not on the Play Store?  
**A:** I don't feel like paying 25 bucks just to have 5 people download this.

**Q:** Why there are no media buttons in main app interface????  
**A:** All buttons are in the notification, so you can access them everywhere in the system. There's no reason to have a copy of them taking space from the main app interface.

**Q:** Why does the notification use custom layout instead of the standard media one?  
**A:** Let's just say I don't like that "media layout". Buttons are too small and there's a timer showing how long ago the notification appeared for some reason.

# Tested on #
* wt88047/7.1.2/LineageOS 14.1
