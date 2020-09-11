package io.virgo.geoWeb.utils;

import java.nio.ByteBuffer;

public class Miscellaneous {

	public static byte[] concatBytesArrays(byte[]...arrays) {
	    // Determine the length of the result array
	    int totalLength = 0;
	    for (int i = 0; i < arrays.length; i++)
	    	totalLength += arrays[i].length;
	    

	    // create the result array
	    byte[] result = new byte[totalLength];

	    // copy the source arrays into the result array
	    int currentIndex = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
	        currentIndex += arrays[i].length;
	    }

	    return result;
	}
	
	public static byte[] intToBytes(int i) {
	    ByteBuffer bb = ByteBuffer.allocate(4); 
	    bb.putInt(i); 
	    return bb.array();
	}
	
}
