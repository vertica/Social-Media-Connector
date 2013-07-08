1. Run "make" to compile TweetParser.
2. Run "make install" to compile and install TweetParser.
3. Create a table where the tweets will be loaded. See Documentation for information on how to name the columns in order to get the desired data
4. Given a json file "datafile.json", load it into your table "tweets" using

   	 copy tweets from local 'datafile.json' parser TweetParser();

5. Run "make uninstall" to uninstall TweetParser
