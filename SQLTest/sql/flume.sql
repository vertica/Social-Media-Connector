create table tweets(created_at varchar(144), "user.name" varchar(144), "user.screen_name" varchar(144), text varchar(1024), retweet_count int, "user.location" varchar(144), "coordinates.coordinates.0" float, "coordinates.coordinates.1" float, lang varchar(5));

\! cd $PACKAGE_SQLTEST/../dist/apache-flume-1.3.1-bin && ./start-stop start TwitterAgent
\! sleep 60

select count(*) > 0 from tweets;

drop table tweets;
\! cd $PACKAGE_SQLTEST/../dist/apache-flume-1.3.1-bin && ./start-stop stop TwitterAgent
