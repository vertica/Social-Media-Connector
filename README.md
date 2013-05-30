HP Vertica - Social Media Connector
===================================

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

license/PersonalCLA.pdf: If you are contributing for yourself
license/CorporateCLA.pdf: If you are contributing on behalf of your company

Overview
--------
The HP Vertica Social Media Connector includes an application ([Apache Flume](http://flume.apache.org)) and an [HP Vertica](http://vertica.com) User Defined eXtension (TweetParser) that allow you to continuously download tweets from [Twitter](http://twitter.com) through Flume and automatically load them into an HP Vertica Database using TweetParser(). TweetParser() is called automatically by Flume so no user intervention is required to load tweets in the database once you start the Flume process. 

You can configure Flume to search for specific keywords, read tweets from a specific Twitter user, or get a random sample of the Twitter _Firehose_. Flume downloads the tweets to a series of files. As the files are saved, a HP Vertica UDx function uploads the contents of the file to the HP Vertica Database.

Requirements
------------
The HP Vertica Social Media Connector requires the following:

* HP Vertica Version 6.1.2 - For platform requirments esee: [Platform Requirements](http://my.vertica.com/docs/6.1.x/HP_Vertica_EE_6.1.x_Supported_Platforms.pdf)
* Oracle Open Java JDK 1.6
* Build Tools:
	* gcc
	* gcc-c++
	* make
	* Any Dependencies on the above
* A (free) Twitter Developer account

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

1. Edit `third-party/conf/flume.conf` with a text editor.
2. Using the values found on the details page of your Twitter application, provide the following values for the following flume source parameters:
	* `TwitterAgent.sources.Twitter.consumerKey` = **Consumer key** value
	* `TwitterAgent.sources.Twitter.consumerSecret` = **Consumer secret** value
	* `TwitterAgent.sources.Twitter.accessToken` = **Access token** value
	* `TwitterAgent.sources.Twitter.accessTokenSecret` = **Access token secret** value
3. Specify `TwitterAgent.sources.Twitter.keywords` and/or `TwitterAgent.sources.Twitter.follow`: 
	* If you specify the _keywords_ value, then Flume returns tweets that contain those keywords. If you want to use hashtags, omit the # sign from them. Use a comma separated list. For example: hockey, stanley cup, playoffs
	* If you specify the _follow_ value, then Flume only returns tweets from that user. If you specify _follow_ and _keywords_ is not assigned a value, then you get all tweets from that user. If _keywords_ is defined, then you only get tweets from the user defined in _follow_ that contain the keywords in _keywords_.
	* Ommitting both _keywords_ and _follow_ settings results in a random sample of all tweets being set. AKA the **firehose** of Twitter streams.
4. You can set `TwitterAgent.sources.Twitter.logging` to true to log the text of each tweet in the log file. However, setting this to true can rapidly fill your disk with log messages. Setting to false still logs normal operation of flume.
5. Provide values for the following flume sink parameters:
	* `TwitterAgent.sinks.Vertica.directory` - The location to save the text files from flume as they arrive. The user who runs the flume process must have write access to this location. Defaults to the files directory at the top level of the HP Vertica Social Media Connector directory.
	* `TwitterAgent.sinks.Vertica.rollInterval` - The number of seconds between batches. Used in conjuction with below, whichever occurs first causes an output file to be written.
	* `TwitterAgent.sinks.Vertica.batchSize` - The number of tweets between each batch.
	* `TwitterAgent.sinks.Vertica.VerticaHost` - hostname or IP address if the host running an HP Vertica Server.
	* `TwitterAgent.sinks.Vertica.port` - HP Vertica Database Port. Default is 5433.
	* `.databasename`, `.username`, `.password` - The database and credentials required to access the database.
	* `TwitterAgent.sinks.Vertica.tableName` - The table name into which the tweets are loaded. Provide the name now, you will create the table in HP Vertica later.


Building and Installing
-----------------------
You must build flume from source. Flume required Oracle Open JDK 6.1. Verify that jar is in your path before building or the build fails. For example:
`export PATH=$PATH:/usr/java/jdk1.6.0_11/bin/`

Navigate to the top level directory and run: 'make flume'

After the build completes, you can run 'make install' if the HP Vertica database exists on the same host and you are logged in as a dbadmin user.

Otherwise, copy the VTweetParser.so file over to your vertica node and install the library:
```
SELECT set_config_parameter('UseLongStrings', 1);
CREATE LIBRARY VTweetLib AS /path/to/VTweetParser.so;

CREATE PARSER TweetParser AS LANGUAGE 'C++' NAME 'TweetParserFactory' LIBRARY VTweetLib NOT FENCED;

```


Creating Tables for Tweets
---------------------------
The HP Vertica Social Media Connector requires you to create a table to store the tweets that are read in by the HP Vertic TweetParser() function.

Flume automatically gets all of the data associated with a tweet. The fields and their sizes that Twitter returns and are documented at https://dev.twitter.com/docs/platform-objects/tweets. 

You must match the field name the Twitter provides with the column name in your table. You must also size the column to hold the data Twitter provides.

For example, the Twitter id field is a 64-bit number. You can hold this data in an INT datatype.

You do not need to create a column for every field that Twitter provides. If the TweetParser() does not match a field name with a column name, then the data in that field is dropped.

Some Twitter fields have multiple sub-fields, which in turn can have additional sub-fields. 

For example, the **user** field has sub-fields for _name_, _screen_name_, and _location_ (in addition to [many others](https://dev.twitter.com/docs/platform-objects/users)). To create columns to store these subfields, you name the column with a "." separating the field and sub-field names, For example:
```
create table tweets( 
	"user.name" varchar(144), 
	"user.screen_name" varchar(144), 
	"user.location" varchar(144), 
); 
```

It is also useful to capture the _lang_ field, so you can easily filter tweets by language. 

Example of a basic table to store tweets:
```
create table tweets3(
    id int,
	created_at varchar(144), 
	"user.name" varchar(144), 
	"user.screen_name" varchar(144), 
	text varchar(500),
	"user.location" varchar(144), 
	"coordinates.coordinates.0" numeric, 
	"coordinates.coordinates.1" float, 
	lang varchar(5)
); 

```

After you create your tweet table (named the same as  _TwitterAgent.sinks.Vertica.tableName_ in flume.conf), then you can start Flume.

Starting and Stopping the Agent
--------------------------------
You start and stop the Flume's Twitter agent using the start-stop script in `dist/apache-flume-1.3.1-bin'.

To start:
./start-stop start TwitterAgent

To stop:
./start-stop stop TwitterAgent

The script detaches the process from the current terminal session so that it remains running if you log out of the session.


Querying Tweet Tables
----------------------

When querying tweet tables, it is useful to filter on the lang attribute so that you only recieve results for your language. For example:

`select created_at, "user.screen_name", text from tweets where lang = 'en' order by created_at limit 10;`


Troubleshooting
----------------

The Flume log is available in: `dist/apache-flume-1.3.1-bin/logs/flume.log`

The Flume log rolls over when it reaches a certain size and the old log is gzipped.


