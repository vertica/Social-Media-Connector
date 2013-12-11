HP Vertica - Social Media Connector
===================================

> _Copyright 2013 - HP Vertica - Hewlett-Packard Development Company, L.P.
The information contained herein is subject to change without notice. HP shall not be liable for technical or editorial errors or omissions contained herein._

1. Contributing
2. Overview
3. Requirements
4. Configuring Flume
5. Building and Installing
6. Creating Tables for Tweets
7. Starting and Stopping the Agent
8. Querying Tweet Tables
9. Troubleshooting

Contributing
--------
IMPORTANT: If you wish to contribute anything to this repository, in
order for us to accept your pull request you MUST sign and send a copy
of the appropriate Contributor License Agreement to Vertica
(contribs@vertica.com):

* license/PersonalCLA.pdf: If you are contributing for yourself
* license/CorporateCLA.pdf: If you are contributing on behalf of your company

Overview
--------
The HP Vertica Social Media Connector includes [Apache Flume](http://flume.apache.org) with an HP Vertica Plugin, and an [HP Vertica](http://vertica.com) User Defined eXtension (TweetParser). This suite allows you to continuously download tweets from [Twitter](http://twitter.com) through Flume and automatically load them over JDBC into an HP Vertica Database using TweetParser(). TweetParser() is called automatically by the Flume process so no user intervention is required to load tweets in the database once you start the Flume. 

You can configure Flume to search for specific keywords, read tweets from a specific Twitter user, get the Twitter _Firehose_ (a random sample of 1 percent of all tweets). Flume downloads the tweets to a series of files. As the files are saved, TweetParser() uploads the contents of the file to the HP Vertica Database.

Requirements
------------
The HP Vertica Social Media Connector requires the following:

* HP Vertica Version 6.1.2+ or 7.0.x - For platform requirements see: [Platform Requirements for 6.1.x](http://my.vertica.com/docs/6.1.x/HP_Vertica_EE_6.1.x_Supported_Platforms.pdf) or [Platform Requirements for 7.0.x](https://my.vertica.com/docs/7.0.x/HTML/index.htm#Authoring/SupportedPlatforms/SupportedPlatforms.htm)
* Full 1.6 JDK (java-1.6.0-openjdk-devel)
* Apache Flume 1.3.1
* JSONCpp 0.6.0 RC2
* SCONS Local 2.3.0
* Build Tools:
	* gcc
	* gcc-c++
	* make
	* Any Dependencies on the above
* A (free) Twitter Developer account


Obtain Third-party Dependencies
-------------------------------
* JSONCpp:
	* Required package: jsconcpp-src-0.6.0-rc2.tar.gz
	* Project Homepage: http://sourceforge.net/projects/jsoncpp/
	* Note: The latest "released" version of this project is 0.5.0. 0.6.0rc2 can be found under the 'Files' tab on the project homepage.

* SCONS:
	* Required package: scons-local-2.3.0-tar.gz
	* Project homepage: http://www.scons.org/download.php
	* Note: The local package is required. The base package is not supported.

* Flume:
	* Project homepage: http://flume.apache.org/
	* Required package: apache-flume-1.3.1-bin.tar.gz

* JDK:
	* a Full 1.6 JDK is required. The java-1.6.0-openjdk-devel package is acceptable.

Prepare Third-party Software
----------------------------
1. Make a `dist` directory in `Social-Media-Connector/third-party`.
2. Copy each of the required packages to the third-party/dist directory. Do not unzip or untar the packages.
3. In the third-party directory type `make`.

Configuring Your Twitter Account
---------------------------------
You must first configure an _application_ in your Twitter account before you can build and install the HP Vertica Social Media Connector. The application connection allows your Social Media Connector to connect to Twitter using your Twitter credentials and access the Twitter Streaming API.


1. Log in to http://dev.twitter.com with your Twitter credentials.
2. Click your user icon in the upper-right of the page and select **My Applications*.
3. Click **Create New Application**.
4. Provide a **Name** and **Description** for your application.
5. For the **Website** enter: `https://api.twitter.com/oauth/authenticate`.
6. For the **Callback URL** enter: `https://stream.twitter.com/1.1/statuses/filter.json`.
7. Agree to the terms (if you agree!) and click **Create your Twitter application**. The application is created and you are provided with authentication keys.
8. Click **Create my access token**. To create an access token that allows the application using these keys to access your account so it can log into Twitter.
9. Click the _Settings_ tab.
10. Check the box for _Allow this application to be used to Sign in with Twitter_.
11. Click **Update this Twitter Application's settings**.
12. Click on the Details tab again and leave that page up while you configure flume. You need to copy several of the OAuth keys and the access tokens to your Flume configuration file.



Configuring Flume
------------------
You must edit the Flume configuration file and provide details for your Twitter account, HP Vertica database, and preferences:

1. Edit `third-party/conf/flume.conf` with a text editor. (Note after you build Flume this file is copied to `dist/apache-flume-1.3.1-bin/conf`)
2. Using the values found on the details page of your Twitter application, provide the following values for the following flume source parameters:
	* `TwitterAgent.sources.Twitter.consumerKey` = **Consumer key** value
	* `TwitterAgent.sources.Twitter.consumerSecret` = **Consumer secret** value
	* `TwitterAgent.sources.Twitter.accessToken` = **Access token** value
	* `TwitterAgent.sources.Twitter.accessTokenSecret` = **Access token secret** value
3. Specify `TwitterAgent.sources.Twitter.keywords` and/or `TwitterAgent.sources.Twitter.follow`: 
	* If you specify the _keywords_ value, then Flume returns tweets that contain those keywords. If you want to use hashtags, omit the # sign from the word. Use a comma separated list. For example: hockey, stanley cup, playoffs. Note that you are limited to 200 keywords in the list.
	* If you specify the _follow_ value, then Flume returns tweets from those users. User a comma separated list of screen names.If you specify _follow_, and _keywords_ is not assigned a value, then you get all tweets from the user(s). If _keywords_ is also defined, then you  get tweets from the user(s) defined in _follow_, and tweets from ***all*** users that contain the keywords in _keywords_. Note that you are limited to 400 screen names in the list.
	* Ommitting both _keywords_ and _follow_ settings results in getting the Twitter **firehose**, which is a 1 percent random sampling of all tweets being tweeted.
4. You can set `TwitterAgent.sources.Twitter.logging` to true to log the text of each tweet in the log file. However, setting this to true can rapidly fill your disk with log messages. Setting to false still logs normal operation of flume.
5. Provide values for the following flume sink parameters:
	* `TwitterAgent.sinks.Vertica.directory` - The location to save the text files from flume as they arrive. The user who runs the flume process must have write access to this location. If you omit this directory, then a Java exception occurs "Directory may not be null". By default this is set to a directory named `files` inside of `Social-Media-Connector`. You must create this directory.
	* `TwitterAgent.sinks.Vertica.rollInterval` - The number of seconds between batches. Used in conjunction with below, whichever occurs first causes an output file to be written. The default is 10 seconds.
	* `TwitterAgent.sinks.Vertica.batchSize` - The number of tweets between each batch. The default is 10,000 tweets.
	* `TwitterAgent.sinks.Vertica.VerticaHost` - hostname or IP address if the host running an HP Vertica Server.
	* `TwitterAgent.sinks.Vertica.port` - HP Vertica Database Port. Default is 5433.
	* `.databasename`, `.username`, `.password` - The database and credentials required to access the database.
	* `TwitterAgent.sinks.Vertica.tableName` - The table name into which the tweets are loaded. Provide the name now, you will create the table in HP Vertica later.
	* 
	Note: The parameter `TwitterAgent.sinks.Vertica.cleanup` removes the JSON files stored in the `TwitterAgent.sinks.Vertica.directory` directory after the data is loaded into HP Vertica. If you want to retain those files, then change the value to _false_. Note that you may eventually fill up your filesystem with JSON files if you do not manually prune the files on a regular basis.


Building and Installing
-----------------------

The Flume build requires a 1.6 full JDK. To install on RedHat based systems, use the command `yum install java-1.6.0-openjdk-devel`. Verify that the path to javac in your path before building or the build fails. 
Navigate to the top level directory of the Social Media Connector and run: 'make flume'

After the build completes, run 'make install' if the HP Vertica database exists on the same host and you are logged in as a dbadmin user. Otherwise, copy the VTweetParser.so file over to your Vertica node and install the library:
```
SELECT set_config_parameter('UseLongStrings', 1);
CREATE LIBRARY VTweetLib AS /path/to/VTweetParser.so;

CREATE PARSER TweetParser AS LANGUAGE 'C++' NAME 'TweetParserFactory' LIBRARY VTweetLib NOT FENCED;

```

Proxy Server Configuration for Flume
------------------------------------

If you must use a proxy server to connect to Twitter, then edit 'Social-Media-Connector/dist/apache-flume-1.3.1-bin/flume-ng-agent' and add the following arguments:

* `-Dtwitter4j.http.proxyHost=` _host_ 
* `-Dtwitter4j.http.proxyPort=` _port_
* `-Dtwitter4j.http.proxyUser=` _username_ (if required)
* `-Dtwitter4j.http.proxyPassword=` _password_ (if required)
* 
Replace _host_, _port_, _username_, and _password_ with values for your network.


Creating Tables for Tweets
---------------------------
The HP Vertica Social Media Connector requires you to create a table to store the tweets that are copied in by the HP Vertica TweetParser() UDx function.

Flume automatically gets all of the data associated with a tweet. The fields and their sizes that Twitter returns are documented at https://dev.twitter.com/docs/platform-objects/tweets. Consult this page for details on how to create specific columns for the fields you want to capture. 

You must match the column name in your table to the field name that Twitter provides. You do not need to create a column for every Twitter field that is sent. If the TweetParser() does not match a field name with a column name, then the data in that field is not copied into HP Vertica. You must also size the table column to hold the data Twitter provides. For example, the Twitter id field is a 64-bit number. You can hold this data in an INT datatype.

If the tweet does not contain the data for a defined column, or the column name and/or type is incorrect, then NULL is loaded for that column.

Note that although tweets are limited to 140 characters, characters are not the same as bytes. Some tweets may contain multi-byte characters. To account for this, create the column for your tweet text field with enough bytes to handle multi-byte characters. For example:
`text varchar(500)`

### Fields with Sub-fields

Some Twitter fields have multiple sub-fields, which in turn can have additional sub-fields. For example, the **user** field has sub-fields for _name_, _screen_name_, and _location_ (in addition to [many others](https://dev.twitter.com/docs/platform-objects/users)). To create columns to store these subfields, you name the column with a "." separating the field and sub-field names, For example:
```
create table tweets( 
	"user.name" varchar(144), 
	"user.screen_name" varchar(144), 
	"user.location" varchar(144), 
	...
); 
```
### Fields with Arrays

Some twitter fields are an array that can contain multiple values. You access the array items by position. For example, if the field name is NAME and it contains an array of items named text of type VARCHAR, then you would store the items of the array by creating columns for each NAME item, starting at 0:
```
"NAME.0.text" VARCHAR,
"NAME.1.text" VARCHAR,
...
"NAME.NAME.N.text" VARCHAR,
```

The Twitter coordinates field contains an array named coordinates and that array contains exactly two items (a longitude and latitude value) of type float. See https://dev.twitter.com/docs/platform-objects/tweets#obj-coordinates for the details of the contents of the coordinates field. To store this data, your Twitter table must have the following columns defined:
```
"coordinates.coordinates.0" float,
"coordinates.coordinates.1" float,
```

Similarly, the entities field contains three sub-fields that contain arrays:
* hashtags
* urls
* user_mentions

See https://dev.twitter.com/docs/platform-objects/entities for details on how Twitter formats these fields in JSON.

These subfields are unbounded (within the limits of 140 characters), therefore, if you need to capture these fields then you should create an appropriate amount of columns, but within reason. Keep in mind that you can always search inside the tweet itself to obtain these items. hashtags start with a #, user_mentions start with an @, and urls start with 'http://'. But if you still wanted to capture, for example, the first two hashtags used in the tweet, then you would need to create the following columns:
```
"entities.hashtags.0.text" VARCHAR(144)
"entities.hashtags.1.text" VARCHAR(144)
```

### The created_at Field

When creating the table to store your tweets you can create the created_at column in one of three ways:
* as a VARCHAR(144) - a simple string
* as a timestamp datatype 
* as a timestamptz datatype - timestamp with timezone information

If you want to use any of the date/time functions then you should choose the timestamp or timestamptz data types for the column.

When the tweet is stored in Vertica, the times are converted to local system time for the timestamp and timestamptz data types.

### The lang Field

It is also useful to capture the _lang_ field, so you can easily filter tweets by language. Simple create your table with:

`lang varchar(5)`

### Retweeted Statuses and Truncation
Tweets are limited to 140 characters. Some Twitter users add text to tweets that they are re-tweeting. If the user's additional text and the retweeted text result in more than 140 characters, then the re-tweeted text is truncated. However, you can still obtain the full text of the original tweet by capturing the retweeted_status.text field. Create your table with the following column to obtain the full text of the original tweet:

`"retweeted_status.text" varchar(500)`


### Example of a basic table to store tweets:
The following is a sample table definition that captures most of the basic information available in the data returned by Flume.
```
create table tweets(
	id int,
	created_at timestamptz,
	"user.name" varchar(144),
	"user.screen_name" varchar(144),
	text varchar(500),
	"retweeted_status.retweet_count" int,
	"retweeted_status.id" int,
	"retweeted_status.favorite_count" int,
	"retweeted_status.text" varchar(500),
	"user.location" varchar(144),
	"coordinates.coordinates.0" float,
	"coordinates.coordinates.1" float,
	"entities.hashtags.0.text" VARCHAR(144),
	"entities.hashtags.1.text" VARCHAR(144),
	lang varchar(5)
);

```

### Example Script to Create a Table

There is an example script to create a basic table in `examples/create_tweet_table.sql`. You can run this script in vsql with the command `\i examples/create_tweet_table.sql`

After you create your tweet table (named the same as  _TwitterAgent.sinks.Vertica.tableName_ in flume.conf), then you can start Flume and begin loading tweets into your HP Vertica database.

Starting and Stopping the Agent
--------------------------------
Flume uses an agent process to run its tasks. You start and stop the Flume's Twitter agent (named TwitterAgent) using the start-stop script in `dist/apache-flume-1.3.1-bin'. TwitterAgent connects to Twitter, downloads the tweets to the _TwitterAgent.sinks.Vertica.directory_ directory, then calls TweetParser() to load the tweets into HP Vertica.

To start:
`./start-stop start TwitterAgent`

To stop:
`./start-stop stop TwitterAgent`

The script detaches the process from the current terminal session so that it remains running if you log out of the session. 


Querying Tweet Tables
----------------------

When querying tweet tables, it is useful to filter on the lang attribute so that you only recieve results for your language. For example:

`select created_at, "user.screen_name", text from tweets where lang = 'en' order by created_at limit 10;`


Troubleshooting
----------------

### Testing TweetParser()

You can test TweetParser() by manually calling it in vsql with the name of one of the data files downloaded by Flume. For example:

`copy tweets from '/home/dbadmin/1370634848147-1' parser TweetParser();`

Vertica responds with the number of rows loaded.
### Logs

The Flume log is available in: `dist/apache-flume-1.3.1-bin/logs/flume.log`

The Flume log rolls over when it reaches a certain size and the old log is gzipped.

### Modifying flume.conf

You can modify your flume config file by editing `dist/apache-flume-1.3.1-bin/conf/flume.conf`. However, you must first stop flume using the start-stop script (`start-stop stop TwitterAgent`). If you do not stop flume prior to modifying flume.conf, then you may receive double results for tweets that match both the old config and the new config. Stop and start flume to correct this. Consult the flume.log file to verify the changes were valid and were applied. Note that if you rebuild Flume, then the flume.conf file from `third-party/conf` is copied over the flume.conf in `dist/apache-flume-1.3.1-bin/conf/flume.conf`.

### Malformed Tweets
If tweets come in malformed, or are malformed in a tweet file that you are importing directly using TweetParser() in vsql, then the malformed tweets are not loaded into Vertica. The tweets that are not loaded are logged in the `CopyErrorLogs` directory, for example: `/home/dbadmin/exampledb/catalog/exampledb/v_exampledb_node0004_catalog/CopyErrorLogs/`

