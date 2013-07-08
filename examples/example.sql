\set datafile '\''`echo $PACKAGE_SQLTEST`'/data/sample_tweets.json\''
create table tweets(created_at varchar(144), "user.name" varchar(144), "user.screen_name" varchar(144), text varchar(500), retweet_count int, "user.location" varchar(144), "coordinates.coordinates.0" numeric, "coordinates.coordinates.1" float, lang varchar(5));

copy tweets from local :datafile parser TweetParser();

select created_at from tweets order by created_at;

select retweet_count from tweets order by created_at;

select "user.location" from tweets where "user.location" is not NULL AND "user.location" <> '' order by created_at;

select "coordinates.coordinates.0", "coordinates.coordinates.1" from tweets where "coordinates.coordinates.0" is not NULL ORDER BY created_at;

drop table tweets;
