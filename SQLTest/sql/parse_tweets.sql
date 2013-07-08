\set datafile '\''`echo $PACKAGE_SQLTEST`'/data/sample_tweets.json\''
create table tweets(created_at varchar(144), "user.name" varchar(144), "user.screen_name" varchar(144), text varchar(500), retweet_count int, "user.location" varchar(144), "coordinates.coordinates.0" numeric, "coordinates.coordinates.1" float, lang varchar(5));

copy tweets from local :datafile parser TweetParser();

select created_at from tweets order by created_at;

select retweet_count from tweets order by created_at;

select "user.location" from tweets where "user.location" is not NULL AND "user.location" <> '' order by created_at;

select "coordinates.coordinates.0", "coordinates.coordinates.1" from tweets where "coordinates.coordinates.0" is not NULL ORDER BY created_at;

drop table tweets;

-- VER-27183
create table tweets(created_at varchar(144), "user.name" varchar(144), "user.screen_name" varchar(144), text varchar(500), retweet_count varchar, "user.location" varchar(144), "coordinates.coordinates.0" numeric, "coordinates.coordinates.1" float, lang varchar(5)); 

copy tweets from :datafile parser TweetParser();
select retweet_count from tweets order by created_at;

-- VER-27139
copy tweets from :datafile parser TweetParser() returnrejected;

drop table tweets;

-- VER-27263
create table tweets(id varchar, created_at int, "user.name" int, "user.screen_name" int, text int, retweet_count varchar, "user.location" int, "coordinates.coordinates.0" varchar, "coordinates.coordinates.1" varchar, lang int); 

copy tweets from :datafile parser TweetParser();

select * from tweets;
drop table tweets;

-- VER-27302
create table tweets(created_at timestamp without timezone, "user.name" varchar(144), "user.screen_name" varchar(144), text varchar(500), retweet_count int, "user.location" varchar(144), "coordinates.coordinates.0" numeric, "coordinates.coordinates.1" float, lang varchar(5));

copy tweets from :datafile parser TweetParser();

select * from tweets order by created_at;
drop table tweets;

-- VER-27318
create table tweets(id int, id_str varchar);
copy tweets from :datafile parser TweetParser();
select * from tweets where not id=id_str;
drop table tweets;

-- VER-27358
create table tweets(
id int,
created_at timestamptz,
"user.name" varchar(144),
"user.screen_name" varchar(144),
text varchar(500),
"retweeted_status.retweet_count" int,
"retweeted_status.id" int,
"retweeted_status.favorite_count" int,
"user.location" varchar(144),
"coordinates.coordinates.0" float,
"coordinates.coordinates.1" float,
"entities.hashtags.hashtags.0" VARCHAR(144),
"entities.hashtags.hashtags.1" VARCHAR(144),
"entities.0.hashtags.0" VARCHAR(144),
lang varchar(5)
); 
copy tweets from :datafile parser TweetParser();
select id from tweets where not "entities.hashtags.hashtags.0" is null;
select id from tweets where not "entities.0.hashtags.0" is null;
drop table tweets;