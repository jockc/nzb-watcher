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

Put your api keys in the resources dir;  setup the map in api.clj to point to your nzb service(s) and api key paths;

Also in resources dir:
thetvdbapi.txt - for your thetvdb api key

Create your categories; then create your includes.   You can use api/lookup_series_id() from the REPL to get the thetvdb id for a show.

There is a function called backfill() that will attempt to load an entire series.  You currently must call this from the REPL.  Clear out the nzbs dir first.  For each episode backfill() will load the nzb file which has the most downloads (as reported by the nzb service) and which has not already been tried (i.e. in a previous backfill run for this series).  Once backfill returns, look in the nzbs directory.  Delete any incorrect files (wrong language, full season instead of single episode, etc).  Then zip the nzbs dir and manually copy to the hot folder (where sabnzbd watches).  Once sabnzbd is finished download and unpacking everything, run gather.sh.
If there were any failures in the download, it will be detected during gather phase (the gather will not find the file(s)). Gather will update the database to reflect these failures.   Rerunning backfill() will then try to load any missing files.  These steps may need to be repeated a few times.  Also note that many services have a daily cap on downloads which you may hit when you are doing backfills.  When this happens you will start seeing nzbs with 0 bytes.  Just delete those zero byte nzbs, and wait until your download cap is reset before continuing.  


## Options



## Examples


### Bugs

* if pattern and target match your target will be picked up by the gather process (and ultimately deleted)

...

## License

Copyright © 2016 Jock Cooper

Distributed under the Eclipse Public License either version 1.0 

