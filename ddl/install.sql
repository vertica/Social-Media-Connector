/*****************************
 * Vertica Analytic Database
 *
 * Social Media Connector Package
 *
 * Copyright (c) 2005 - 2013 Vertica, an HP company
 *****************************/
\set libfile '\''`echo ${VTweet_LIBFILE-lib/VTweetParser.so}`'\''

SELECT set_config_parameter('UseLongStrings', 1);
CREATE LIBRARY VTweetLib AS :libfile;

CREATE PARSER TweetParser AS LANGUAGE 'C++' NAME 'TweetParserFactory' LIBRARY VTweetLib NOT FENCED;
