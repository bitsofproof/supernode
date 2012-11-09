package com.bccapi.api;

/**
 * Exception thrown by the {@link BitcoinClientAPI} when a the BCCAPI server
 * reports an error on a function call.
 */
public class APIException extends Exception {

   private static final long serialVersionUID = -4274617809747288725L;

   public APIException(String message) {
      super(message);
   }

   public APIException(String message, Exception inner) {
      super(message, inner);
   }

   public APIException(Exception inner) {
      super(inner);
   }

}
