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

## Bookshelf (second app, `bookshelf/`)

Android reading list. Type an author's name or a book title; the app looks the
author up on [Open Library](https://openlibrary.org) and, if the text could
mean more than one author, asks which you meant. All of that author's books are
then added to your list, each **unread** until you tick it.

- Search box filters the whole list by any author or title keywords
  (case and accent insensitive; every keyword must match).
- Tap a book to tick it read and to add short comments (200 characters).
  Each comment records the date and time and, if you allow location access and
  the phone has a fix, where you were. Long-press a comment to delete it.
- Data is stored on the phone only. Lookups need an internet connection.

Install `Bookshelf.apk` from the latest release, the same way as above.
