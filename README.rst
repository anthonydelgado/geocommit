This attaches geolocation information to git commits you you know where you
where when you coded the stuff.

Developing
==========

1.) python bootstrap.py
2.) ./bin/buildout
3.) ???
4.) # happy hacking!

Building the GeoGitHub chrome extension
---------------------------------------

$> ./bin/crxmake src/chrome-ext/ path/to/geogithub.pem

Crazy ideas
===========

 * foursquare integration
 * 3rd party service with badges:
   * jetsetter badge (commits at five different airports)
   * mountain badge (commits at over 4000ft altitude)
 * geocommit + git-remote-couch + geocouch === awesome!
 * git geolog foo..bar > foobar.kml
 * Chrome/Safari extension to display google maps all over github

Geocommit data format (v1.0)
============================
We store a number of keys with values in git notes or hg commits.
There is a long and a short format. Both define a set of key/value
pairs in no particular order. The format version defines the allowed
keys.

<version> is a version number of the format x.y
<key> is an alphanumeric lowercase identifier without spaces or other special characters except _ and -
<value> must not contain a linebreak, "," or ";"

The short format is:
geocommit(<version>): <key> <value>, ..., <key> <value>;

The long format is, terminated by an empty line:

geocommit (<version)
<key>: <value>
...
<key>: <value>

Version 1.0 of the format defines the keys:
 * long (required) contains longitude value of a coordinate in WGS84
 * lat (required) contains latitude value of a coordinate in WGS84
 * src (required) contains the name of the data provider used to generate the geodata
 * alt (optional) contains altitude in metres
 * speed (optional) speed in metres / second
 * dir (optional) direction of travel
 * hacc (optional) horizontal accuracy of long/lat values in metres
 * vacc (optional) vertical accuracy of altitude value in metres