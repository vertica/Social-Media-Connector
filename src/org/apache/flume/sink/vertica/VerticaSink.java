package org.apache.flume.sink.vertica;
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


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.flume.sink.AbstractSink;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.formatter.output.PathManager;
import org.apache.flume.instrumentation.SinkCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.flume.serialization.EventSerializer;
import org.apache.flume.serialization.EventSerializerFactory;

public class VerticaSink extends AbstractSink implements Configurable {

  private static final Logger logger = LoggerFactory
      .getLogger(VerticaSink.class);
  private static final long defaultRollInterval = 30;
  private static final int defaultBatchSize = 100;

  private int batchSize = defaultBatchSize;
  private int rollSize = 100;

  private File directory;
  private long rollInterval;
  private OutputStream outputStream;
  private ScheduledExecutorService rollService;

  private String serializerType;
  private Context serializerContext;
  private EventSerializer serializer;

  private SinkCounter sinkCounter;

  private PathManager pathController;
  private volatile boolean shouldRotate;
  
  // JDBC connection to Vertica
  private Connection conn;
  private String tableName;
  private String parserName;
  
  private int eventAttemptCounter;

  public VerticaSink() {
    pathController = new PathManager();
    shouldRotate = false;
    try {
		Class.forName("com.vertica.jdbc.Driver");
	} catch (ClassNotFoundException e) {
		// Could not find the driver class. Likely an issue
		// with finding the .jar file.
		System.err.println("Could not find the JDBC driver class.");
		e.printStackTrace();
		return; // Exit. Cannot do anything further.
	}
    	
  }

  @Override
  public void configure(Context context) {

    String directory = context.getString("directory");
    String rollInterval = context.getString("rollInterval");

    serializerType = context.getString("serializer", "TEXT");
    serializerContext =
        new Context(context.getSubProperties(EventSerializer.CTX_PREFIX));

    Preconditions.checkArgument(directory != null, "Directory may not be null");
    Preconditions.checkNotNull(serializerType, "Serializer type is undefined");

    if (rollInterval == null) {
      this.rollInterval = defaultRollInterval;
    } else {
      this.rollInterval = Long.parseLong(rollInterval);
    }

    batchSize = context.getInteger("batchSize", defaultBatchSize);
    rollSize = Math.min(rollSize, batchSize);
    this.directory = new File(directory);

    if (sinkCounter == null) {
      sinkCounter = new SinkCounter(getName());
    }
    
    // Table and parser to copy tweets into Vertica
    tableName = context.getString("tableName");
    parserName = context.getString("parserName", "TwitterParser");
    
    // Create property object to hold Vertica username & password
    Properties myProp = new Properties();
    myProp.put("user", context.getString("username"));
    myProp.put("password", context.getString("password", ""));
    
    // Open JDBC connection to Vertica
    String hostname = context.getString("VerticaHost");
    String port = context.getString("port", "5433");
    String dbName = context.getString("databaseName");
    try {
    	conn = DriverManager.getConnection(
    			"jdbc:vertica://" + hostname + ":" + port + "/" + dbName, myProp);
    }  catch (SQLException e) {
        // Could not connect to database.
        System.err.println("Could not connect to database.");
        logger.error(e.getMessage());
        return;
    }
  }

  @Override
  public void start() {
    logger.info("Starting {}...", this);
    sinkCounter.start();
    super.start();

    pathController.setBaseDirectory(directory);
    if(rollInterval > 0){

      rollService = Executors.newScheduledThreadPool(
          1,
          new ThreadFactoryBuilder().setNameFormat(
              "rollingFileSink-roller-" +
          Thread.currentThread().getId() + "-%d").build());

      /*
       * Every N seconds, mark that it's time to rotate. We purposefully do NOT
       * touch anything other than the indicator flag to avoid error handling
       * issues (e.g. IO exceptions occuring in two different threads.
       * Resist the urge to actually perform rotation in a separate thread!
       */
      rollService.scheduleAtFixedRate(new Runnable() {

        @Override
        public void run() {
          logger.debug("Marking time to rotate file {}",
              pathController.getCurrentFile());
          shouldRotate = true;
        }

      }, rollInterval, rollInterval, TimeUnit.SECONDS);
    } else{
      logger.info("RollInterval is not valid, file rolling will not happen.");
    }
    logger.info("RollingFileSink {} started.", getName());
  }

