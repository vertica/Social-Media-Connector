all: scons jsoncpp jdk

.PHONY: all scons jsoncpp jdk

THIRD_PARTY = $(shell pwd)
INSTALL_DIR = $(THIRD_PARTY)/install

SCONS_DIR = $(THIRD_PARTY)/scons
SCONS_TBZ = $(SCONS_DIR)/scons-local-2.1.0.tar.gz
SCONS_SRC = $(SCONS_DIR)/scons-local-2.1.0
SCONS = $(SCONS_DIR)/scons.py
SCONS_EXTRA = $(SCONS_DIR)/sconsign.py $(SCONS_DIR)/scons-LICENSE $(SCONS) $(SCONS_DIR)/scons-README $(SCONS_DIR)/scons-time.py

JSONCPP_DIR = $(THIRD_PARTY)/jsoncpp
JSONCPP_TBZ = $(JSONCPP_DIR)/jsoncpp-src-0.6.0-rc2.tar.gz
JSONCPP_SRC = $(JSONCPP_DIR)/jsoncpp-src-0.6.0-rc2

JDK_DIR = $(THIRD_PARTY)/jdk1.6.0_11
JDK_TBZ = $(THIRD_PARTY)/jdk1.6.0_11.tar.gz

scons: $(SCONS_DIR)/.done
jsoncpp: $(JSONCPP_DIR)/.done
jdk: $(JDK_DIR)/.done

$(SCONS_DIR)/.done:
	@echo "Unpacking, configuring and installing scons...";
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

$(JDK_DIR)/.done: 
	@echo "Unpacking and installing jdk 1.6...";
	(tar xzf $(JDK_TBZ))
	touch $@

clean:
	find . -name ".done" -exec rm -rf {} \;
	rm -rf $(INSTALL_DIR)
	rm -rf $(SCONS_SRC)
	rm -rf $(JSONCPP_SRC)
	rm -rf $(JDK_DIR)
	rm -f $(SCONS_EXTRA)