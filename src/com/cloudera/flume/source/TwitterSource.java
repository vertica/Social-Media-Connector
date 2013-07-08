/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.flume.source;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.FilterQuery;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.json.DataObjectFactory;

/**
 * A Flume Source, which pulls data from Twitter's streaming API. Currently,
 * this only supports pulling from the sample API, and only gets new status
 * updates.
 */
public class TwitterSource extends AbstractSource
    implements EventDrivenSource, Configurable {
  
  private static final Logger logger =
      LoggerFactory.getLogger(TwitterSource.class);

  /** Information necessary for accessing the Twitter API */
  public String consumerKey;
  public String consumerSecret;
  public String accessToken;
  public String accessTokenSecret; 
  private boolean logging;
  
  public String BATCH_SIZE_KEY = "batchSize";
  public long DEFAULT_BATCH_SIZE = 1000L;
  
  private String[] keywords;
  private String[] follow_usernames;
  private long[] follow_ids;
  
  /** The actual Twitter stream. It's set up to collect raw JSON data */
  private final TwitterStream twitterStream = new TwitterStreamFactory(
      new ConfigurationBuilder()
        .setJSONStoreEnabled(true)
        .setIncludeEntitiesEnabled(true)
        .build()).getInstance();

  /**
   * The initialization method for the Source. The context contains all the
   * Flume configuration info, and can be used to retrieve any configuration
   * values necessary to set up the Source.
   */
  @Override
  public void configure(Context context) {
    consumerKey = context.getString(TwitterSourceConstants.CONSUMER_KEY_KEY);
    consumerSecret = context.getString(TwitterSourceConstants.CONSUMER_SECRET_KEY);
    accessToken = context.getString(TwitterSourceConstants.ACCESS_TOKEN_KEY);
    accessTokenSecret = context.getString(TwitterSourceConstants.ACCESS_TOKEN_SECRET_KEY);
    logging = context.getBoolean("logging", false);
    
    // parse the keywords
    String keywordString = context.getString(TwitterSourceConstants.KEYWORDS_KEY, "");
    if (keywordString.length() > 0) {
	    keywords = keywordString.split(",");
	    for (int i = 0; i < keywords.length; i++) {
	      keywords[i] = keywords[i].trim();
	    }
    } else
    	keywords = new String[0];
    
    
    Twitter twitter = new TwitterFactory().getInstance();
    twitter.setOAuthConsumer(consumerKey, consumerSecret);
    AccessToken token = new AccessToken(accessToken, accessTokenSecret);
    twitter.setOAuthAccessToken(token);
    
    // parse the screen names to follow, convert them to user ids
    String followString = context.getString(TwitterSourceConstants.FOLLOW_KEY, "");
    if (followString.length() > 0) {
    	follow_usernames = followString.split(",");
    	follow_ids = new long[follow_usernames.length];
	    for (int i = 0; i < follow_usernames.length; i++) {
	      follow_usernames[i] = follow_usernames[i].trim();
	      try {
			follow_ids[i] = twitter.showUser(follow_usernames[i]).getId();
			} catch (TwitterException e) {
				e.printStackTrace();
			}
	    }
    } else
    	follow_ids = new long[0];    

  }

  /**
   * Start processing events. This uses the Twitter Streaming API to sample
   * Twitter, and process tweets.
   */
  @Override
  public void start() {
    // The channel is the piece of Flume that sits between the Source and Sink,
    // and is used to process events.
    final ChannelProcessor channel = getChannelProcessor();
    
    final Map<String, String> headers = new HashMap<String, String>();
    
    // The StatusListener is a twitter4j API, which can be added to a Twitter
    // stream, and will execute methods every time a message comes in through
    // the stream.
    StatusListener listener = new StatusListener() {
      // The onStatus method is executed every time a new tweet comes in.
      public void onStatus(Status status) {
        // The EventBuilder is used to build an event using the headers and
        // the raw JSON of a tweet
    	if (logging)
    		logger.debug(status.getUser().getScreenName() + ": " + status.getText());

        headers.put("timestamp", String.valueOf(status.getCreatedAt().getTime()));
        Event event = EventBuilder.withBody(
            DataObjectFactory.getRawJSON(status).getBytes(), headers);
        
        channel.processEvent(event);
      }
      
      public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {}
      public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
    	  logger.info("This source has been track limited. There were " + numberOfLimitedStatuses + 
    			  " statuses matching the keywords which were not delivered. Try modifying the keywords to be" +
    			  " more selective.");
      }
      public void onScrubGeo(long userId, long upToStatusId) {}
      public void onException(Exception ex) {
    	  logger.info(ex.getMessage());
      }
      public void onStallWarning(StallWarning warning) {
    	  logger.info(warning.getMessage());
      }
    };
    
    if (logging) {
    	logger.debug("Setting up Twitter sample stream using consumer key {} and" +
    			" access token {}", new String[] { consumerKey, accessToken });
    }
    // Set up the stream's listener (defined above), and set any necessary
    // security information.
    twitterStream.addListener(listener);
    try {
    	twitterStream.getOAuthAccessToken();
    } catch (Exception e) {
	    twitterStream.setOAuthConsumer(consumerKey, consumerSecret);
		
	    AccessToken token = new AccessToken(accessToken, accessTokenSecret);
	    twitterStream.setOAuthAccessToken(token);
    }
    
    // If no keywords or screennames are provided, just sample the TwitterStream
    if (keywords.length == 0 && follow_ids.length == 0) {
      logger.info("Starting up Twitter sampling...");
      twitterStream.sample();
    } else {
      logger.info("Starting up Twitter filtering...");
      
      FilterQuery query = new FilterQuery();
      if (follow_ids.length > 0)
    	  query.follow(follow_ids);     
      if (keywords.length > 0)
    	  query.track(keywords);

      twitterStream.filter(query);
    }
    super.start();
  }
  
  /**
   * Stops the Source's event processing and shuts down the Twitter stream.
   */
  @Override
  public void stop() {
	logger.info("Shutting down Twitter sample stream...");
    twitterStream.shutdown();
    super.stop();
  }
}
