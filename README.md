BOP Community Bitcoin Server
============================

This software enables your application to send/receive Bitcoin payments.

You may run it stand-alone utilizing its own validation engine or as a slave behind the Satoshi client so it accepts exactly what the 'reference client' does. In either case it builds a fully indexed block chain of transactions stored in LevelDB.

The server process handles peer-to-peer communication with the network and serves clients connected to it through a message bus. Wallet(s) are implemented by the client library and transactions are also signed at the client side. Private keys, that is the control of Bitcoins always remain at the client side. 

The BOP Community Bitcoin Server is a fully functional demonstration of BOP technology, that we use to build commercial applications such as wallets and exchanges.

The BOP Enterprise Bitcoin Server supports the same API but has significantly improved performance e.g. in processing wallets using a large number of addresses. BOP offers professionally hosted Enterprise Bitcoin Servers, access to its latest Enterprise Server source code and support contracts. Please contact sales@bitsofproof.com for an offer.

Build the Community Server
--------------------------
Make sure you have Maven3, JDK 7 (with JCE Unlimited Strength Policy Jurisdiction) and Google protobuf compiler 2.4.1 installed.

   git clone https://github.com/bitsofproof/supernode

   cd supernode
   
   mvn package

Let us assume the directory you would want to run the server is /home/bitsofproof/run. 

  cp server/target/bitsofproof-server-version-shaded.jar /home/bitsofproof/run
  
  cp server/src/main/resources/context/*.xml /home/bitsofproof/run
  
  cp server/src/main/resources/log4j.properties /home/bitsofproof/run
  
  mkdir /home/bitsofproof/run/signed-libs
  
Download http://www.bouncycastle.org/download/bcprov-jdk15on-150.jar
  
  cp bcprov-jdk15on-150.jar /home/bitsofproof/run/signed-libs/bcprov-jdk15on.jar

Run
---
  cd /home/bitsofproof/run
  
  java -server -Xmx4g -jar target/server/target/bitsofproof-server-version-shaded.jar testnet3 memdb

The final two parameters of the above example command line identify configuration contexts  you copied under *-profile.xml. You have to choose one of the networks by specifying either testnet3 or production or slave, and a database layer, that could be (examples):
   
   * memdb - in memory database for tests
   * leveldb - LevelDB

To use the API of your local server you need to run a message broker process providing the infrastructure. Since the message bus offers authentication and a wide selection of transports, your installation will likely be unique and need to be reflected in BCSAPI-profile.xml. You find example configurations for the message Active MQ there. The complete command line for a production environment might be:

  java -server -Xmx4g -jar target/server/target/bitsofproof-server-version-shaded.jar production leveldb BCSAPI activemq

License
-------
Apache License, Version 2.0. See LICENSE file.

Donations
---------
Please honor Bits of Proof's contribution to the Bitcoin community, and donate to:

1EuamejAs2Lcz1ZPNrEhLsFTLnEY29BYKU

