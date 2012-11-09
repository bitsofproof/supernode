package com.bccapi.api;

import java.io.Serializable;

/**
 * Settings for the network used. Can be either the test or production network.
 */
public class Network implements Serializable {
   private static final long serialVersionUID = 1L;

   public static final Network testNetwork = new Network(false);
   public static final Network productionNetwork = new Network(true);

   /**
    * The first byte of a base58 encoded bitcoin standard address.
    */
   private int _standardAddressHeader;

   /**
    * The first byte of a base58 encoded bitcoin multisig address.
    */
   private int _multisigAddressHeader;

   private Network(boolean isProdnet) {
      if (isProdnet) {
         _standardAddressHeader = 0x00;
         _multisigAddressHeader = 0x05;
      } else {
         _standardAddressHeader = 0x6F;
         _multisigAddressHeader = 0xC4;
      }
   }

   /**
    * Get the first byte of a base58 encoded bitcoin address as an integer.
    * 
    * @return The first byte of a base58 encoded bitcoin address as an integer.
    */
   public int getStandardAddressHeader() {
      return _standardAddressHeader;
   }

   /**
    * Get the first byte of a base58 encoded bitcoin multisig address as an
    * integer.
    * 
    * @return The first byte of a base58 encoded bitcoin multisig address as an
    *         integer.
    */
   public int getMultisigAddressHeader() {
      return _multisigAddressHeader;
   }

   @Override
   public int hashCode() {
      return _standardAddressHeader;
   };

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof Network)) {
         return false;
      }
      Network other = (Network) obj;
      return other._standardAddressHeader == _standardAddressHeader;
   }

}