  @Override
  public Status process() throws EventDeliveryException {
    if (shouldRotate) {
      logger.debug("Time to rotate {}", pathController.getCurrentFile());

      if (outputStream != null) {
        logger.debug("Closing file {}", pathController.getCurrentFile());

        try {
          serializer.flush();
          serializer.beforeClose();
          outputStream.close();
          sinkCounter.incrementConnectionClosedCount();
          shouldRotate = false;
          
          // build sql command to copy tweets into Vertica
          String sqlCommand = "copy " + tableName + " from local '" + 
        		  pathController.getCurrentFile() + "' parser " + parserName + "();";
          Runnable r = new CopyThread(conn, sqlCommand);
          new Thread(r).start();

        } catch (IOException e) {
          sinkCounter.incrementConnectionFailedCount();
          throw new EventDeliveryException("Unable to rotate file "
              + pathController.getCurrentFile() + " while delivering event", e);
        } finally {
          serializer = null;
          outputStream = null;
        }
        pathController.rotate();
      }
    }

    if (outputStream == null) {
      File currentFile = pathController.getCurrentFile();
      logger.debug("Opening output stream for file {}", currentFile);
      try {
        outputStream = new BufferedOutputStream(
            new FileOutputStream(currentFile));
        serializer = EventSerializerFactory.getInstance(
            serializerType, serializerContext, outputStream);
        serializer.afterCreate();
        sinkCounter.incrementConnectionCreatedCount();
      } catch (IOException e) {
        sinkCounter.incrementConnectionFailedCount();
        throw new EventDeliveryException("Failed to open file "
            + pathController.getCurrentFile() + " while delivering event", e);
      }
    }

    Channel channel = getChannel();
    Transaction transaction = channel.getTransaction();
    Event event = null;
    Status result = Status.READY;

    try {
      transaction.begin();
      for (int i = 0; i < rollSize; i++) {
        event = channel.take();
        if (event != null) {
          sinkCounter.incrementEventDrainAttemptCount();
          eventAttemptCounter++;
          serializer.write(event);

          if (eventAttemptCounter == batchSize) {
        	  shouldRotate = true;
        	  eventAttemptCounter = 0;
          }

          /*
           * FIXME: Feature: Control flush interval based on time or number of
           * events. For now, we're super-conservative and flush on each write.
           */
        } else {
          // No events found, request back-off semantics from runner
          result = Status.BACKOFF;
          break;
        }
      }
      serializer.flush();
      outputStream.flush();
      transaction.commit();
      sinkCounter.addToEventDrainSuccessCount(eventAttemptCounter);
    } catch (Exception ex) {
      transaction.rollback();
      throw new EventDeliveryException("Failed to process transaction", ex);
    } finally {
      transaction.close();
    }

    return result;
  }

  @Override
  public void stop() {
    logger.info("RollingFile sink {} stopping...", getName());
    sinkCounter.stop();
    super.stop();

    if (outputStream != null) {
      logger.debug("Closing file {}", pathController.getCurrentFile());

      try {
        serializer.flush();
        serializer.beforeClose();
        outputStream.close();

        // build sql command to copy tweets into Vertica
        String sqlCommand = "copy " + tableName + " from local '" +
	    pathController.getCurrentFile() + "' parser " + parserName + "();";
	try {
	    Statement stmt = conn.createStatement();
	    stmt.execute(sqlCommand);
	} catch (SQLException e) {
	    // Could not connect to database.
	    System.err.println("Could not connect to database.");
	    logger.error(e.getMessage());
	}

        sinkCounter.incrementConnectionClosedCount();
      } catch (IOException e) {
        sinkCounter.incrementConnectionFailedCount();
        logger.error("Unable to close output stream. Exception follows.", e);
      } finally {
        outputStream = null;
        serializer = null;
      }
    }
    if(rollInterval > 0){
      rollService.shutdown();

      while (!rollService.isTerminated()) {
        try {
          rollService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          logger
          .debug(
              "Interrupted while waiting for roll service to stop. " +
              "Please report this.", e);
        }
      }
    }
    logger.info("RollingFile sink {} stopped. Event metrics: {}",
        getName(), sinkCounter);
  }

  public File getDirectory() {
    return directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  public long getRollInterval() {
    return rollInterval;
  }

  public void setRollInterval(long rollInterval) {
    this.rollInterval = rollInterval;
  }
  
  // Copy tweets into Vertica
  class CopyThread implements Runnable {
	  private String sqlCommand;
	  private Connection conn;

	  public CopyThread(Connection conn, String sqlCommand) {
		  this.conn = conn;
		  this.sqlCommand = sqlCommand;	
	  }

	  public void run() {
		  try {
			  Statement stmt = conn.createStatement();
			  stmt.execute(sqlCommand);
		  } catch (SQLException e) {
			  // Could not connect to database.
			  System.err.println("Could not connect to database.");
			  logger.error(e.getMessage());
		  }
	  }
  }

}
