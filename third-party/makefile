all: scons jsoncpp 

.PHONY: all scons jsoncpp 

THIRD_PARTY = $(shell pwd)
INSTALL_DIR = $(THIRD_PARTY)/install


#
#  Location of OSS software distributions
#
  
DIST_DIR    = $(THIRD_PARTY)/dist
SCONS_TBZ   = $(DIST_DIR)/scons-local-2.3.0.tar.gz
JSONCPP_TBZ = $(DIST_DIR)/jsoncpp-src-0.6.0-rc2.tar.gz
JDK_DIR     = $(DIST_DIR)/jdk1.6.0_11
FLUME_TGZ   = $(DIST_DIR)/apache-flume-1.3.1-bin.tar.gz

SCONS_DIR   = $(THIRD_PARTY)/scons
SCONS_SRC   = $(SCONS_DIR)/scons-2.3.0
SCONS       = $(SCONS_DIR)/scons.py
SCONS_EXTRA = $(SCONS_DIR)/sconsign.py $(SCONS_DIR)/scons-LICENSE $(SCONS) $(SCONS_DIR)/scons-README $(SCONS_DIR)/scons-time.py

JSONCPP_DIR = $(THIRD_PARTY)/jsoncpp
JSONCPP_SRC = $(JSONCPP_DIR)/jsoncpp-src-0.6.0-rc2

THIRD_PARTY_DISTRIBUTIONS = $(JSONCPP_TBZ) $(SCONS_TBZ)

scons: $(SCONS_DIR)/.done
jsoncpp: $(JSONCPP_DIR)/.done

$(SCONS_DIR)/.done:
	@echo "Unpacking, configuring and installing scons...";
	mkdir -p $(SCONS_DIR)
	(cd $(SCONS_DIR); tar xzf $(SCONS_TBZ))
	touch $@

$(JSONCPP_DIR)/.done: $(SCONS_DIR)/.done
	@echo "Unpacking, configuring and installing jsoncpp...";
	(cd $(JSONCPP_DIR); tar xzf $(JSONCPP_TBZ))
	(cd $(JSONCPP_DIR); patch -p0 < $(JSONCPP_DIR)/json.patch)
	(cd $(JSONCPP_SRC); $(SCONS) platform=linux-gcc)
	mkdir -p $(INSTALL_DIR)/lib
	mkdir -p $(INSTALL_DIR)/include
	(cd $(JSONCPP_SRC); cp `find libs -name "*.a" | head -n1 ` $(INSTALL_DIR)/lib/libjson.a)
	(cd $(JSONCPP_SRC); cp -r include/* $(INSTALL_DIR)/include)
	touch $@

clean:
	find . -name ".done" -exec rm -rf {} \;
	rm -rf $(INSTALL_DIR)
	rm -rf $(SCONS_DIR)
	rm -rf $(JSONCPP_SRC)
	rm -f $(SCONS_EXTRA)

