# Photo Grid Finder

Android app that reads the GPS position saved in a photo and shows it as
latitude/longitude and a 10-figure Ordnance Survey grid reference, or
**No Location** if the photo has none.

## Install

Open **Releases** on this page on your Android phone, download
`PhotoGridFinder.apk` from the latest release and open it. Android will ask
you to allow installs from your browser the first time.

When the app asks for access to photos, choose **Allow all**. That lets it
read the location saved in photos, which Android hides from apps without
that permission.

## Build

Every push to `main` builds the app with GitHub Actions and publishes a new
release.
