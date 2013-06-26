BOP Community Bitcoin Server
============================

This software enables your application or website to send or receive Bitcoin payments or to data mine the network's transaction history.

You may run it stand-alone utilizing its own validation engine or as a slave process behind the Satosi implementation so definitely accepts exactly what Satoshi does. In either case it builds a fully indexed block chain of transactions stored in LevelDB or in a relational database.

The server process handles peer-to-peer communication with the network and serves clients connected to it through a message bus. Wallet(s) are implemented by the client library and transactions are also signed at the client side. It is safe to operate a Server in a remote, untrusted environment as it does not store or receive private keys.

Bits of Proof offers professionally hosted server instances for commercial use, that share and build on this code base:

* BOP Enterprise Bitcoin Server -  for very high volume multiple and hierarchical wallet support.
* BOP Mobile Bitcoin Server -  to back mobile wallets or any mobile application sending or receiving Bitcoin.
* BOP Merchant Bitcoin Server - pay or issue payment requests, get notification on payments. Deal with your own Bitcoin, save commissions.
* BOP ERP Bitcoin Server - initiate and monitor Bitcoin transactions with your ERP system.
* BOP Audit Bitcoin Server - data mine the Bitcoin transaction history and get real-time feed of network events.

Please review our product sheets at http://bitsofproof.com and contact sales@bitsofproof.com for further info.

Build
-----
Make sure you have Maven3, JDK 1.6 or 7 and Google protobuf compiler 2.4.1 installed.

   git clone https://github.com/bitsofproof/supernode

   cd supernode
   
   mvn package

Run
---

java -server -Xmx2g -jar target/server/target/bitsofproof-server-1.0.jar testnet3 memdb

The final two parameters of the above example command line identify configuration contexts stored under server/src/main/resources/context. You have to choose one of the networks by specifying either testnet3 or production or slave, and a database layer, that could be (examples):
   
   * memdb - in memory database for tests
   * leveldb - LevelDB
   * derby - For embedded relational database Derby
   * progresql - To connect to a ProgreSQL server

Review the context file of the SQL databases before attempting to use them, since connection parameters will likely not apply to your installation. In addition you might add the BCSAPI context to let the server listen to a message broker. The complete command line for a production environment is likely:

java -server -Xmx2g -jar target/server/target/bitsofproof-server-1.0.jar production leveldb BCSAPI

To use the API of your local server you need to run a message broker process providing the infrastructure. Since the message bus offers authentication and a wide selection of transports, your installation will likely be unique and need to be reflected in server/src/main/resources/context/BCSAPI-profile.xml. You find example configurations for the message broker Apollo and Active MQ there.

License
-------
Apache License, Version 2.0. See LICENSE file.

Donations
---------
In case you do not require professional services of Bits of Proof, but would like to honor its contribution to the Bitcoin community, please donate to:

1EuamejAs2Lcz1ZPNrEhLsFTLnEY29BYKU

Donations will finance social events of Bits of Proof developer.