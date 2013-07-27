BOP Community Bitcoin Server
============================

This software enables your application to send/receive Bitcoin payments or to data mine the network's transaction history, with ease.

You may run it stand-alone utilizing its own validation engine or as a slave behind the Satoshi client so it accepts exactly what the 'reference client' does. In either case it builds a fully indexed block chain of transactions stored in LevelDB or in a relational database.

The server process handles peer-to-peer communication with the network and serves clients connected to it through a message bus. Wallet(s) are implemented by the client library and transactions are also signed at the client side, therefore it is safe to operate the Server in a remote environment as it does not store or receive private keys.

Bits of Proof offers professionally hosted server instances for commercial use, that share and build on this code base:

* BOP Enterprise Bitcoin Server -  for very high volume multiple and hierarchical wallet support. Don't despair with the 'reference client's geeky RPC calls design and poor performance. Use our intuitive, business friendly, high performance API and spend rather to develop your own business case.

* BOP Mobile Bitcoin Server -  back mobile wallets or any mobile application sending or receiving Bitcoin. We give you the highest performance mobile Bitcoin service with the smallest memory and traffic footprint on the device. See the example port of the popular Android Bitcoin wallet BitcoinSpinner at https://github.com/bitsofproof/bop-bitcoinspinner

* BOP Merchant Bitcoin Server - pay or issue payment requests, get notification on payments. Deal with your own Bitcoin, save commissions.

* BOP Audit Bitcoin Server - data mine the Bitcoin transaction history and get real-time feed of network events. Best for forensic research, no hassle to get high quality data.

* BOP ERP Bitcoin Server - initiate and monitor Bitcoin transactions with your ERP system. The next generation of service - stay tuned.

_Use our ready to go hosted BOP Server instance! Get an evaluation access, attractive pricing based on resources used or transactions processed from sales@bitsofproof.com_

Build the Community Server
--------------------------
Make sure you have Maven3, JDK 7 (with JCE Unlimited Strength Policy Jurisdiction) and Google protobuf compiler 2.4.1 installed.

   git clone https://github.com/bitsofproof/supernode

   cd supernode
   
   mvn package

Run
---

java -server -Xmx2g -jar target/server/target/bitsofproof-server-1.1.3.jar testnet3 memdb

The final two parameters of the above example command line identify configuration contexts stored under server/src/main/resources/context. You have to choose one of the networks by specifying either testnet3 or production or slave, and a database layer, that could be (examples):
   
   * memdb - in memory database for tests
   * leveldb - LevelDB
   * derby - For embedded relational database Derby
   * progresql - To connect to a ProgreSQL server

Review the context file of the SQL databases before attempting to use them, since connection parameters will likely not apply to your installation. To use the API of your local server you need to run a message broker process providing the infrastructure. Since the message bus offers authentication and a wide selection of transports, your installation will likely be unique and need to be reflected in server/src/main/resources/context/BCSAPI-profile.xml. You find example configurations for the message broker Apollo and Active MQ there. The complete command line for a production environment might be:

java -server -Xmx2g -jar target/server/target/bitsofproof-server-1.1.3.jar production leveldb BCSAPI apollo


License
-------
Apache License, Version 2.0. See LICENSE file.

Donations
---------
In case you do not require professional services of Bits of Proof, but would like to honor its contribution to the Bitcoin community, please donate to:

1EuamejAs2Lcz1ZPNrEhLsFTLnEY29BYKU

Donations will finance social events of Bits of Proof developer.
