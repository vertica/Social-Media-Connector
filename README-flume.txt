In order to run the Twitter-Vertica flume plugin, you must first edit the flume config file at third-party/conf/flume.conf

1. Provide the consumerKey, consumerSecret, accessToken, and accessTokenSecret. You get these by creating a Twitter Application. To do so, go to http://dev.twitter.com and sign in using your Twitter username and password. Create a new application
a. Give it any name and description
b. Set the website to https://api.twitter.com/oauth/authenticate and the callback URL to https://stream.twitter.com/1.1/statuses/filter.json
c. Agree to the terms of service and create the application
d. Create my access token. You should now have a Consumer key, Consumer secret, Access token, and Access token secret
e. Go to Settings and enable Allow this application to be used to Sign in with Twitter

2. Edit TwitterAgent.sink.Vertica.directory -- the directory for the twitter json files to go before they are parsed into Vertica

3. Edit the JDBC settings -- VerticaHost, port, databaseName, username, password, tableName, parserName

4. Fill in the usernames you want to follow and/or the keywords you want to filter by. If you provide username Vertica and keyword database, you will receive all tweets from Vertica and all tweets containing the word database. If you leave both blank, you will receive a random sampling of tweets.

5. [Optional] Edit TwitterAgent.sources.Twitter.logging to turn on/off debug logging. All logging is in dist/apache-flume-1.3.1-bin/logs/flume.log. If you turn on logging, each tweet that is downloaded will have its text logged. Otherwise, there will just be logging for setup, errors, every time you switch files and load tweets into Vertica, and teardown.

6. [Optional] Set TwitterAgent.sinks.Vertica.rollInterval and/or TwitterAgent.sinks.Vertica.batchSize to control the number of seconds or the number of tweets between batches. Defaults to 30 seconds and 100 tweets. The more limiting one ends the current batch.

Now, compile the Twitter-Vertica flume plugin (make flume), create a table with the tableName you provided in flume.conf, and install the TweetParser (make install)

Go to dist/apache-flume-1.3.1-bin. To start the Twitter-Vertica flume plugin, run:
./start-stop start TwitterAgent 

To stop it, run:
./start-stop stop TwitterAgent
