############################
# Vertica Analytic Database
#
# Makefile to build package directory
#
# Copyright 2011 Vertica Systems, an HP Company
############################

SDK?=/opt/vertica/sdk
VSQL?= /opt/vertica/bin/vsql

VERSION ?= 1.0.0
RELEASE ?= 0
VERTICA_SDK_INCLUDE = $(SDK)/include
SIMULATOR_PATH = $(SDK)/simulator

THIRD_PARTY = $(shell pwd)/third-party
SRCS = $(wildcard src/*.cpp)
OBJECTS = $(SRCS:src/%.cpp=build/%.o)
VPATH = src:build

# Add in your source files below
BUILD_FILES      = build/Vertica.o $(OBJECTS)

# Define the .so name here (and update the references in ddl/install.sql and ddl/uninstall.sql)
INSTALL_DIR ?= lib
PARSER_LIBNAME = $(INSTALL_DIR)/VTweetParser.so
FLUME_LIBNAME = $(INSTALL_DIR)/VerticaFlume.jar
FLUME_CONFIG = $(THIRD_PARTY)/conf/flume.conf

BUILD_DIR ?= dist

ORIGINAL_FLUME = $(BUILD_DIR)/apache-flume-1.3.1-bin
ORIGINAL_FLUME_TAR = $(THIRD_PARTY)/dist/apache-flume-1.3.1-bin.tar.gz

JAVA_SRCS := $(shell find src/ -name *.java)
NEW_LIBFILES := $(shell find third-party/lib -name *.jar -printf '%p:')

CXX=g++
CXXFLAGS=-c -I ../include  -I $(THIRD_PARTY)/install/include -Wall -Wno-unused-value -shared -fno-strict-aliasing -fPIC -I $(VERTICA_SDK_INCLUDE)
LDFLAGS=-shared  -L$(THIRD_PARTY)/install/lib -Wl,-rpath, -ljson

# add optimization if not a debug build
# (make DEBUG=true" will make a non-optimized build)
ifndef DEBUG
CXXFLAGS+=-O2
else
CXXFLAGS+=-g
endif

all: $(PARSER_LIBNAME) flume

# target to ensure third-party libraries are build
.PHONY: tparty install uninstall test clean realclean all

tparty:
	$(MAKE) -C $(THIRD_PARTY) all

flume: $(FLUME_LIBNAME)

# Main target that builds the package library
$(PARSER_LIBNAME): tparty $(BUILD_FILES) 
	mkdir -p $(INSTALL_DIR)
	$(CXX) $(BUILD_FILES) -o $@  $(LDFLAGS)

$(FLUME_LIBNAME): $(ORIGINAL_FLUME_TAR) tparty $(JAVA_SRCS) build/.done
	$(eval FLUME_LIBFILES := $(shell find $(ORIGINAL_FLUME)/lib -name *.jar -printf '%p:'))
	$(eval CLASSPATH := $(NEW_LIBFILES):$(FLUME_LIBFILES))
	mkdir -p build/classes
	javac -classpath $(CLASSPATH) $(JAVA_SRCS) -d build/classes
	mkdir -p $(INSTALL_DIR)
	cd build/classes && jar cf ../../$(FLUME_LIBNAME) .
	cp $(FLUME_LIBNAME) $(ORIGINAL_FLUME)/lib/
	cp third-party/lib/* $(ORIGINAL_FLUME)/lib/
	cp third-party/scripts/* $(ORIGINAL_FLUME)
	chmod +x $(ORIGINAL_FLUME)/start-stop
	chmod +x $(ORIGINAL_FLUME)/flume-ng-agent
	cp third-party/conf/* $(ORIGINAL_FLUME)/conf/

$(ORIGINAL_FLUME_TAR): build/.done
	mkdir -p $(BUILD_DIR)
	cd $(BUILD_DIR) && tar xvf $(shell readlink -f $(ORIGINAL_FLUME_TAR))

build/.done:
	mkdir -p build/classes
	touch build/.done

# rule to make build/XXX.o from src/XXX.cpp
build/%.o: src/%.cpp 
	@mkdir -p build
	$(CXX) $(CXXFLAGS) $< -o $@

# rule to compile symbols from the vertica SDK:
build/Vertica.o: $(VERTICA_SDK_INCLUDE)/Vertica.cpp $(VERTICA_SDK_INCLUDE)/BuildInfo.h
	@mkdir -p build
	$(CXX) $(CXXFLAGS) $< -o $@

# Targets to install and uninstall the library and functions
install: $(PARSER_LIBNAME) ddl/install.sql
	VTweet_LIBFILE="`readlink -f $(PARSER_LIBNAME)`" $(VSQL) -f ddl/install.sql
uninstall: ddl/uninstall.sql
	VTweet_LIBFILE="`readlink -f $(PARSER_LIBNAME)`" $(VSQL) -f ddl/uninstall.sql

# run examples
test:
	$(VSQL) -f examples/example.sql

clean:
	rm -f build/*.o
	rm -f build/.done
	rm -f $(INSTALL_DIR)/*
	rm -rf $(BUILD_DIR)/*

realclean:
	$(MAKE) -C $(THIRD_PARTY) clean
	rm -rf build
	rm -rf $(INSTALL_DIR)
	rm -rf $(BUILD_DIR)
	rm -rf dist
