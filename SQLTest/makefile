include ../../common.mk

THIRD_PARTY_LIB = $(shell pwd)/../build/lib

OBJECTS = $(wildcard ../build/*.o)

.PHONY: test

default: test

UDX_CXXFLAGS = -g -I ../src -I ../build/include -Wall -shared -fPIC
LDFLAGS=-shared -L $(THIRD_PARTY_LIB) -lgeos_c -Wl,-rpath,$(THIRD_PARTY_LIB)


test:
	$(MAKE) -C .. SDK=$(SOURCE)/VerticaSDK VSQL=$(TARGET)/bin/vsql install
	$(MAKE) vertest_jsonparser
	$(MAKE) -C .. SDK=$(SOURCE)/VerticaSDK VSQL=$(TARGET)/bin/vsql uninstall

flume_test:
	$(MAKE) -C .. flume
	$(MAKE) vertest_flume
