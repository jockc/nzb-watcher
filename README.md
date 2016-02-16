# nzb-watcher

This is a small program that will watch a newznab service and download NZBs based on a set of regexps.  It has a second mode that will organize and rename the downloaded media files. 

## Installation

clone the repo.

load the resource.sch into postgres to create the nzb_watcher database.  Make sure owner is correct.  Populate the tables as described below (sorry, no easy way to do this yet, just go into psql and insert the data).

edit core.clj and set:
* drop-folder
* usenet-folder
* default-gather-target

make the executable with "lein uberjar"

Running watch.sh will scan the defined categories finding matching nzbs (based on includes table) since the last run.

Running gather.sh will scan the download dir, looking for downloads.  It will gather up the files into specified target dirs, and rename the files to include episode name if available.

(these can be setup to run in your cron tab)

logging is configured in the resources/log4j.properties which you will need to edit for your own system.


## Usage

Database tables to populate:

categories:
*  number - category number eg 5040
* last_seen_dt - how far back to look; updated after every run; set with some initial value

includes:
  
* pattern -   regexp to search for
* extra_pattern - extra regexp which must also match (optional)
* rageid - rageid which must also match (optional); useful because newznab provided rageid
* category_id - id (pk) from category table
* target - target dir to gather to;  make sure your target dir does not match your pattern (eg target 'The Office' and pattern 'the.office')
* prefix - prefix for renaming -  will rename as 'Prefix.S##E##.title.ext' for tv shows
     for non tv shows it will use the dir name + ext
* gather_only_flag - do not watch the newznab server for this pattern, but process if found when gathering.  this can be useful if you download a bunch of things manually, gather mode will collect and rename them
* thetvdbid - optional - used to lookup episode titles

excludes:
*  pattern_regex - any match on these patterns is skipped

Put your api keys in the resources dir:

api.txt for your newznab api key
thetvdbapi.txt - for your thetvdb api key

Create your categories; then create your includes.   You can use api/lookup_series_id() from the REPL to get the thetvdb id for a show.

There is a function called backfill() that will attempt to load an entire series.  You currently must call this from the REPL.  Backfill will try to load the season/episode with the most downloads.  If there are any failures, it will be detected during gather phase (the gather will not find the file(s)).  Rerunning backfill() will try to load any missing files.


## Options



## Examples


### Bugs

* if pattern and target match your target will be picked up by the gather process (and ultimately deleted)

...

## License

Copyright © 2016 Jock Cooper

Distributed under the Eclipse Public License either version 1.0 

